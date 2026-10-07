package ir.madreseyar.student

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Checking : SessionState
    data object LoggedOut : SessionState
    data class ForcePassword(val student: StudentProfile) : SessionState
    data object LoggedIn : SessionState
}

data class UiState(
    val session: SessionState = SessionState.Checking,
    val dashboard: DashboardData? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val message: String = "",
    val error: String = "",
    val selectedTab: AppTab = AppTab.Home,
    val lastSyncAt: Long = 0L,
    val downloadedExamIds: Set<String> = emptySet(),
    val pendingExamIds: Set<String> = emptySet(),
    val pendingHomeworkIds: Set<String> = emptySet(),
    val activeExam: OfflineExamSession? = null,
    val appUpdate: AppUpdateInfo = AppUpdateInfo(),
    val cacheStats: CacheStats = CacheStats(),
    val profilePhotoDataUrl: String = "",
    val examAnalysis: ExamAnalysis? = null,
    val analysisLoading: Boolean = false
)

enum class AppTab { Home, Schedule, Homework, Exams, Reports, Attendance, Notifications, More }

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var lastForegroundSyncAt: Long = 0L

    init {
        if (repository.hasSession()) {
            val local=repository.localDashboard()
            _state.value = _state.value.copy(
                session = SessionState.LoggedIn,
                dashboard = local,
                offline = !repository.isOnline(),
                lastSyncAt = repository.lastSyncAt(),
                cacheStats = repository.cacheStats(),
                profilePhotoDataUrl = repository.cachedProfilePhoto()
            )
            updateLocalStates(local)
            SyncWorker.schedulePeriodic(application)
            SyncWorker.enqueue(application)
            lastForegroundSyncAt = System.currentTimeMillis()
            autoSync(showLoading = local == null)
        } else _state.value = _state.value.copy(session = SessionState.LoggedOut)
    }

    fun selectTab(tab: AppTab) { _state.value = _state.value.copy(selectedTab = tab) }
    fun clearMessage() { _state.value = _state.value.copy(message="", error="") }

    fun onAppForeground() {
        if (_state.value.session != SessionState.LoggedIn || _state.value.activeExam != null) return
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncAt < 30_000L) return
        lastForegroundSyncAt = now
        SyncWorker.enqueue(getApplication<Application>())
        autoSync(showLoading = _state.value.dashboard == null)
    }

    fun onAppBackground() {
        val session=_state.value.activeExam ?: return
        if(session.draft.status=="in_progress") repository.queueExamBackgroundIncident(session)
    }

    fun login(nationalId: String, password: String, birthDate: String) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading=true,error="",message="")
            try {
                val result = repository.login(normalizeDigits(nationalId), password, normalizeBirthDate(birthDate))
                if (result.mustChangePassword) {
                    _state.value = _state.value.copy(loading=false, session=SessionState.ForcePassword(result.student))
                } else {
                    _state.value = _state.value.copy(session=SessionState.LoggedIn)
                    syncInternal(false,true)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading=false, session=SessionState.LoggedOut, error=e.message ?: "ورود انجام نشد.")
            }
        }
    }

    fun forcePassword(newPassword: String, repeat: String) {
        if (newPassword.length < 8) { _state.value=_state.value.copy(error="رمز جدید باید حداقل ۸ نویسه باشد."); return }
        if (newPassword != repeat) { _state.value=_state.value.copy(error="تکرار رمز با رمز جدید یکسان نیست."); return }
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try {
                repository.changePassword(newPassword)
                _state.value=_state.value.copy(session=SessionState.LoggedIn,loading=false,message="رمز شخصی با موفقیت ساخته شد.")
                syncInternal(false,true)
            } catch(e:Exception){ _state.value=_state.value.copy(loading=false,error=e.message?:"تغییر رمز انجام نشد.") }
        }
    }

    private fun autoSync(showLoading:Boolean=false){ viewModelScope.launch { syncInternal(showLoading,false) } }
    fun refresh() {
        SyncWorker.enqueue(getApplication<Application>())
        viewModelScope.launch { syncInternal(true,true) }
    }

    private suspend fun syncInternal(showLoading:Boolean,manual:Boolean) {
        if (showLoading) _state.value=_state.value.copy(loading=true,error="",message="")
        try {
            val r=repository.syncAll(downloadContent=true)
            val update=runCatching { repository.appUpdate() }.getOrDefault(_state.value.appUpdate)
            val profilePhoto=repository.syncProfilePhoto()
            _state.value=_state.value.copy(
                session=SessionState.LoggedIn,dashboard=r.dashboard,loading=false,offline=r.offline,lastSyncAt=r.lastSyncAt,error="",
                appUpdate=update, cacheStats=repository.cacheStats(), profilePhotoDataUrl=profilePhoto,
                message=if(manual && !r.offline) buildString {
                    append("اطلاعات با سرور همگام شد")
                    if(r.downloadedExams>0) append(" · ${r.downloadedExams} آزمون دانلود شد")
                    if(r.cachedResources>0) append(" · ${r.cachedResources} منبع ذخیره شد")
                    if(r.pendingExamUploads>0) append(" · ${r.pendingExamUploads} پاسخنامه آزمون در صف ارسال")
                    if(r.pendingHomeworkUploads>0) append(" · ${r.pendingHomeworkUploads} تکلیف در صف ارسال")
                    append(".")
                } else _state.value.message
            )
            updateLocalStates(r.dashboard)
        } catch(e: ApiException) {
            if (e.status==401 || e.status==403) {
                repository.clearLocalSession()
                _state.value=UiState(session=SessionState.LoggedOut,error="نشست ورود منقضی شده است. دوباره وارد شوید.")
            } else {
                val local=repository.localDashboard()
                if(local!=null){
                    _state.value=_state.value.copy(session=SessionState.LoggedIn,dashboard=local,loading=false,offline=true,lastSyncAt=repository.lastSyncAt(),cacheStats=repository.cacheStats(),error=if(manual) (e.message?:"به‌روزرسانی انجام نشد؛ اطلاعات ذخیره‌شده نمایش داده می‌شود.") else "")
                    updateLocalStates(local)
                } else _state.value=_state.value.copy(loading=false,error=e.message?:"دریافت اطلاعات انجام نشد.")
            }
        } catch(e:Exception){
            val local=repository.localDashboard()
            if(local!=null){_state.value=_state.value.copy(dashboard=local,loading=false,offline=true,lastSyncAt=repository.lastSyncAt(),cacheStats=repository.cacheStats(),error=if(manual)"اینترنت در دسترس نیست؛ اطلاعات ذخیره‌شده روی گوشی نمایش داده می‌شود." else "");updateLocalStates(local)}
            else _state.value=_state.value.copy(loading=false,error=e.message?:"ارتباط با سرور انجام نشد.")
        }
    }

    private fun updateLocalStates(d:DashboardData?){
        val exams=d?.exams.orEmpty()
        val downloaded=exams.filter{repository.examLocalState(it).downloaded}.map{it.id}.toSet()
        val pendingExams=exams.filter{repository.examLocalState(it).pendingUpload}.map{it.id}.toSet()
        _state.value=_state.value.copy(
            downloadedExamIds=downloaded,
            pendingExamIds=pendingExams,
            pendingHomeworkIds=repository.pendingHomeworkIds()
        )
    }

    fun openExamAnalysis(examId:String){
        viewModelScope.launch {
            _state.value=_state.value.copy(analysisLoading=true,error="",examAnalysis=null)
            try { _state.value=_state.value.copy(analysisLoading=false,examAnalysis=repository.examAnalysis(examId)) }
            catch(e:Exception){ _state.value=_state.value.copy(analysisLoading=false,error=e.message?:"تحلیل آزمون دریافت نشد.") }
        }
    }
    fun closeExamAnalysis(){ _state.value=_state.value.copy(examAnalysis=null) }

    fun startExam(exam:ExamItem){
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try{ val session=repository.openExam(exam); _state.value=_state.value.copy(loading=false,activeExam=session); updateLocalStates(_state.value.dashboard) }
            catch(e:Exception){_state.value=_state.value.copy(loading=false,error=e.message?:"آزمون باز نشد.")}
        }
    }

    fun answerExam(questionId:String,value:String){
        val session=_state.value.activeExam ?: return
        val updated=repository.saveExamAnswer(session,questionId,value)
        _state.value=_state.value.copy(activeExam=updated)
    }

    fun closeExam(){
        val session=_state.value.activeExam
        if(session?.draft?.status=="in_progress") repository.queueExamBackgroundIncident(session)
        _state.value=_state.value.copy(activeExam=null)
    }

    fun finishExam(){
        val session=_state.value.activeExam ?: return
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try{
                val (updated,sent)=repository.finalizeExam(session)
                _state.value=_state.value.copy(loading=false,activeExam=null,message=if(sent)"پاسخنامه با موفقیت به سرور ارسال شد." else "پاسخنامه روی گوشی قفل و در صف ارسال ذخیره شد. با اتصال اینترنت خودکار ارسال می‌شود.")
                updateLocalStates(_state.value.dashboard)
                if(sent) syncInternal(false,false)
            }catch(e:Exception){_state.value=_state.value.copy(loading=false,error=e.message?:"نهایی‌سازی آزمون انجام نشد.")}
        }
    }

    fun joinOnlineClass(scheduleEntryId:String,onReady:(String)->Unit) {
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try {
                val url=repository.joinOnlineClass(scheduleEntryId, java.time.LocalDate.now().toString())
                _state.value=_state.value.copy(loading=false)
                if(url.startsWith("https://")) onReady(url) else _state.value=_state.value.copy(error="لینک معتبر کلاس آنلاین دریافت نشد.")
            } catch(e:Exception){_state.value=_state.value.copy(loading=false,error=e.message?:"ورود به کلاس آنلاین انجام نشد.")}
        }
    }

    fun homeworkDraft(homeworkId:String):String=repository.homeworkDraft(homeworkId)
    fun saveHomeworkDraft(homeworkId:String,text:String)=repository.saveHomeworkDraft(homeworkId,text)

    fun submitHomework(homeworkId:String,text:String,onDone:()->Unit={}) {
        if(text.trim().length<2){_state.value=_state.value.copy(error="پاسخ یا توضیح تکلیف را وارد کنید.");return}
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try {
                val sent=repository.submitHomework(homeworkId,text.trim())
                updateLocalStates(_state.value.dashboard)
                _state.value=_state.value.copy(
                    loading=false,
                    message=if(sent) "تکلیف برای معلم ارسال شد." else "تکلیف روی گوشی قفل و در صف ارسال ذخیره شد؛ با اتصال اینترنت خودکار ارسال می‌شود."
                )
                onDone()
                if(sent) syncInternal(false,false)
            } catch(e:Exception){
                updateLocalStates(_state.value.dashboard)
                _state.value=_state.value.copy(loading=false,error=e.message?:"ارسال تکلیف انجام نشد.")
            }
        }
    }

    fun openResource(resource:ResourceItem,onReady:(String)->Unit){
        viewModelScope.launch {
            try{onReady(repository.resourceUri(resource))}catch(e:Exception){_state.value=_state.value.copy(error=e.message?:"باز کردن منبع انجام نشد.")}
        }
    }

    fun saveProfile(profile:StudentProfile,onDone:()->Unit={}) {
        if(profile.emergencyGuardianMobile.isBlank()) {_state.value=_state.value.copy(error="شماره تماس اضطراری ولی را وارد کنید.");return}
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try {
                repository.updateProfile(profile)
                syncInternal(false,false)
                _state.value=_state.value.copy(message="پروفایل ذخیره شد.")
                onDone()
            } catch(e:Exception){_state.value=_state.value.copy(loading=false,error=e.message?:"ذخیره پروفایل انجام نشد.")}
        }
    }

    fun clearDownloadedContent() {
        val stats=repository.clearDownloadedContent()
        _state.value=_state.value.copy(cacheStats=stats,message="فایل‌های قابل حذف آفلاین پاک شدند؛ پاسخ‌های درحال انجام و صف ارسال دست‌نخورده ماندند.")
        updateLocalStates(_state.value.dashboard)
    }

    fun logout() {
        viewModelScope.launch {
            _state.value=_state.value.copy(loading=true,error="")
            try {
                repository.logout()
                _state.value=UiState(session=SessionState.LoggedOut)
            } catch(e:Exception) {
                _state.value=_state.value.copy(loading=false,error=e.message?:"خروج از حساب انجام نشد.")
            }
        }
    }
}

fun normalizeDigits(value:String):String = value
    .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
    .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
    .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4')
    .replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')

fun normalizeBirthDate(value:String):String = normalizeDigits(value).filter { it.isDigit() }.take(8)
