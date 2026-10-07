package ir.madreseyar.student

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class QueuedHomeworkSubmission(
    val homeworkId: String,
    val text: String,
    val submissionKey: String = UUID.randomUUID().toString(),
    val queuedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("homeworkId", homeworkId)
        .put("text", text)
        .put("submissionKey", submissionKey)
        .put("queuedAt", queuedAt)
        .put("retryCount", retryCount)
        .put("lastError", lastError)

    companion object {
        fun fromJson(o: JSONObject) = QueuedHomeworkSubmission(
            homeworkId = o.string("homeworkId"),
            text = o.string("text"),
            submissionKey = o.string("submissionKey").ifBlank { UUID.randomUUID().toString() },
            queuedAt = o.optLong("queuedAt", System.currentTimeMillis()),
            retryCount = o.int("retryCount"),
            lastError = o.string("lastError")
        )
    }
}

class HomeworkOutboxStore(context: Context) {
    private val store = EncryptedFileStore(context, "homework_outbox")
    private fun key(homeworkId: String) = "hw_out_$homeworkId"

    fun queue(homeworkId: String, text: String): QueuedHomeworkSubmission {
        val existing = load(homeworkId)
        val item = if (existing == null) {
            QueuedHomeworkSubmission(homeworkId = homeworkId, text = text)
        } else {
            existing.copy(text = text, lastError = "")
        }
        save(item)
        return item
    }

    fun save(item: QueuedHomeworkSubmission) = store.put(key(item.homeworkId), item.toJson().toString())

    fun load(homeworkId: String): QueuedHomeworkSubmission? =
        store.get(key(homeworkId)).takeIf { it.isNotBlank() }?.let {
            runCatching { QueuedHomeworkSubmission.fromJson(JSONObject(it)) }.getOrNull()
        }

    fun remove(homeworkId: String) = store.delete(key(homeworkId))

    fun all(): List<QueuedHomeworkSubmission> = store.keys("hw_out_").mapNotNull { storageKey ->
        store.get(storageKey).takeIf { it.isNotBlank() }?.let {
            runCatching { QueuedHomeworkSubmission.fromJson(JSONObject(it)) }.getOrNull()
        }
    }.sortedBy { it.queuedAt }

    fun pendingIds(): Set<String> = all().map { it.homeworkId }.toSet()
    fun clear() = store.clear()
}
