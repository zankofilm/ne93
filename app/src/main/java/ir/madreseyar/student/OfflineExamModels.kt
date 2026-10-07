package ir.madreseyar.student

import org.json.JSONObject
import java.util.UUID

data class OfflineExamQuestion(
    val id: String,
    val number: Int,
    val text: String,
    val score: Double,
    val type: String,
    val choices: List<String>
) {
    val isChoice: Boolean get() = choices.isNotEmpty() || type in setOf("mcq", "true_false", "single_choice")
    companion object {
        fun fromJson(o: JSONObject) = OfflineExamQuestion(
            id=o.string("id"), number=o.int("number"), text=o.string("text"), score=o.double("score"),
            type=o.string("type"), choices=o.arr("choices").let { arr -> List(arr.length()) { i -> arr.optString(i) } }
        )
    }
}

data class OfflineExamPackage(
    val examId: String,
    val title: String,
    val subject: String,
    val type: String,
    val durationMinutes: Int,
    val packageVersion: String,
    val attemptId: String,
    val packageSignature: String,
    val expiresAt: Long,
    val downloadedAt: Long,
    val sheetCode: String,
    val questions: List<OfflineExamQuestion>,
    val raw: String
) {
    companion object {
        fun fromJson(root: JSONObject): OfflineExamPackage {
            val exam=root.obj("exam")
            return OfflineExamPackage(
                examId=exam.string("id"), title=exam.string("title","آزمون"), subject=exam.string("subject"),
                type=exam.string("type","mcq"), durationMinutes=exam.int("duration",1).coerceAtLeast(1),
                packageVersion=root.string("packageVersion",exam.string("packageVersion")), attemptId=root.string("attemptId"),
                packageSignature=root.string("packageSignature"), expiresAt=root.optLong("expiresAt",0L),
                downloadedAt=root.optLong("downloadedAt",System.currentTimeMillis()), sheetCode=exam.string("sheetCode"),
                questions=exam.arr("questions").mapObjects(OfflineExamQuestion::fromJson), raw=root.toString()
            )
        }
    }
}

data class OfflineExamDraft(
    val examId: String,
    val startedAt: Long,
    val finalizedAt: Long = 0L,
    val status: String = "in_progress",
    val submissionKey: String = UUID.randomUUID().toString(),
    val answers: Map<String,String> = emptyMap(),
    val retryCount: Int = 0,
    val lastError: String = "",
    val receiptId: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("examId",examId).put("startedAt",startedAt).put("finalizedAt",finalizedAt).put("status",status)
        .put("submissionKey",submissionKey).put("answers",JSONObject(answers)).put("retryCount",retryCount)
        .put("lastError",lastError).put("receiptId",receiptId)

    companion object {
        fun fromJson(o: JSONObject): OfflineExamDraft {
            val ans=o.obj("answers"); val map=linkedMapOf<String,String>()
            for(k in ans.keys()) map[k]=ans.opt(k)?.toString().orEmpty()
            return OfflineExamDraft(
                examId=o.string("examId"), startedAt=o.optLong("startedAt",0L), finalizedAt=o.optLong("finalizedAt",0L),
                status=o.string("status","in_progress"), submissionKey=o.string("submissionKey",UUID.randomUUID().toString()),
                answers=map, retryCount=o.int("retryCount"), lastError=o.string("lastError"), receiptId=o.string("receiptId")
            )
        }
    }
}

data class ExamLocalState(
    val downloaded: Boolean=false,
    val draftStatus: String="",
    val pendingUpload: Boolean=false,
    val receiptId: String=""
)

data class OfflineExamSession(val pack: OfflineExamPackage, val draft: OfflineExamDraft)
