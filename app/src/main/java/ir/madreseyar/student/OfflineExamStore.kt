package ir.madreseyar.student

import android.content.Context
import org.json.JSONObject

class OfflineExamStore(context: Context) {
    private val store=EncryptedFileStore(context,"exams")
    private fun p(id:String)="exam_pkg_$id"
    private fun d(id:String)="exam_draft_$id"

    fun savePackage(pack: OfflineExamPackage) = store.put(p(pack.examId), pack.raw)
    fun loadPackage(examId:String): OfflineExamPackage? = store.get(p(examId)).takeIf{it.isNotBlank()}?.let { runCatching { OfflineExamPackage.fromJson(JSONObject(it)) }.getOrNull() }
    fun hasPackage(examId:String, packageVersion:String=""):Boolean {
        val pack=loadPackage(examId) ?: return false
        return packageVersion.isBlank() || pack.packageVersion==packageVersion
    }
    fun deletePackage(examId:String)=store.delete(p(examId))

    fun saveDraft(draft: OfflineExamDraft)=store.put(d(draft.examId),draft.toJson().toString())
    fun loadDraft(examId:String): OfflineExamDraft?=store.get(d(examId)).takeIf{it.isNotBlank()}?.let { runCatching{OfflineExamDraft.fromJson(JSONObject(it))}.getOrNull() }
    fun deleteDraft(examId:String)=store.delete(d(examId))

    fun localState(examId:String, packageVersion:String=""):ExamLocalState {
        val draft=loadDraft(examId)
        return ExamLocalState(
            downloaded=hasPackage(examId,packageVersion),
            draftStatus=draft?.status.orEmpty(),
            pendingUpload=draft?.status=="queued",
            receiptId=draft?.receiptId.orEmpty()
        )
    }

    fun queuedDrafts():List<OfflineExamDraft> = store.keys("exam_draft_").mapNotNull { key ->
        store.get(key).takeIf{it.isNotBlank()}?.let { runCatching{OfflineExamDraft.fromJson(JSONObject(it))}.getOrNull() }
    }.filter{it.status=="queued"}

    fun downloadedCount():Int = store.keys("exam_pkg_").size
    fun storageBytes():Long = store.totalBytes()
    fun clearPackagesSafe():Int {
        var removed=0
        for(key in store.keys("exam_pkg_")){
            val examId=key.removePrefix("exam_pkg_")
            val draft=loadDraft(examId)
            if(draft==null || draft.status=="submitted"){ store.delete(key); removed++ }
        }
        return removed
    }
    fun clear()=store.clear()
}
