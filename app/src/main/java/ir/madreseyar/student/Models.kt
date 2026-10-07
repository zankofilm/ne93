package ir.madreseyar.student

import org.json.JSONObject

data class StudentProfile(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val nationalId: String = "",
    val studentCode: String = "",
    val classId: String = "",
    val className: String = "",
    val gradeName: String = "",
    val schoolName: String = "",
    val academicYear: String = "",
    val studentMobile: String = "",
    val email: String = "",
    val guardianMobile: String = "",
    val homePhone: String = "",
    val emergencyGuardianName: String = "",
    val emergencyGuardianRelation: String = "",
    val emergencyGuardianMobile: String = "",
    val address: String = "",
    val postalCode: String = ""
) {
    val fullName: String get() = "$firstName $lastName".trim()
    companion object {
        fun fromJson(o: JSONObject) = StudentProfile(
            id=o.string("id"), firstName=o.string("firstName"), lastName=o.string("lastName"),
            nationalId=o.string("nationalId"), studentCode=o.string("studentCode"), classId=o.string("classId"),
            className=o.string("className"), gradeName=o.string("gradeName"), schoolName=o.string("schoolName"),
            academicYear=o.string("academicYear"), studentMobile=o.string("studentMobile"), email=o.string("email"),
            guardianMobile=o.string("guardianMobile"), homePhone=o.string("homePhone"),
            emergencyGuardianName=o.string("emergencyGuardianName"), emergencyGuardianRelation=o.string("emergencyGuardianRelation"),
            emergencyGuardianMobile=o.string("emergencyGuardianMobile"), address=o.string("address"), postalCode=o.string("postalCode")
        )
    }
}

data class ScheduleItem(
    val id: String,
    val day: String,
    val subject: String,
    val teacherName: String,
    val startTime: String,
    val endTime: String,
    val periodNo: Int,
    val shiftName: String,
    val deliveryMode: String,
    val meetingUrl: String
) {
    companion object {
        fun fromJson(o: JSONObject) = ScheduleItem(
            id=o.string("id"), day=o.string("day"), subject=o.string("subject","درس"),
            teacherName=o.string("teacherName"), startTime=o.string("startTime"), endTime=o.string("endTime"),
            periodNo=o.int("periodNo"), shiftName=o.string("shiftName"), deliveryMode=o.string("deliveryMode","in_person"),
            meetingUrl=o.string("meetingUrl")
        )
    }
}

data class HomeworkItem(
    val id: String,
    val title: String,
    val subject: String,
    val description: String,
    val dueAt: String,
    val submitted: Boolean,
    val submissionText: String,
    val scoreText: String,
    val feedback: String
) {
    companion object {
        fun fromJson(o: JSONObject): HomeworkItem {
            val submission=o.optJSONObject("submission")
            val score = when {
                submission == null -> ""
                submission.has("score") && !submission.isNull("score") -> submission.opt("score").toString()
                else -> ""
            }
            return HomeworkItem(
                id=o.string("id"), title=o.string("title","تکلیف"), subject=o.string("subject"),
                description=o.string("description"), dueAt=o.string("dueAt"), submitted=submission!=null,
                submissionText=submission?.optString("text","") ?: "", scoreText=score,
                feedback=submission?.optString("feedback","") ?: ""
            )
        }
    }
}

data class ExamItem(
    val id: String,
    val title: String,
    val subject: String,
    val type: String,
    val status: String,
    val startAt: String,
    val endAt: String,
    val availabilityState: String,
    val canStart: Boolean,
    val packageVersion: String,
    val durationMinutes: Int,
    val gradingStatus: String = "",
    val resultScore: Double? = null,
    val resultTotal: Double? = null,
    val correctCount: Int? = null,
    val answeredCount: Int? = null,
    val questionCount: Int? = null
) {
    val resultPercent: Double? get() = if (resultScore != null && resultTotal != null && resultTotal > 0) resultScore / resultTotal * 100.0 else null
    companion object {
        fun fromJson(o: JSONObject): ExamItem {
            val info=o.optJSONObject("submissionInfo")
            fun nullableDouble(name:String):Double? = if(info!=null && info.has(name) && !info.isNull(name)) info.optDouble(name) else null
            fun nullableInt(name:String):Int? = if(info!=null && info.has(name) && !info.isNull(name)) info.optInt(name) else null
            return ExamItem(
                id=o.string("id"), title=o.string("title","آزمون"), subject=o.string("subject"), type=o.string("type","mcq"),
                status=o.string("studentStatus", o.string("status")), startAt=o.string("startAt"), endAt=o.string("endAt"),
                availabilityState=o.string("availabilityState"), canStart=o.bool("canStart"), packageVersion=o.string("packageVersion"),
                durationMinutes=o.int("duration").coerceAtLeast(0), gradingStatus=info?.optString("gradingStatus","") ?: "",
                resultScore=nullableDouble("score"), resultTotal=nullableDouble("total"), correctCount=nullableInt("correctCount"),
                answeredCount=nullableInt("answeredCount"), questionCount=nullableInt("questionCount")
            )
        }
    }
}

data class ExamAnalysisQuestion(
    val number:Int,val text:String,val selectedText:String,val correctText:String,val isCorrect:Boolean,val unanswered:Boolean,val earnedScore:Double,val score:Double,val explanation:String
) { companion object { fun fromJson(o:JSONObject):ExamAnalysisQuestion {
    val choices=o.arr("choices").let { a -> (0 until a.length()).map { i -> a.optString(i, "") } }
    val selectedIndex=if(o.has("selectedIndex")&&!o.isNull("selectedIndex")) o.optInt("selectedIndex",-1) else -1
    val correctIndex=if(o.has("correctIndex")&&!o.isNull("correctIndex")) o.optInt("correctIndex",-1) else -1
    val selected=o.string("selectedText",o.string("selectedAnswer")).ifBlank { if(selectedIndex in choices.indices) choices[selectedIndex] else "" }
    val correct=o.string("correctText",o.string("correctAnswer")).ifBlank { if(correctIndex in choices.indices) choices[correctIndex] else "" }
    return ExamAnalysisQuestion(o.int("number"),o.string("text"),selected,correct,o.bool("isCorrect",o.bool("correct")),o.bool("unanswered",!o.bool("answered",true)),o.double("earnedScore"),o.double("score",o.double("maxScore")),o.string("explanation"))
} } }

data class ExamAnalysis(val examTitle:String,val subject:String,val score:Double,val total:Double,val percent:Double,val questions:List<ExamAnalysisQuestion>) {
    companion object { fun fromJson(o:JSONObject):ExamAnalysis { val ex=o.obj("exam"); val r=o.obj("result"); return ExamAnalysis(ex.string("title"),ex.string("subject"),r.double("score"),r.double("total"),r.double("percent"),o.arr("questions").mapObjects(ExamAnalysisQuestion::fromJson)) } }
}

data class NotificationItem(val id:String,val title:String,val message:String,val at:String,val read:Boolean) {
    companion object { fun fromJson(o:JSONObject)=NotificationItem(o.string("id"),o.string("title"),o.string("message",o.string("body")),o.string("at"),o.bool("read")) }
}

data class AttendanceSummary(
    val present:Int=0,val absent:Int=0,val pending:Int=0,val excused:Int=0,val unexcused:Int=0,val late:Int=0,val total:Int=0,
    val recent:List<AttendanceRecord> = emptyList()
) {
    companion object {
        fun fromJson(o:JSONObject)=AttendanceSummary(
            present=o.int("present"), absent=o.int("absent"), pending=o.int("pending"), excused=o.int("excused"),
            unexcused=o.int("unexcused"), late=o.int("late"), total=o.int("total"),
            recent=o.arr("recent").mapObjects(AttendanceRecord::fromJson)
        )
    }
}

data class AttendanceRecord(val date:String,val status:String,val subject:String,val startTime:String,val endTime:String,val teacherName:String) {
    companion object { fun fromJson(o:JSONObject)=AttendanceRecord(o.string("date"),o.string("status"),o.string("subject"),o.string("startTime"),o.string("endTime"),o.string("teacherName")) }
}

data class SubjectReport(val subject:String,val percent:Double,val count:Int) {
    companion object { fun fromJson(o:JSONObject)=SubjectReport(o.string("subject"),o.double("percent"),o.int("count")) }
}

data class TermCourse(
    val subject:String,val continuousScore:String,val finalExamScore:String,val finalScore:String,val status:String
) {
    companion object {
        fun fromJson(o:JSONObject)=TermCourse(
            subject=o.string("subject"),
            continuousScore=displayNumber(o.opt("continuousScore")),
            finalExamScore=displayNumber(o.opt("finalExamScore")),
            finalScore=displayNumber(o.opt("finalScore")),
            status=o.string("status")
        )
        private fun displayNumber(v:Any?):String = when(v){null, JSONObject.NULL -> "—"; is Number -> if(v.toDouble()%1.0==0.0) v.toInt().toString() else "%.2f".format(v.toDouble()); else -> v.toString()}
    }
}

data class TermReport(val period:String,val published:Boolean,val average:String,val courses:List<TermCourse>) {
    companion object {
        fun fromJson(period:String,o:JSONObject):TermReport {
            val rows = when {
                o.has("completeSubjects") -> o.arr("completeSubjects")
                o.has("courses") -> o.arr("courses")
                o.has("subjects") -> o.arr("subjects")
                o.has("rows") -> o.arr("rows")
                else -> org.json.JSONArray()
            }
            val avg = listOf("average","termAverage","gpa").firstNotNullOfOrNull { k -> if(o.has(k)&&!o.isNull(k)) o.opt(k)?.toString() else null } ?: "—"
            val published = o.string("reportCardStatus") == "published" || o.bool("published",o.bool("isPublished",false))
            return TermReport(period,published,avg,rows.mapObjects(TermCourse::fromJson))
        }
    }
}

data class ResourceItem(val id:String,val title:String,val subject:String,val url:String,val description:String) {
    companion object { fun fromJson(o:JSONObject)=ResourceItem(o.string("id"),o.string("title","منبع آموزشی"),o.string("subject"),o.string("url",o.string("link")),o.string("description")) }
}

data class DashboardData(
    val student:StudentProfile=StudentProfile(),
    val todaySchedule:List<ScheduleItem> = emptyList(),
    val weeklySchedule:List<ScheduleItem> = emptyList(),
    val homework:List<HomeworkItem> = emptyList(),
    val exams:List<ExamItem> = emptyList(),
    val notifications:List<NotificationItem> = emptyList(),
    val attendance:AttendanceSummary=AttendanceSummary(),
    val report:List<SubjectReport> = emptyList(),
    val semester1:TermReport=TermReport("semester1",false,"—",emptyList()),
    val semester2:TermReport=TermReport("semester2",false,"—",emptyList()),
    val resources:List<ResourceItem> = emptyList(),
    val raw:String=""
) {
    companion object {
        fun fromJson(o:JSONObject):DashboardData {
            val terms=o.obj("termReports")
            return DashboardData(
                student=StudentProfile.fromJson(o.obj("student")),
                todaySchedule=o.arr("todaySchedule").mapObjects(ScheduleItem::fromJson),
                weeklySchedule=o.arr("weeklySchedule").mapObjects(ScheduleItem::fromJson),
                homework=o.arr("homework").mapObjects(HomeworkItem::fromJson),
                exams=o.arr("exams").mapObjects(ExamItem::fromJson),
                notifications=o.arr("notifications").mapObjects(NotificationItem::fromJson),
                attendance=AttendanceSummary.fromJson(o.obj("attendance")),
                report=o.arr("report").mapObjects(SubjectReport::fromJson),
                semester1=TermReport.fromJson("semester1",terms.obj("semester1")),
                semester2=TermReport.fromJson("semester2",terms.obj("semester2")),
                resources=o.arr("resources").mapObjects(ResourceItem::fromJson),
                raw=o.toString()
            )
        }
    }
}


data class AppUpdateInfo(
    val latestVersion:String="1.3.0",
    val latestVersionCode:Int=6,
    val minSupportedVersionCode:Int=1,
    val apkUrl:String="",
    val notes:String="",
    val required:Boolean=false
) {
    val updateAvailable:Boolean get() = latestVersionCode > BuildConfig.VERSION_CODE
    companion object {
        fun fromJson(o:JSONObject)=AppUpdateInfo(
            latestVersion=o.string("latestVersion",BuildConfig.VERSION_NAME),
            latestVersionCode=o.int("latestVersionCode",BuildConfig.VERSION_CODE),
            minSupportedVersionCode=o.int("minSupportedVersionCode",1),
            apkUrl=o.string("apkUrl"), notes=o.string("notes"),
            required=o.bool("required", o.int("minSupportedVersionCode",1) > BuildConfig.VERSION_CODE)
        )
    }
}

data class LoginResult(val token:String,val student:StudentProfile,val mustChangePassword:Boolean)
