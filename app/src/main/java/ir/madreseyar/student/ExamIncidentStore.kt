package ir.madreseyar.student

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class QueuedExamIncident(
    val id: String = UUID.randomUUID().toString(),
    val examId: String,
    val type: String,
    val description: String,
    val clientAt: Long = System.currentTimeMillis(),
    val sessionKey: String,
    val visibility: String = "background",
    val retryCount: Int = 0
) {
    fun toJson() = JSONObject()
        .put("id", id).put("examId", examId).put("type", type).put("description", description)
        .put("clientAt", clientAt).put("sessionKey", sessionKey).put("visibility", visibility).put("retryCount", retryCount)

    companion object {
        fun fromJson(o: JSONObject) = QueuedExamIncident(
            id=o.string("id", UUID.randomUUID().toString()), examId=o.string("examId"), type=o.string("type","other"),
            description=o.string("description"), clientAt=o.optLong("clientAt",System.currentTimeMillis()),
            sessionKey=o.string("sessionKey"), visibility=o.string("visibility","background"), retryCount=o.int("retryCount")
        )
    }
}

class ExamIncidentStore(context: Context) {
    private val store = EncryptedFileStore(context, "exam_incidents")
    private fun key(id:String)="incident_$id"
    fun add(item:QueuedExamIncident){ store.put(key(item.id), item.toJson().toString()) }
    fun remove(id:String){ store.delete(key(id)) }
    fun all():List<QueuedExamIncident> = store.keys("incident_").mapNotNull { k ->
        store.get(k).takeIf { it.isNotBlank() }?.let { runCatching { QueuedExamIncident.fromJson(JSONObject(it)) }.getOrNull() }
    }.sortedBy { it.clientAt }
    fun clear()=store.clear()
}
