package ir.madreseyar.student

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.net.URI

class ResourceFileStore(private val context:Context) {
    private val dir=File(context.filesDir,"offline_resources").apply{mkdirs()}
    private fun safeExt(url:String):String {
        val p=runCatching{URI(url).path.orEmpty()}.getOrDefault("")
        val ext=p.substringAfterLast('.',"").lowercase()
        return if(ext in setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","jpg","jpeg","png","webp","zip","txt","mp4")) ".$ext" else ".bin"
    }
    private fun file(resource:ResourceItem)=File(dir,resource.id.replace(Regex("[^A-Za-z0-9_.-]"),"_")+safeExt(resource.url))
    fun shouldPrefetch(resource:ResourceItem):Boolean = resource.url.startsWith("https://") && safeExt(resource.url)!=".bin"
    fun has(resource:ResourceItem)=file(resource).exists() && file(resource).length()>0
    fun save(resource:ResourceItem,bytes:ByteArray){ val f=file(resource); val tmp=File(f.parentFile,f.name+".tmp"); tmp.writeBytes(bytes); if(!tmp.renameTo(f)){f.writeBytes(bytes);tmp.delete()} }
    fun uri(resource:ResourceItem):String? = if(has(resource)) FileProvider.getUriForFile(context,"${BuildConfig.APPLICATION_ID}.files",file(resource)).toString() else null
    fun fileCount():Int = dir.listFiles().orEmpty().count { it.isFile }
    fun totalBytes():Long = dir.listFiles().orEmpty().sumOf { if(it.isFile) it.length() else 0L }
    fun clear(){dir.listFiles().orEmpty().forEach{it.delete()}}
}
