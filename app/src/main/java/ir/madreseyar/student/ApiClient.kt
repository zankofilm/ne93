package ir.madreseyar.student

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String, val status: Int = 0, val payload: JSONObject = JSONObject()) : Exception(message)

class ApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    suspend fun login(nationalId: String, password: String, birthDate: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("nationalId", nationalId)
        if (password.isNotBlank()) body.put("password", password) else body.put("birthDate", birthDate)
        val result = request("/api/student/login", "POST", body, "")
        LoginResult(
            token = result.string("token"),
            student = StudentProfile.fromJson(result.obj("student")),
            mustChangePassword = result.bool("mustChangePassword")
        )
    }

    suspend fun changePassword(token: String, newPassword: String, currentPassword: String = "") = withContext(Dispatchers.IO) {
        val body = JSONObject().put("newPassword", newPassword)
        if (currentPassword.isNotBlank()) body.put("currentPassword", currentPassword)
        request("/api/student/change-password", "POST", body, token)
    }

    suspend fun dashboard(token: String): DashboardData = withContext(Dispatchers.IO) {
        DashboardData.fromJson(request("/api/student/dashboard", "GET", null, token))
    }

    suspend fun appVersion(): AppUpdateInfo = withContext(Dispatchers.IO) {
        AppUpdateInfo.fromJson(request("/api/student/app-version?versionCode=${BuildConfig.VERSION_CODE}", "GET", null, ""))
    }

    suspend fun reportExamIncident(token:String,item:QueuedExamIncident):JSONObject = withContext(Dispatchers.IO) {
        request("/api/student/exam-incidents", "POST", JSONObject()
            .put("examId",item.examId).put("type",item.type).put("description",item.description)
            .put("clientEventId",item.id).put("clientAt",item.clientAt).put("sessionKey",item.sessionKey)
            .put("networkOnline",true).put("visibility",item.visibility).put("appVersion",BuildConfig.VERSION_NAME), token)
    }

    suspend fun joinOnlineClass(token: String, scheduleEntryId: String, date: String): String = withContext(Dispatchers.IO) {
        request("/api/student/online-class/join", "POST", JSONObject().put("scheduleEntryId", scheduleEntryId).put("date", date), token).string("meetingUrl")
    }

    suspend fun submitHomework(token: String, homeworkId: String, text: String, submissionKey: String = ""): JSONObject = withContext(Dispatchers.IO) {
        request(
            "/api/student/homework/submit",
            "POST",
            JSONObject().put("homeworkId", homeworkId).put("text", text),
            token,
            if(submissionKey.isBlank()) emptyMap() else mapOf("Idempotency-Key" to submissionKey)
        )
    }


    suspend fun downloadExam(token: String, examId: String): JSONObject = withContext(Dispatchers.IO) {
        request("/api/student/exams/${java.net.URLEncoder.encode(examId, "UTF-8")}/download", "GET", null, token)
    }

    suspend fun examAnalysis(token:String, examId:String):ExamAnalysis = withContext(Dispatchers.IO) {
        ExamAnalysis.fromJson(request("/api/student/exams/${java.net.URLEncoder.encode(examId, "UTF-8")}/analysis", "GET", null, token))
    }

    suspend fun submitExam(token: String, pack: OfflineExamPackage, draft: OfflineExamDraft): JSONObject = withContext(Dispatchers.IO) {
        val answers=JSONObject()
        for(q in pack.questions){
            val raw=draft.answers[q.id] ?: continue
            if(q.isChoice) answers.put(q.id, raw.toIntOrNull() ?: raw) else answers.put(q.id, raw)
        }
        val now=System.currentTimeMillis()
        val duration=((draft.finalizedAt.takeIf{it>0} ?: now)-draft.startedAt).coerceAtLeast(0L)/1000L
        val body=JSONObject()
            .put("answers",answers)
            .put("attemptId",pack.attemptId)
            .put("packageSignature",pack.packageSignature)
            .put("packageVersion",pack.packageVersion)
            .put("submissionKey",draft.submissionKey)
            .put("startedAt",draft.startedAt)
            .put("finalizedAt",draft.finalizedAt.takeIf{it>0} ?: now)
            .put("durationSeconds",duration)
            .put("deliveryDiagnostics",JSONObject()
                .put("clientQueuedAt",draft.finalizedAt)
                .put("clientFinalizedAt",draft.finalizedAt)
                .put("clientRetryCount",draft.retryCount)
                .put("clientLastTriedAt",now))
        val suffix=when(pack.type){
            "mixed" -> "submit-mixed"
            "descriptive" -> "submit-descriptive"
            else -> "submit"
        }
        request("/api/student/exams/${java.net.URLEncoder.encode(pack.examId, "UTF-8")}/$suffix", "POST", body, token)
    }

    suspend fun downloadResource(urlString:String,maxBytes:Int=25*1024*1024):Pair<ByteArray,String> = withContext(Dispatchers.IO) {
        val url=URL(urlString)
        require(url.protocol=="https") { "فقط دانلود HTTPS مجاز است." }
        val c=(url.openConnection() as HttpURLConnection).apply {
            requestMethod="GET"; connectTimeout=12_000; readTimeout=30_000; instanceFollowRedirects=true
            setRequestProperty("User-Agent","MadreseyarStudentAndroid/1.3.9")
        }
        try{
            val status=c.responseCode
            if(status !in 200..299) throw ApiException("دانلود منبع انجام نشد ($status)",status)
            val declared=c.contentLengthLong
            if(declared>maxBytes) throw ApiException("حجم فایل بیشتر از حد مجاز دانلود آفلاین است.")
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf=ByteArray(8192); var total=0
                while(true){ val n=input.read(buf); if(n<=0)break; total+=n; if(total>maxBytes)throw ApiException("حجم فایل بیشتر از حد مجاز دانلود آفلاین است."); out.write(buf,0,n) }
            }
            out.toByteArray() to (c.contentType ?: "application/octet-stream")
        } finally { c.disconnect() }
    }


    suspend fun profilePhoto(token: String): String = withContext(Dispatchers.IO) {
        request("/api/student/profile/photo", "GET", null, token).string("photoDataUrl")
    }

    suspend fun requestExamDeliveryRecovery(token:String, pack:OfflineExamPackage, draft:OfflineExamDraft, error:ApiException):JSONObject = withContext(Dispatchers.IO) {
        request("/api/student/exam-delivery-recovery/request", "POST", JSONObject()
            .put("examId", pack.examId)
            .put("submissionKey", draft.submissionKey)
            .put("packageVersion", pack.packageVersion)
            .put("finalizedAt", draft.finalizedAt)
            .put("queuedAt", draft.finalizedAt)
            .put("retryCount", draft.retryCount)
            .put("lastError", error.message ?: "خطای تحویل")
            .put("lastErrorStatus", error.status), token)
    }

    suspend fun updateProfile(token: String, profile: StudentProfile): StudentProfile = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("studentMobile", profile.studentMobile)
            .put("email", profile.email)
            .put("guardianMobile", profile.guardianMobile)
            .put("homePhone", profile.homePhone)
            .put("emergencyGuardianName", profile.emergencyGuardianName)
            .put("emergencyGuardianRelation", profile.emergencyGuardianRelation)
            .put("emergencyGuardianMobile", profile.emergencyGuardianMobile)
            .put("address", profile.address)
            .put("postalCode", profile.postalCode)
        StudentProfile.fromJson(request("/api/student/profile", "POST", body, token).obj("student"))
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        runCatching { request("/api/student/logout", "POST", JSONObject(), token) }
    }

    private fun request(path: String, method: String, body: JSONObject?, token: String, extraHeaders: Map<String,String> = emptyMap()): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        require(url.protocol == "https") { "اتصال فقط از طریق HTTPS مجاز است." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Client-App", "madreseyar-student-android/1.3.7-production")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            extraHeaders.forEach { (name,value) -> setRequestProperty(name,value) }
            if (body != null && method != "GET") doOutput = true
        }
        try {
            if (body != null && method != "GET") connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = if (stream != null) BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() } else "{}"
            val json = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw ApiException(json.string("error", "خطای سرور ($status)"), status, json)
            return json
        } finally {
            connection.disconnect()
        }
    }
}
