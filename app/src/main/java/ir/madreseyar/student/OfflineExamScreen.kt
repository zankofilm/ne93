package ir.madreseyar.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OfflineExamScreen(state:UiState, vm:StudentViewModel){
    val session=state.activeExam ?: return
    var confirm by remember{ mutableStateOf(false) }
    var now by remember{ mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.draft.startedAt){ while(true){ now=System.currentTimeMillis(); delay(1000) } }
    val durationDeadline=session.draft.startedAt + session.pack.durationMinutes*60_000L
    val deadline=if(session.pack.expiresAt>0L) minOf(durationDeadline,session.pack.expiresAt) else durationDeadline
    val remaining=(deadline-now).coerceAtLeast(0L)
    val mm=(remaining/60000).toInt(); val ss=((remaining%60000)/1000).toInt()
    LaunchedEffect(remaining,session.draft.status){
        if(remaining==0L && session.draft.status=="in_progress") vm.finishExam()
    }

    Scaffold(
        topBar={
            Surface(shadowElevation=2.dp){
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)){
                    TextButton(onClick=vm::closeExam){Text("بازگشت")}
                    Column(Modifier.weight(1f)){
                        Text(session.pack.title,fontWeight=FontWeight.Black)
                        Text("${session.pack.subject} · آفلاین مجاز",style=MaterialTheme.typography.bodySmall)
                    }
                    AssistChip(onClick={},label={Text("%02d:%02d".format(mm,ss))})
                }
            }
        },
        bottomBar={
            Surface(shadowElevation=4.dp){
                Button(onClick={confirm=true},modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).height(52.dp),shape=RoundedCornerShape(16.dp)){
                    Text("نهایی‌کردن پاسخنامه",fontWeight=FontWeight.Bold)
                }
            }
        }
    ){pad->
        LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{
                Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer){
                    Column(Modifier.padding(14.dp)){
                        Text("حالت امن آزمون فعال است",fontWeight=FontWeight.Bold)
                        Text("پاسخ‌ها روی همین گوشی ذخیره می‌شوند. اسکرین‌شات و ضبط صفحه در زمان آزمون مسدود است؛ خروج از اپ به‌عنوان رخداد آزمون ثبت می‌شود. با پایان زمان، پاسخنامه خودکار قفل می‌شود.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(session.pack.questions,key={it.id}){q->
                val value=session.draft.answers[q.id].orEmpty()
                Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                    Column(Modifier.padding(16.dp)){
                        Text("${q.number}. ${q.text}",fontWeight=FontWeight.Bold)
                        Text("${q.score} نمره",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=4.dp,bottom=8.dp))
                        if(q.isChoice){
                            q.choices.forEachIndexed{i,label->
                                Row(Modifier.fillMaxWidth().padding(vertical=2.dp)){
                                    RadioButton(selected=value==i.toString(),onClick={vm.answerExam(q.id,i.toString())})
                                    Text(label,Modifier.padding(top=12.dp))
                                }
                            }
                        }else{
                            OutlinedTextField(value=value,onValueChange={vm.answerExam(q.id,it)},label={Text("پاسخ")},minLines=4,modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item{Spacer(Modifier.height(72.dp))}
        }
    }
    if(confirm){
        AlertDialog(
            onDismissRequest={confirm=false},
            title={Text("تحویل نهایی آزمون")},
            text={Text("بعد از این مرحله پاسخ‌ها قفل می‌شوند. اگر اینترنت قطع باشد، پاسخنامه امن روی گوشی در صف ارسال باقی می‌ماند.")},
            confirmButton={Button(onClick={confirm=false;vm.finishExam()}){Text("تأیید و تحویل")}},
            dismissButton={TextButton(onClick={confirm=false}){Text("ادامه آزمون")}}
        )
    }
}
