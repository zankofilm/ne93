package ir.madreseyar.student

import android.content.Context

data class CacheStats(val resourceFiles:Int=0,val resourceBytes:Long=0,val examPackages:Int=0,val examBytes:Long=0)

data class SyncResult(
    val dashboard: DashboardData,
    val offline: Boolean,
    val lastSyncAt: Long,
    val downloadedExams: Int,
    val cachedResources: Int,
    val pendingExamUploads: Int,
    val pendingHomeworkUploads: Int
)

class StudentRepository(private val context: Context) {
    private val api = ApiClient()
    private val secure = SecureSessionStore(context)
    private val cache = DashboardCache(context)
    private val examStore = OfflineExamStore(context)
    private val resourceStore = ResourceFileStore(context)
    private val homeworkDrafts = HomeworkDraftStore(context)
    private val homeworkOutbox = HomeworkOutboxStore(context)
    private val profilePrefs = context.getSharedPreferences("student_profile_media", Context.MODE_PRIVATE)
    private val incidentStore = ExamIncidentStore(context)
    private val notifications = AppNotificationManager(context)

    fun token(): String = secure.loadToken()
    fun hasSession(): Boolean = token().isNotBlank()
    fun isOnline():Boolean=NetworkUtil.isOnline(context)
    fun lastSyncAt():Long=cache.savedAt()
    fun pendingExamCount():Int=examStore.queuedDrafts().size
    fun pendingHomeworkCount():Int=homeworkOutbox.all().size
    fun pendingIncidentCount():Int=incidentStore.all().size
    fun pendingHomeworkIds():Set<String> = homeworkOutbox.pendingIds()
    fun cachedProfilePhoto():String = profilePrefs.getString("photoDataUrl", "").orEmpty()
    suspend fun syncProfilePhoto():String {
        if(!isOnline() || token().isBlank()) return cachedProfilePhoto()
        return runCatching { api.profilePhoto(token()) }.getOrElse { return cachedProfilePhoto() }.also { profilePrefs.edit().putString("photoDataUrl", it).apply() }
    }
    fun cacheStats():CacheStats = CacheStats(
        resourceFiles=resourceStore.fileCount(), resourceBytes=resourceStore.totalBytes(),
        examPackages=examStore.downloadedCount(), examBytes=examStore.storageBytes()
    )
    suspend fun appUpdate():AppUpdateInfo = api.appVersion()
    suspend fun examAnalysis(examId:String):ExamAnalysis {
        if(!isOnline()) throw ApiException("برای دریافت تحلیل آزمون، اینترنت را وصل کنید.")
        return api.examAnalysis(token(),examId)
    }

    fun localDashboard(): DashboardData? {
        val raw=cache.load()
        return raw.takeIf{it.isNotBlank()}?.let { runCatching { DashboardData.fromJson(org.json.JSONObject(it)) }.getOrNull() }
    }

    suspend fun login(nationalId: String, password: String, birthDate: String): LoginResult {
        val result = api.login(nationalId, password, birthDate)
        val previousStudentId = secure.loadStudentId()
        if (previousStudentId.isBlank() || previousStudentId != result.student.id) {
            clearOfflineData()
        }
        secure.saveSession(result.token, result.student.id)
        SyncWorker.schedulePeriodic(context)
        return result
    }

    suspend fun changePassword(newPassword: String, currentPassword: String = "") = api.changePassword(token(), newPassword, currentPassword)

    suspend fun syncAll(downloadContent:Boolean=true):SyncResult {
        val t=token()
        if(t.isBlank()) throw ApiException("نشست ورود وجود ندارد.",401)
        if(!isOnline()) {
            val local=localDashboard() ?: throw ApiException("این دستگاه هنوز اطلاعات آفلاین ندارد؛ یک‌بار با اینترنت وارد شوید.")
            return SyncResult(
                dashboard=local,
                offline=true,
                lastSyncAt=lastSyncAt(),
                downloadedExams=0,
                cachedResources=0,
                pendingExamUploads=pendingExamCount(),
                pendingHomeworkUploads=pendingHomeworkCount()
            )
        }

        flushPendingExamIncidents()
        flushPendingExamSubmissions()

        // Read server state before replaying homework Outbox. If a previous POST reached
        // the server but its HTTP response was lost, this reconciliation prevents a
        // duplicate teacher notification/audit entry on the retry.
        var data=api.dashboard(t)
        reconcileHomeworkOutbox(data)
        val homeworkSent=flushPendingHomeworkSubmissions()
        if(homeworkSent>0) data=api.dashboard(t)

        notifications.sync(data.student.id,data.notifications)
        cache.save(data.raw)
        var examCount=0
        var resourceCount=0
        if(downloadContent){
            for(exam in data.exams){
                if(exam.status=="submitted") continue
                if(exam.canStart && !examStore.hasPackage(exam.id,exam.packageVersion)){
                    runCatching {
                        val pack=OfflineExamPackage.fromJson(api.downloadExam(t,exam.id))
                        examStore.savePackage(pack); examCount++
                    }
                }
            }
            for(resource in data.resources){
                if(resourceStore.shouldPrefetch(resource) && !resourceStore.has(resource)){
                    runCatching {
                        val (bytes,_)=api.downloadResource(resource.url)
                        resourceStore.save(resource,bytes)
                        resourceCount++
                    }
                }
            }
        }
        return SyncResult(
            dashboard=data,
            offline=false,
            lastSyncAt=System.currentTimeMillis(),
            downloadedExams=examCount,
            cachedResources=resourceCount,
            pendingExamUploads=pendingExamCount(),
            pendingHomeworkUploads=pendingHomeworkCount()
        )
    }

    suspend fun dashboard(allowCache: Boolean = true): Pair<DashboardData, Boolean> {
        return try {
            val r=syncAll(true); r.dashboard to r.offline
        } catch(e:Exception){
            val local=localDashboard()
            if(allowCache && local!=null) local to true else throw e
        }
    }

    fun examLocalState(exam:ExamItem):ExamLocalState=examStore.localState(exam.id,exam.packageVersion)

    suspend fun ensureExamPackage(exam:ExamItem):OfflineExamPackage {
        examStore.loadPackage(exam.id)?.let { if(exam.packageVersion.isBlank() || it.packageVersion==exam.packageVersion) return it }
        if(!isOnline()) throw ApiException("سؤال‌های این آزمون هنوز روی گوشی دانلود نشده‌اند. برای دانلود، اینترنت را وصل کنید.")
        if(!exam.canStart) throw ApiException("دانلود سؤال‌ها طبق سیاست مدرسه از زمان شروع آزمون فعال می‌شود.")
        val pack=OfflineExamPackage.fromJson(api.downloadExam(token(),exam.id))
        examStore.savePackage(pack)
        return pack
    }

    suspend fun openExam(exam:ExamItem):OfflineExamSession {
        val pack=ensureExamPackage(exam)
        val existing=examStore.loadDraft(exam.id)
        if(existing?.status=="submitted") throw ApiException("این آزمون قبلاً تحویل شده است.")
        if(existing?.status=="queued") throw ApiException("پاسخنامه این آزمون نهایی شده و در صف ارسال است.")
        val draft=existing ?: OfflineExamDraft(examId=exam.id,startedAt=System.currentTimeMillis())
        examStore.saveDraft(draft)
        return OfflineExamSession(pack,draft)
    }

    fun saveExamAnswer(session:OfflineExamSession,questionId:String,value:String):OfflineExamSession {
        val d=session.draft.copy(answers=session.draft.answers.toMutableMap().apply{put(questionId,value)})
        examStore.saveDraft(d)
        return session.copy(draft=d)
    }

    suspend fun finalizeExam(session:OfflineExamSession):Pair<OfflineExamSession,Boolean> {
        val finalized=session.draft.copy(finalizedAt=System.currentTimeMillis(),status="queued",lastError="")
        examStore.saveDraft(finalized)
        SyncWorker.enqueue(context)
        if(!isOnline()) return session.copy(draft=finalized) to false
        return try {
            val sent=submitQueued(session.pack,finalized)
            session.copy(draft=sent) to true
        } catch(e:Exception){
            val queued=finalized.copy(retryCount=finalized.retryCount+1,lastError=e.message?:"خطای ارسال")
            examStore.saveDraft(queued)
            SyncWorker.enqueue(context)
            session.copy(draft=queued) to false
        }
    }

    private suspend fun submitQueued(pack:OfflineExamPackage,draft:OfflineExamDraft):OfflineExamDraft {
        val response=api.submitExam(token(),pack,draft)
        val sent=draft.copy(status="submitted",receiptId=response.string("receiptId","ثبت‌شده"),lastError="")
        examStore.saveDraft(sent)
        notifications.notifySubmission("پاسخنامه ارسال شد", "پاسخنامه آزمون ${pack.title} با موفقیت به مدرسه رسید.")
        return sent
    }

    suspend fun flushPendingExamSubmissions():Int {
        if(!isOnline() || token().isBlank()) return 0
        var sent=0
        for(draft in examStore.queuedDrafts()){
            val pack=examStore.loadPackage(draft.examId) ?: continue
            try { submitQueued(pack,draft); sent++ }
            catch(e:ApiException){
                val next=draft.copy(retryCount=draft.retryCount+1,lastError=e.message?:"خطای ارسال")
                examStore.saveDraft(next)
                if(e.status in listOf(409,410,422)) runCatching { api.requestExamDeliveryRecovery(token(),pack,next,e) }
            }
            catch(e:Exception){ examStore.saveDraft(draft.copy(retryCount=draft.retryCount+1,lastError=e.message?:"خطای ارسال")) }
        }
        return sent
    }

    fun cachedResourceUri(resource:ResourceItem):String?=resourceStore.uri(resource)

    suspend fun resourceUri(resource:ResourceItem):String {
        resourceStore.uri(resource)?.let{return it}
        if(!isOnline()) throw ApiException("این منبع هنوز روی گوشی ذخیره نشده است و برای دریافت آن اینترنت لازم است.")
        if(resourceStore.shouldPrefetch(resource)){
            val (bytes,_)=api.downloadResource(resource.url); resourceStore.save(resource,bytes)
            resourceStore.uri(resource)?.let{return it}
        }
        return resource.url
    }

    suspend fun joinOnlineClass(scheduleEntryId:String,date:String):String {
        if(!isOnline()) throw ApiException("برای ورود به کلاس آنلاین اینترنت لازم است.")
        return api.joinOnlineClass(token(),scheduleEntryId,date)
    }

    fun homeworkDraft(homeworkId:String):String=homeworkDrafts.load(homeworkId)
    fun saveHomeworkDraft(homeworkId:String,text:String)=homeworkDrafts.save(homeworkId,text)

    suspend fun submitHomework(homeworkId: String, text: String):Boolean {
        homeworkDrafts.save(homeworkId,text)
        val queued = homeworkOutbox.queue(homeworkId,text)
        SyncWorker.enqueue(context)
        if(!isOnline()) return false
        return try {
            sendQueuedHomework(queued)
            true
        } catch(e:ApiException) {
            if(e.status in 400..499 && e.status !in listOf(408,429)) {
                homeworkOutbox.remove(homeworkId)
                throw e
            }
            homeworkOutbox.save(queued.copy(retryCount=queued.retryCount+1,lastError=e.message?:"خطای ارسال"))
            SyncWorker.enqueue(context)
            false
        } catch(e:Exception) {
            homeworkOutbox.save(queued.copy(retryCount=queued.retryCount+1,lastError=e.message?:"خطای ارسال"))
            SyncWorker.enqueue(context)
            false
        }
    }

    private suspend fun sendQueuedHomework(item:QueuedHomeworkSubmission) {
        api.submitHomework(token(), item.homeworkId, item.text, item.submissionKey)
        homeworkOutbox.remove(item.homeworkId)
        homeworkDrafts.delete(item.homeworkId)
        notifications.notifySubmission("تکلیف ارسال شد", "تکلیف ذخیره‌شده با موفقیت برای مدرسه ارسال شد.")
    }

    suspend fun flushPendingHomeworkSubmissions():Int {
        if(!isOnline() || token().isBlank()) return 0
        var sent=0
        for(item in homeworkOutbox.all()) {
            try {
                sendQueuedHomework(item)
                sent++
            } catch(e:ApiException) {
                if(e.status==401 || e.status==403) throw e
                if(e.status in 400..499 && e.status !in listOf(408,429)) {
                    // Keep the editable draft, but stop retrying a permanently rejected payload.
                    homeworkOutbox.remove(item.homeworkId)
                } else {
                    homeworkOutbox.save(item.copy(retryCount=item.retryCount+1,lastError=e.message?:"خطای ارسال"))
                }
            } catch(e:Exception) {
                homeworkOutbox.save(item.copy(retryCount=item.retryCount+1,lastError=e.message?:"خطای ارسال"))
            }
        }
        return sent
    }


    private fun reconcileHomeworkOutbox(data:DashboardData):Int {
        var reconciled=0
        val byId=data.homework.associateBy { it.id }
        for(item in homeworkOutbox.all()) {
            val remote=byId[item.homeworkId] ?: continue
            if(remote.submitted && remote.submissionText.trim()==item.text.trim()) {
                homeworkOutbox.remove(item.homeworkId)
                homeworkDrafts.delete(item.homeworkId)
                reconciled++
            }
        }
        return reconciled
    }

    fun queueExamBackgroundIncident(session:OfflineExamSession) {
        val item=QueuedExamIncident(
            examId=session.pack.examId, type="other",
            description="دانش‌آموز هنگام آزمون اپ را به پس‌زمینه برد یا از صفحه آزمون خارج شد.",
            sessionKey=session.draft.submissionKey, visibility="background"
        )
        incidentStore.add(item)
        SyncWorker.enqueue(context)
    }

    suspend fun flushPendingExamIncidents():Int {
        if(!isOnline() || token().isBlank()) return 0
        var sent=0
        for(item in incidentStore.all()) {
            try { api.reportExamIncident(token(),item); incidentStore.remove(item.id); sent++ }
            catch(e:ApiException){
                if(e.status==401 || e.status==403) throw e
                if(e.status in 400..499 && e.status !in listOf(408,429)) incidentStore.remove(item.id)
                else incidentStore.add(item.copy(retryCount=item.retryCount+1))
            } catch(_:Exception) { incidentStore.add(item.copy(retryCount=item.retryCount+1)) }
        }
        return sent
    }

    fun clearDownloadedContent():CacheStats {
        resourceStore.clear()
        examStore.clearPackagesSafe()
        return cacheStats()
    }

    suspend fun updateProfile(profile: StudentProfile):StudentProfile {
        if(!isOnline()) throw ApiException("برای ذخیره تغییرات پروفایل اینترنت لازم است.")
        return api.updateProfile(token(), profile)
    }

    suspend fun logout() {
        val pendingExam = pendingExamCount()
        val pendingHomework = pendingHomeworkCount()
        if (pendingExam > 0 || pendingHomework > 0) {
            val parts = buildList {
                if(pendingExam>0) add("$pendingExam پاسخنامه آزمون")
                if(pendingHomework>0) add("$pendingHomework تکلیف")
            }
            throw ApiException("${parts.joinToString(" و ")} هنوز در صف ارسال است. ابتدا اینترنت را وصل و «بروزرسانی اطلاعات» را بزنید تا اطلاعات ارسال شوند.")
        }
        val t = token()
        if (t.isNotBlank() && isOnline()) api.logout(t)
        SyncWorker.cancel(context)
        secure.clear()
        clearOfflineData()
    }

    fun clearLocalSession() { secure.clear(); SyncWorker.cancel(context) }

    private fun clearOfflineData() {
        cache.clear()
        examStore.clear()
        resourceStore.clear()
        homeworkDrafts.clear()
        homeworkOutbox.clear()
        profilePrefs.edit().clear().apply()
        incidentStore.clear()
    }
}
