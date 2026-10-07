package ir.madreseyar.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StudentRoot(vm: StudentViewModel, openUrl: (String)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val session = state.session) {
        SessionState.Checking -> FullLoader("در حال بررسی ورود…")
        SessionState.LoggedOut -> LoginScreen(state, vm)
        is SessionState.ForcePassword -> ForcePasswordScreen(session.student, state, vm)
        SessionState.LoggedIn -> if(state.activeExam!=null) OfflineExamScreen(state,vm) else AppScaffold(state, vm, openUrl)
    }
}

@Composable
private fun LoginScreen(state: UiState, vm: StudentViewModel) {
    var nationalId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier=Modifier.fillMaxSize().padding(horizontal=24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center
        ) {
            Image(painter=painterResource(R.drawable.ic_launcher),contentDescription="مدرسه‌یار",modifier=Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text("مدرسه‌یار دانش‌آموز", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Black)
            Text("ورود امن به فضای آموزشی مدرسه", color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(nationalId,{nationalId=normalizeDigits(it).filter(Char::isDigit).take(10)},label={Text("کد ملی")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password,{password=it},label={Text("رمز عبور")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(birthDate,{birthDate=normalizeDigits(it).take(10)},label={Text("تاریخ تولد فقط برای اولین ورود")},placeholder={Text("مثلاً 1392/07/15")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("اگر اولین ورود است و هنوز رمز شخصی نداری، فیلد رمز را خالی بگذار و تاریخ تولد را وارد کن.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
            StateMessage(state)
            Button(
                onClick={vm.login(nationalId,password,birthDate)},
                enabled=!state.loading && nationalId.length==10 && (password.isNotBlank() || normalizeBirthDate(birthDate).length==8),
                modifier=Modifier.fillMaxWidth().padding(top=14.dp).height(52.dp),shape=RoundedCornerShape(16.dp)
            ) { if(state.loading) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp) else Text("ورود به حساب دانش‌آموز",fontWeight=FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            Text("ارتباط فقط از طریق HTTPS با سرور رسمی مدرسه انجام می‌شود.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
        }
    }
}

@Composable
private fun ForcePasswordScreen(student:StudentProfile,state:UiState,vm:StudentViewModel){
    var p1 by remember{mutableStateOf("")}; var p2 by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
        Text("ساخت رمز شخصی",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)
        Text("${student.fullName}، برای ادامه یک رمز حداقل ۸ نویسه‌ای بساز.",modifier=Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(p1,{p1=it},label={Text("رمز جدید")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp)); OutlinedTextField(p2,{p2=it},label={Text("تکرار رمز جدید")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        StateMessage(state)
        Button({vm.forcePassword(p1,p2)},enabled=!state.loading,modifier=Modifier.fillMaxWidth().padding(top=14.dp).height(52.dp)){Text("ثبت رمز و ادامه")}
    }
}

@Composable
private fun AppScaffold(state:UiState,vm:StudentViewModel,openUrl:(String)->Unit){
    val dash=state.dashboard
    Scaffold(
        topBar={AppTopBar(dash?.student,state.offline,state.loading,{vm.refresh()},state.profilePhotoDataUrl)},
        bottomBar={BottomTabs(state.selectedTab,vm::selectTab)}
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad)){
            when(state.selectedTab){
                AppTab.Home -> HomeScreen(dash,state,vm,openUrl)
                AppTab.Schedule -> ScheduleScreen(dash,vm,openUrl)
                AppTab.Homework -> HomeworkScreen(dash,state,vm)
                AppTab.Exams -> ExamsScreen(dash,state,vm)
                AppTab.Reports -> ReportsScreen(dash)
                AppTab.Attendance -> AttendanceScreen(dash)
                AppTab.Notifications -> NotificationsScreen(dash)
                AppTab.More -> MoreScreen(dash,state,vm,openUrl)
            }
            if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if(state.error.isNotBlank()||state.message.isNotBlank()){
                Snackbar(modifier=Modifier.align(Alignment.BottomCenter).padding(16.dp),action={TextButton({vm.clearMessage()}){Text("بستن")}}){Text(if(state.error.isNotBlank())state.error else state.message)}
            }
        }
    }
}

@Composable
private fun AppTopBar(student:StudentProfile?,offline:Boolean,loading:Boolean,refresh:()->Unit,photoDataUrl:String=""){
    Surface(color=MaterialTheme.colorScheme.surface, tonalElevation=1.dp){
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
            ProfileAvatar(photoDataUrl,student?.fullName.orEmpty(),44)
            Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                Text(student?.fullName?.ifBlank{"دانش‌آموز"}?:"دانش‌آموز",fontWeight=FontWeight.Black,fontSize=16.sp)
                Text("${student?.className.orEmpty()}${if(student?.academicYear?.isNotBlank()==true) " · ${student.academicYear}" else ""}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(offline) AssistChip(onClick={},label={Text("آفلاین")},leadingIcon={Icon(Icons.Outlined.CloudOff,null,Modifier.size(16.dp))})
            IconButton(onClick=refresh,enabled=!loading){Icon(Icons.Outlined.Refresh,"به‌روزرسانی")}
        }
    }
}

@Composable
private fun BottomTabs(selected:AppTab,onSelect:(AppTab)->Unit){
    val items=listOf(
        Triple(AppTab.Home,Icons.Outlined.Home,"خانه"),
        Triple(AppTab.Schedule,Icons.Outlined.CalendarMonth,"برنامه"),
        Triple(AppTab.Homework,Icons.Outlined.Assignment,"تکالیف"),
        Triple(AppTab.Exams,Icons.Outlined.Quiz,"آزمون"),
        Triple(AppTab.More,Icons.Outlined.MoreHoriz,"بیشتر")
    )
    val visibleSelected=if(selected in setOf(AppTab.Reports,AppTab.Attendance,AppTab.Notifications)) AppTab.More else selected
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=3.dp) { items.forEach{(tab,icon,label)->NavigationBarItem(selected=visibleSelected==tab,onClick={onSelect(tab)},icon={Icon(icon,null)},label={Text(label,maxLines=1,fontSize=10.sp,fontWeight=if(visibleSelected==tab) FontWeight.Bold else FontWeight.Normal)})} }
}

@Composable
private fun HomeScreen(d:DashboardData?,state:UiState,vm:StudentViewModel,openUrl:(String)->Unit){
    if(d==null){EmptyState("هنوز اطلاعاتی دریافت نشده است.",Icons.Outlined.CloudDownload);return}
    val next=d.todaySchedule.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.primary,shadowElevation=5.dp){
                Column(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=22.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("سلام ${d.student.firstName}",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Black)
                            Text("امروزت را با تمرکز شروع کن",color=Color.White.copy(alpha=.72f),fontSize=12.sp,modifier=Modifier.padding(top=3.dp))
                        }
                        Surface(shape=RoundedCornerShape(14.dp),color=Color.White.copy(alpha=.13f)){
                            Icon(Icons.Outlined.School,null,tint=Color.White,modifier=Modifier.padding(11.dp).size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Surface(shape=RoundedCornerShape(18.dp),color=Color.White.copy(alpha=.10f)){
                        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(if(next!=null) Icons.Outlined.Schedule else Icons.Outlined.EventAvailable,null,tint=Color.White)
                            Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                                Text(if(next!=null) "کلاس بعدی" else "برنامه امروز",color=Color.White.copy(alpha=.70f),fontSize=11.sp)
                                Text(if(next!=null) "${next.subject}  ·  ${next.startTime} تا ${next.endTime}" else "برای امروز کلاس دیگری ثبت نشده است.",color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp)
                            }
                        }
                    }
                    if(next!=null && next.deliveryMode!="in_person") TextButton(onClick={vm.joinOnlineClass(next.id,openUrl)},modifier=Modifier.align(Alignment.End)){Text("ورود به کلاس آنلاین ←",color=Color.White,fontWeight=FontWeight.Bold)}
                }
            }
        }
        item {
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Sync,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                        Text("به‌روزرسانی و ذخیره آفلاین",fontWeight=FontWeight.Bold)
                        Text(if(state.lastSyncAt>0) "آخرین بروزرسانی: ${PersianDate.dateTime(state.lastSyncAt)}" else "هنوز بروزرسانی کامل انجام نشده است.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("برنامه، تکالیف، کارنامه و آزمون‌های قابل دانلود روی گوشی نگهداری می‌شوند.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        val pendingTotal=state.pendingExamIds.size+state.pendingHomeworkIds.size
                        if(pendingTotal>0) Text("$pendingTotal مورد در صف ارسال خودکار",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    }
                    Button(onClick={vm::refresh},enabled=!state.loading){Text("بروزرسانی اطلاعات")}
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                MetricCard("تکلیف",d.homework.count{!it.submitted}.toString(),Icons.Outlined.Assignment,Modifier.weight(1f))
                MetricCard("آزمون",d.exams.count{it.status!="submitted"}.toString(),Icons.Outlined.Quiz,Modifier.weight(1f))
                MetricCard("غیبت",d.attendance.absent.toString(),Icons.Outlined.EventBusy,Modifier.weight(1f))
            }
        }
        item{SectionTitle("برنامه امروز",Icons.Outlined.Today)}
        if(d.todaySchedule.isEmpty()) item{CompactEmpty("برای امروز برنامه‌ای ثبت نشده است.")} else items(d.todaySchedule){ScheduleCard(it,vm,openUrl)}
        item{SectionTitle("دسترسی سریع",Icons.Outlined.DashboardCustomize)}
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={vm.selectTab(AppTab.Reports)},modifier=Modifier.weight(1f).height(52.dp)){Icon(Icons.Outlined.Assessment,null);Spacer(Modifier.width(5.dp));Text("کارنامه")}
                OutlinedButton(onClick={vm.selectTab(AppTab.Attendance)},modifier=Modifier.weight(1f).height(52.dp)){Icon(Icons.Outlined.FactCheck,null);Spacer(Modifier.width(5.dp));Text("حضور و غیاب")}
                OutlinedButton(onClick={vm.selectTab(AppTab.Notifications)},modifier=Modifier.weight(1f).height(52.dp)){Icon(Icons.Outlined.Notifications,null);Spacer(Modifier.width(5.dp));Text("اعلان‌ها")}
            }
        }
        item{SectionTitle("کارهای مهم",Icons.Outlined.NotificationsActive)}
        val notices=d.notifications.take(4)
        if(notices.isEmpty()) item{CompactEmpty("اعلان جدیدی نداری.")} else items(notices){n->InfoCard(n.title,n.message,Icons.Outlined.Notifications)}
        if(d.notifications.size>4) item { TextButton(onClick={vm.selectTab(AppTab.Notifications)},modifier=Modifier.fillMaxWidth()){Text("مشاهده همه اعلان‌ها (${d.notifications.size})")} }
        item{SectionTitle("آخرین وضعیت درسی",Icons.Outlined.TrendingUp)}
        if(d.report.isEmpty()) item{CompactEmpty("هنوز نتیجه‌ای ثبت نشده است.")} else items(d.report.take(5)){r->ProgressRow(r.subject,r.percent,r.count)}
    }
}

@Composable
private fun ScheduleScreen(d:DashboardData?,vm:StudentViewModel,openUrl:(String)->Unit){
    val days=listOf("شنبه","یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه")
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageHeader("برنامه هفتگی","ساعت، درس، دبیر و شیفت کلاس")}
        days.forEach{day->
            val rows=d?.weeklySchedule?.filter{it.day==day}.orEmpty()
            if(rows.isNotEmpty()){
                item{Text(day,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp))}
                items(rows){ScheduleCard(it,vm,openUrl)}
            }
        }
        if(d?.weeklySchedule.isNullOrEmpty()) item{EmptyState("برنامه هفتگی هنوز منتشر نشده است.",Icons.Outlined.CalendarMonth)}
    }
}

@Composable
private fun HomeworkScreen(d:DashboardData?,state:UiState,vm:StudentViewModel){
    var selected by remember{mutableStateOf<HomeworkItem?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageHeader("تکالیف","مشاهده، تحویل و پیگیری بازخورد معلم")}
        val list=d?.homework.orEmpty()
        if(list.isEmpty()) item{EmptyState("تکلیفی برای شما ثبت نشده است.",Icons.Outlined.AssignmentTurnedIn)} else items(list){h->
            val pending=state.pendingHomeworkIds.contains(h.id)
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().clickable{selected=h}){
                Column(Modifier.padding(16.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(h.title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        StatusPill(when{pending->"در صف ارسال";h.submitted->"تحویل شده";else->"در انتظار"},h.submitted || pending)
                    }
                    Text(h.subject,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                    if(h.description.isNotBlank())Text(h.description,maxLines=2,modifier=Modifier.padding(top=6.dp))
                    Text("مهلت: ${if(h.dueAt.isBlank()) "تعیین نشده" else PersianDate.shortDate(h.dueAt)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                    if(pending) Text("این تکلیف روی گوشی ذخیره شده و با اولین اتصال اینترنت خودکار ارسال می‌شود.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=6.dp))
                    if(h.feedback.isNotBlank())Text("بازخورد: ${h.feedback}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
                }
            }
        }
    }
    selected?.let{h-> HomeworkDialog(h,vm,onDismiss={selected=null},onSubmit={text->vm.submitHomework(h.id,text){selected=null}})}
}

@Composable
private fun HomeworkDialog(h:HomeworkItem,vm:StudentViewModel,onDismiss:()->Unit,onSubmit:(String)->Unit){
    var text by remember(h.id){mutableStateOf(vm.homeworkDraft(h.id).ifBlank{h.submissionText})}
    AlertDialog(onDismissRequest=onDismiss,title={Text(h.title)},text={Column{if(h.description.isNotBlank())Text(h.description);Spacer(Modifier.height(10.dp));OutlinedTextField(text,{text=it;vm.saveHomeworkDraft(h.id,it)},label={Text("پاسخ یا توضیح تحویل")},minLines=5,modifier=Modifier.fillMaxWidth())}},confirmButton={Button({onSubmit(text)}){Text(if(h.submitted)"ارسال مجدد" else "ارسال برای معلم")}},dismissButton={TextButton(onDismiss){Text("بستن")}})
}

@Composable
private fun ExamsScreen(d:DashboardData?,state:UiState,vm:StudentViewModel){
    val list=d?.exams.orEmpty()
    state.examAnalysis?.let { ExamAnalysisDialog(it,vm::closeExamAnalysis) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageHeader("آزمون‌ها","دانلود امن، اجرای آفلاین و تحویل هنگام اتصال اینترنت")}
        item{
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer){
                Text("هر آزمون وقتی زمان شروعش برسد، با بروزرسانی اپ روی گوشی دانلود می‌شود. پس از دانلود می‌توان آزمون را بدون اینترنت انجام داد.",Modifier.padding(14.dp),style=MaterialTheme.typography.bodySmall)
            }
        }
        if(list.isEmpty()) item{EmptyState("آزمونی برای شما منتشر نشده است.",Icons.Outlined.Quiz)} else items(list){ex->
            val downloaded=state.downloadedExamIds.contains(ex.id)
            val pending=state.pendingExamIds.contains(ex.id)
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(16.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(ex.title,fontWeight=FontWeight.Black);Text(ex.subject,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)}
                        StatusPill(when{ex.status=="submitted"->"تحویل‌شده";pending->"در صف ارسال";downloaded->"روی گوشی";ex.canStart->"آماده دانلود";else->"زمان‌بندی‌شده"},ex.status=="submitted"||downloaded)
                    }
                    Text("${if(ex.startAt.isBlank()) "شروع اعلام نشده" else PersianDate.smartDateTime(ex.startAt)} تا ${if(ex.endAt.isBlank()) "پایان اعلام نشده" else PersianDate.smartDateTime(ex.endAt)}${if(ex.durationMinutes>0) " · ${PersianDate.fa(ex.durationMinutes.toString())} دقیقه" else ""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))
                    when{
                        ex.status=="submitted" -> ExamResultCard(ex,vm)
                        pending -> Column{ Button(onClick={vm::refresh},modifier=Modifier.fillMaxWidth()){Text("ارسال پاسخنامه / بروزرسانی")}; Text("اگر ارسال مستقیم ممکن نباشد، اپ به‌صورت خودکار درخواست Recovery امن برای مدیر ثبت می‌کند و پس از تأیید دوباره ارسال می‌کند.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
                        downloaded -> Button(onClick={vm.startExam(ex)},modifier=Modifier.fillMaxWidth()){Text("شروع / ادامه آزمون آفلاین")}
                        ex.canStart -> Button(onClick={vm.startExam(ex)},modifier=Modifier.fillMaxWidth()){Text("دانلود و شروع آزمون")}
                        else -> OutlinedButton(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("دانلود سؤال‌ها در زمان شروع فعال می‌شود")}
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamResultCard(ex:ExamItem,vm:StudentViewModel){
    val score=ex.resultScore
    val total=ex.resultTotal
    val percent=ex.resultPercent
    Surface(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(14.dp),
        color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f)
    ){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            if(ex.gradingStatus=="final" && score!=null && total!=null){
                Text("نتیجه آزمون",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                Text("نمره: ${PersianDate.fa(formatExamNumber(score))} از ${PersianDate.fa(formatExamNumber(total))}",fontWeight=FontWeight.Bold)
                if(percent!=null) Text("درصد: ${PersianDate.fa(formatExamNumber(percent))}٪",style=MaterialTheme.typography.bodySmall)
                if(ex.correctCount!=null){
                    val q=ex.questionCount ?: 0
                    val answered=ex.answeredCount ?: 0
                    val wrong=(answered-ex.correctCount).coerceAtLeast(0)
                    val blank=(q-answered).coerceAtLeast(0)
                    Text("صحیح: ${PersianDate.fa(ex.correctCount.toString())} · غلط: ${PersianDate.fa(wrong.toString())} · بی‌پاسخ: ${PersianDate.fa(blank.toString())}",style=MaterialTheme.typography.bodySmall)
                }
                Text("تصحیح نهایی شده است",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Button(onClick={vm.openExamAnalysis(ex.id)},modifier=Modifier.fillMaxWidth()){ Icon(Icons.Outlined.Analytics,null); Spacer(Modifier.width(6.dp)); Text("تحلیل سؤال‌به‌سؤال آزمون") }
            }else{
                Text("پاسخنامه تحویل شده است",fontWeight=FontWeight.Bold)
                Text("در انتظار تصحیح و انتشار نتیجه توسط معلم",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExamAnalysisDialog(a:ExamAnalysis,onDismiss:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Column{Text("تحلیل آزمون",fontWeight=FontWeight.Black);Text(a.examTitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){Text("نمره ${PersianDate.fa(formatExamNumber(a.score))} از ${PersianDate.fa(formatExamNumber(a.total))} · ${PersianDate.fa(formatExamNumber(a.percent))}٪",Modifier.fillMaxWidth().padding(12.dp),fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)}}
            items(a.questions){q->Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(q.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("سؤال ${PersianDate.fa(q.number.toString())} · ${if(q.unanswered) "بی‌پاسخ" else if(q.isCorrect) "✓ درست" else "✗ غلط"}",fontWeight=FontWeight.Black,color=if(q.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error);Text(q.text,fontWeight=FontWeight.Bold);Text("پاسخ شما: ${if(q.unanswered) "—" else q.selectedText}");Text("پاسخ صحیح: ${q.correctText}",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text("نمره: ${PersianDate.fa(formatExamNumber(q.earnedScore))} از ${PersianDate.fa(formatExamNumber(q.score))}",style=MaterialTheme.typography.bodySmall);if(q.explanation.isNotBlank())Text("نکته: ${q.explanation}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        }},
        confirmButton={Button(onClick=onDismiss){Text("بستن")}}
    )
}

private fun formatExamNumber(v:Double):String = if(kotlin.math.abs(v-kotlin.math.round(v))<0.000001) kotlin.math.round(v).toLong().toString() else String.format(java.util.Locale.US,"%.2f",v).trimEnd('0').trimEnd('.')

@Composable
private fun ReportsScreen(d:DashboardData?){
    var semester by remember { mutableStateOf(1) }
    val report = if (semester == 1) d?.semester1 else d?.semester2
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageHeader("کارنامه", "کارنامه رسمی نیم‌سال اول و دوم") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (semester == 1) Button({ semester = 1 }, Modifier.weight(1f)) { Text("نیم‌سال اول") }
                else OutlinedButton({ semester = 1 }, Modifier.weight(1f)) { Text("نیم‌سال اول") }
                if (semester == 2) Button({ semester = 2 }, Modifier.weight(1f)) { Text("نیم‌سال دوم") }
                else OutlinedButton({ semester = 2 }, Modifier.weight(1f)) { Text("نیم‌سال دوم") }
            }
        }
        item { SectionTitle("تمام نمرات آزمون‌ها", Icons.Outlined.FactCheck) }
        val gradedExams = d?.exams.orEmpty().filter { it.status=="submitted" && it.gradingStatus=="final" && it.resultScore!=null && it.resultTotal!=null }
        if(gradedExams.isEmpty()) item { CompactEmpty("هنوز نمره آزمون نهایی‌شده‌ای ثبت نشده است.") }
        else items(gradedExams){ ex ->
            Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(ex.subject,fontWeight=FontWeight.Black);Text(ex.title,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Text("${PersianDate.fa(formatExamNumber(ex.resultScore!!))} / ${PersianDate.fa(formatExamNumber(ex.resultTotal!!))}",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionTitle("کارنامه رسمی نیم‌سال", Icons.Outlined.Assessment) }
        if (report == null || !report.published) {
            item { EmptyState("کارنامه این نیم‌سال هنوز توسط مدرسه منتشر نشده است.", Icons.Outlined.LockClock) }
        } else {
            item {
                Surface(shape=RoundedCornerShape(20.dp), color=MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("معدل نیم‌سال", fontWeight=FontWeight.Bold)
                            Text("کارنامه منتشرشده", style=MaterialTheme.typography.bodySmall)
                        }
                        Text(report.average, fontSize=28.sp, fontWeight=FontWeight.Black, color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(report.courses) { course ->
                Surface(shape=RoundedCornerShape(16.dp), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(course.subject, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
                            StatusPill(
                                if(course.status=="passed") "قبول" else if(course.status=="failed") "نیاز به پیگیری" else "در انتظار",
                                course.status=="passed"
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                            ScoreCell("مستمر", course.continuousScore)
                            ScoreCell("پایانی", course.finalExamScore)
                            ScoreCell("نهایی", course.finalScore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(d:DashboardData?){
    val notices=d?.notifications.orEmpty()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageHeader("مرکز اعلان‌ها","پیام‌ها و اطلاعیه‌های مدرسه در یکجا")}
        if(notices.isEmpty()) item{EmptyState("هنوز اعلان یا اطلاعیه‌ای برای شما ثبت نشده است.",Icons.Outlined.NotificationsNone)}
        else items(notices){n->
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top){
                    Icon(if(n.read) Icons.Outlined.Notifications else Icons.Outlined.NotificationsActive,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                        Text(n.title.ifBlank{"اطلاعیه مدرسه"},fontWeight=FontWeight.Bold)
                        if(n.message.isNotBlank()) Text(n.message,modifier=Modifier.padding(top=4.dp))
                        if(n.at.isNotBlank()) Text(PersianDate.shortDate(n.at),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                    }
                    if(!n.read) StatusPill("جدید",true)
                }
            }
        }
    }
}

@Composable
private fun AttendanceScreen(d:DashboardData?){
    val a=d?.attendance?:AttendanceSummary()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageHeader("حضور و غیاب","وضعیت رسمی زنگ‌به‌زنگ ثبت‌شده توسط مدرسه")}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MetricCard("حاضر",a.present.toString(),Icons.Outlined.CheckCircle,Modifier.weight(1f));MetricCard("غایب",a.absent.toString(),Icons.Outlined.Cancel,Modifier.weight(1f));MetricCard("تأخیر",a.late.toString(),Icons.Outlined.Schedule,Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MetricCard("موجه",a.excused.toString(),Icons.Outlined.Verified,Modifier.weight(1f));MetricCard("غیرموجه",a.unexcused.toString(),Icons.Outlined.Report,Modifier.weight(1f));MetricCard("بررسی",a.pending.toString(),Icons.Outlined.HourglassTop,Modifier.weight(1f))}}
        item{SectionTitle("آخرین رکوردها",Icons.Outlined.History)}
        if(a.recent.isEmpty()) item{CompactEmpty("هنوز حضور و غیاب زنگ‌به‌زنگی ثبت نشده است.")} else items(a.recent){r->InfoCard("${statusFa(r.status)} · ${r.subject}","${PersianDate.shortDate(r.date)} · ${PersianDate.fa(r.startTime)} تا ${PersianDate.fa(r.endTime)}${if(r.teacherName.isNotBlank()) " · ${r.teacherName}" else ""}",attendanceIcon(r.status))}
    }
}

@Composable
private fun MoreScreen(d:DashboardData?,state:UiState,vm:StudentViewModel,openUrl:(String)->Unit){
    var editProfile by remember { mutableStateOf(false) }
    val resources = d?.resources.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        item { PageHeader("بیشتر", "منابع، پروفایل و تنظیمات حساب") }
        item { SectionTitle("خدمات آموزشی", Icons.Outlined.DashboardCustomize) }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={vm.selectTab(AppTab.Reports)},modifier=Modifier.weight(1f).height(52.dp)){Icon(Icons.Outlined.Assessment,null);Spacer(Modifier.width(6.dp));Text("کارنامه")}
                OutlinedButton(onClick={vm.selectTab(AppTab.Attendance)},modifier=Modifier.weight(1f).height(52.dp)){Icon(Icons.Outlined.FactCheck,null);Spacer(Modifier.width(6.dp));Text("حضور و غیاب")}
            }
        }
        item {
            OutlinedButton(onClick={vm.selectTab(AppTab.Notifications)},modifier=Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Outlined.NotificationsActive,null);Spacer(Modifier.width(6.dp));Text("مرکز اعلان‌ها${if(d?.notifications?.isNotEmpty()==true) " · ${d.notifications.size}" else ""}")}
        }
        item { SectionTitle("پروفایل", Icons.Outlined.Person) }
        item {
            Surface(shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(d?.student?.fullName.orEmpty(), fontWeight=FontWeight.Black, fontSize=18.sp)
                    Text("${d?.student?.gradeName.orEmpty()} · ${d?.student?.className.orEmpty()}", color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top=10.dp)) {
                        OutlinedButton({editProfile=true}) {
                            Icon(Icons.Outlined.Edit,null); Spacer(Modifier.width(6.dp)); Text("ویرایش پروفایل")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton({vm.logout()}) {
                            Icon(Icons.Outlined.Logout,null); Spacer(Modifier.width(6.dp)); Text("خروج")
                        }
                    }
                }
            }
        }
        item { SectionTitle("منابع آموزشی", Icons.Outlined.MenuBook) }
        if (resources.isEmpty()) {
            item { CompactEmpty("منبع آموزشی فعالی ثبت نشده است.") }
        } else {
            items(resources) { resource ->
                val safe = resource.url.startsWith("https://")
                Surface(
                    shape=RoundedCornerShape(16.dp),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                    modifier=Modifier.fillMaxWidth().clickable(enabled=safe){vm.openResource(resource,openUrl)}
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Download,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                            Text(resource.title,fontWeight=FontWeight.Bold)
                            Text(resource.subject,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronLeft,null)
                    }
                }
            }
        }
        item { SectionTitle("نسخه برنامه", Icons.Outlined.SystemUpdate) }
        item {
            val update=state.appUpdate
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(16.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.Android,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                            Text("نسخه ${BuildConfig.VERSION_NAME}",fontWeight=FontWeight.Bold)
                            Text(if(update.updateAvailable) "نسخه ${update.latestVersion} آماده است" else "برنامه به‌روز است",style=MaterialTheme.typography.bodySmall,color=if(update.updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(update.updateAvailable && update.apkUrl.startsWith("https://")) Button(onClick={openUrl(update.apkUrl)}){Text("دریافت")}
                    }
                    if(update.notes.isNotBlank()) Text(update.notes,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
                    if(update.required) Text("این نسخه برای ادامه استفاده باید بروزرسانی شود.",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
                }
            }
        }
        item { SectionTitle("حافظه آفلاین", Icons.Outlined.Storage) }
        item {
            val bytes=state.cacheStats.resourceBytes+state.cacheStats.examBytes
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(16.dp)){
                    Text("${state.cacheStats.examPackages} آزمون و ${state.cacheStats.resourceFiles} فایل آموزشی روی گوشی",fontWeight=FontWeight.Bold)
                    Text("حجم تقریبی: ${formatBytes(bytes)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=6.dp))
                    Text("پاک‌سازی فقط فایل‌های قابل دانلود مجدد را حذف می‌کند؛ پاسخ آزمون، Draft تکلیف و صف ارسال حذف نمی‌شوند.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick={vm.clearDownloadedContent()},modifier=Modifier.padding(top=10.dp)){Icon(Icons.Outlined.CleaningServices,null);Spacer(Modifier.width(6.dp));Text("پاک‌کردن فایل‌های آفلاین") }
                }
            }
        }
        item { SectionTitle("امنیت", Icons.Outlined.Security) }
        item {
            InfoCard(
                "اتصال امن",
                "تمام درخواست‌های اپ فقط از طریق HTTPS به سرور رسمی مدرسه ارسال می‌شوند و Token ورود با Android Keystore روی دستگاه محافظت می‌شود.",
                Icons.Outlined.Lock
            )
        }
        item {
            Text(
                "نسخه 1.3.7 · Production Ready · Offline‑First · مدرسه‌یار دانش‌آموز",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.fillMaxWidth().padding(12.dp),
                textAlign=TextAlign.Center
            )
        }
    }
    if(editProfile && d!=null) ProfileDialog(d.student,{editProfile=false},{profile->vm.saveProfile(profile){editProfile=false}})
}

@Composable
private fun ProfileAvatar(dataUrl:String,name:String,size:Int=48){
    val bitmap=remember(dataUrl){
        runCatching {
            if(dataUrl.isBlank()) null else {
                val raw=dataUrl.substringAfter("base64,","")
                if(raw.isBlank()) null else { val bytes=Base64.decode(raw,Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes,0,bytes.size) }
            }
        }.getOrNull()
    }
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(size.dp)){
        if(bitmap!=null) Image(bitmap=bitmap.asImageBitmap(),contentDescription="عکس دانش‌آموز",modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(name.trim().take(1).ifBlank{"د"},fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)}
    }
}

private fun formatBytes(bytes:Long):String = when {
    bytes < 1024 -> "${PersianDate.fa(bytes.toString())} بایت"
    bytes < 1024L*1024L -> "${PersianDate.fa("%.1f".format(bytes/1024.0))} کیلوبایت"
    else -> "${PersianDate.fa("%.1f".format(bytes/(1024.0*1024.0)))} مگابایت"
}

@Composable
private fun ProfileDialog(p:StudentProfile,onDismiss:()->Unit,onSave:(StudentProfile)->Unit){
    var mobile by remember{mutableStateOf(p.studentMobile)};var email by remember{mutableStateOf(p.email)};var guardian by remember{mutableStateOf(p.guardianMobile)};var home by remember{mutableStateOf(p.homePhone)};var eName by remember{mutableStateOf(p.emergencyGuardianName)};var eRel by remember{mutableStateOf(p.emergencyGuardianRelation)};var eMobile by remember{mutableStateOf(p.emergencyGuardianMobile)};var address by remember{mutableStateOf(p.address)};var postal by remember{mutableStateOf(p.postalCode)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ویرایش پروفایل")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState())){ProfileField("موبایل دانش‌آموز",mobile){mobile=it};ProfileField("ایمیل",email){email=it};ProfileField("موبایل ولی",guardian){guardian=it};ProfileField("تلفن منزل",home){home=it};ProfileField("نام تماس اضطراری",eName){eName=it};ProfileField("نسبت",eRel){eRel=it};ProfileField("موبایل اضطراری",eMobile){eMobile=it};ProfileField("نشانی",address){address=it};ProfileField("کد پستی",postal){postal=it}}},confirmButton={Button({onSave(p.copy(studentMobile=mobile,email=email,guardianMobile=guardian,homePhone=home,emergencyGuardianName=eName,emergencyGuardianRelation=eRel,emergencyGuardianMobile=eMobile,address=address,postalCode=postal))}){Text("ذخیره")}},dismissButton={TextButton(onDismiss){Text("انصراف")}})
}

@Composable private fun ProfileField(label:String,value:String,onValue:(String)->Unit){OutlinedTextField(value,onValue,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),singleLine=label!="نشانی")}

@Composable
private fun ScheduleCard(s:ScheduleItem,vm:StudentViewModel,openUrl:(String)->Unit){
    Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(if(s.periodNo>0)"زنگ ${s.periodNo}" else "کلاس",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);Text("${s.startTime}\n${s.endTime}",fontSize=11.sp,textAlign=TextAlign.Center)}};Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(s.subject,fontWeight=FontWeight.Black);Text(listOf(s.teacherName,s.shiftName).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(s.deliveryMode!="in_person")FilledTonalIconButton(onClick={vm.joinOnlineClass(s.id,openUrl)}){Icon(Icons.Outlined.VideoCall,"ورود آنلاین")}}
    }
}

@Composable private fun MetricCard(title:String,value:String,icon:ImageVector,modifier:Modifier=Modifier){Surface(modifier,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=1.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.padding(vertical=15.dp,horizontal=10.dp),horizontalAlignment=Alignment.CenterHorizontally){Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(8.dp).size(20.dp))};Spacer(Modifier.height(7.dp));Text(value,fontSize=21.sp,fontWeight=FontWeight.Black);Text(title,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable private fun SectionTitle(title:String,icon:ImageVector){Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=4.dp,bottom=2.dp)){Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(7.dp).size(18.dp))};Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.Black,fontSize=17.sp)}}
@Composable private fun PageHeader(title:String,subtitle:String){Column{Text(title,fontSize=24.sp,fontWeight=FontWeight.Black);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun InfoCard(title:String,body:String,icon:ImageVector){Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.padding(horizontal=10.dp)){Text(title,fontWeight=FontWeight.Bold);if(body.isNotBlank())Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))}}}}
@Composable private fun CompactEmpty(text:String){Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),modifier=Modifier.fillMaxWidth()){Text(text,Modifier.padding(14.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun EmptyState(text:String,icon:ImageVector){Column(Modifier.fillMaxWidth().padding(36.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(text,Modifier.padding(top=12.dp),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun ProgressRow(subject:String,percent:Double,count:Int){Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.padding(14.dp)){Row{Text(subject,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("${percent.toInt()}٪ · $count آزمون",style=MaterialTheme.typography.bodySmall)};Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={ (percent/100.0).coerceIn(0.0,1.0).toFloat() },modifier=Modifier.fillMaxWidth())}}}
@Composable private fun StatusPill(text:String,positive:Boolean){Surface(shape=RoundedCornerShape(99.dp),color=if(positive)Color(0xFFE0F5EC) else Color(0xFFFFF0D8)){Text(text,modifier=Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=10.sp,color=if(positive)Color(0xFF087A57) else Color(0xFF9A5A00),fontWeight=FontWeight.Bold)}}
@Composable private fun ScoreCell(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,fontWeight=FontWeight.Black,fontSize=18.sp);Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun StateMessage(state:UiState){if(state.error.isNotBlank())Text(state.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=10.dp));if(state.message.isNotBlank())Text(state.message,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=10.dp))}
@Composable private fun FullLoader(text:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Text(text,Modifier.padding(top=12.dp))}}}

private fun statusFa(s:String)=when(s){"present"->"حاضر";"late"->"تأخیر";"absent_excused","excused"->"غیبت موجه";"absent_unexcused"->"غیبت غیرموجه";"absent_pending","absent"->"غایب؛ در انتظار بررسی";else->s.ifBlank{"ثبت نشده"}}
private fun attendanceIcon(s:String)=when(s){"present"->Icons.Outlined.CheckCircle;"late"->Icons.Outlined.Schedule;"absent_excused","excused"->Icons.Outlined.Verified;"absent_unexcused"->Icons.Outlined.Report;else->Icons.Outlined.EventBusy}
