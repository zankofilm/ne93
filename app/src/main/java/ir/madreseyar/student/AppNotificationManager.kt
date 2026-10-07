package ir.madreseyar.student

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AppNotificationManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("student_notifications_v1", Context.MODE_PRIVATE)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "اعلان‌های مدرسه‌یار", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تکالیف، آزمون‌ها، کارنامه و پیام‌های مدرسه"
                }
            )
        }
    }

    fun sync(studentId: String, notifications: List<NotificationItem>) {
        if (studentId.isBlank()) return
        val key = "seen_$studentId"
        val seen = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val current = notifications.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (!prefs.contains(key)) {
            prefs.edit().putStringSet(key, current).apply()
            return
        }
        notifications.asReversed().filter { it.id.isNotBlank() && it.id !in seen && !it.read }.forEach { item ->
            notify(item)
            seen += item.id
        }
        // Bound persistent state while retaining recent IDs.
        val bounded = (notifications.map { it.id } + seen).filter { it.isNotBlank() }.distinct().take(250).toSet()
        prefs.edit().putStringSet(key, bounded).apply()
    }

    fun notifySubmission(title: String, body: String) = notify(NotificationItem("local_${System.currentTimeMillis()}", title, body, "", false))

    private fun notify(item: NotificationItem) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title.ifBlank { "مدرسه‌یار" })
            .setContentText(item.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
    }

    companion object { private const val CHANNEL = "madreseyar_student_updates" }
}
