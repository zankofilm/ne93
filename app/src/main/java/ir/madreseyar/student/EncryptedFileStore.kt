package ir.madreseyar.student

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedFileStore(context: Context, namespace: String = "default") {
    private val dir = File(File(context.filesDir, "offline_secure"), namespace.replace(Regex("[^A-Za-z0-9_.-]"), "_")).apply { mkdirs() }
    private val alias = "madreseyar_student_offline_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun fileFor(id: String): File = File(dir, id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".bin")

    fun put(id: String, text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + cipher.iv.size + encrypted.size)
        payload[0] = cipher.iv.size.toByte()
        System.arraycopy(cipher.iv, 0, payload, 1, cipher.iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + cipher.iv.size, encrypted.size)
        val target = fileFor(id)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(payload)
        if (!tmp.renameTo(target)) {
            target.writeBytes(payload)
            tmp.delete()
        }
    }

    fun get(id: String): String = runCatching {
        val bytes = fileFor(id).readBytes()
        if (bytes.isEmpty()) return ""
        val ivSize = bytes[0].toInt() and 0xff
        if (ivSize <= 0 || bytes.size <= 1 + ivSize) return ""
        val iv = bytes.copyOfRange(1, 1 + ivSize)
        val data = bytes.copyOfRange(1 + ivSize, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrDefault("")

    fun delete(id: String) { fileFor(id).delete() }
    fun exists(id: String): Boolean = fileFor(id).exists()
    fun keys(prefix: String = ""): List<String> = dir.listFiles().orEmpty().mapNotNull { f ->
        if (!f.name.endsWith(".bin")) null else f.name.removeSuffix(".bin").takeIf { it.startsWith(prefix) }
    }
    fun totalBytes(): Long = dir.listFiles().orEmpty().sumOf { if (it.isFile) it.length() else 0L }
    fun fileCount(): Int = dir.listFiles().orEmpty().count { it.isFile }
    fun clear() { dir.listFiles().orEmpty().forEach { it.delete() } }
}
