package ir.madreseyar.student

import android.content.Context

class DashboardCache(context: Context) {
    private val prefs = context.getSharedPreferences("student_cache_meta", Context.MODE_PRIVATE)
    private val secure = EncryptedFileStore(context,"dashboard")
    fun save(json: String) { secure.put("dashboard",json); prefs.edit().putLong("savedAt", System.currentTimeMillis()).apply() }
    fun load(): String = secure.get("dashboard")
    fun savedAt(): Long = prefs.getLong("savedAt", 0L)
    fun clear() { secure.clear(); prefs.edit().clear().apply() }
}
