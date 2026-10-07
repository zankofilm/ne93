package ir.madreseyar.student

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.string(name: String, fallback: String = ""): String =
    if (has(name) && !isNull(name)) optString(name, fallback) else fallback

fun JSONObject.int(name: String, fallback: Int = 0): Int =
    if (has(name) && !isNull(name)) optInt(name, fallback) else fallback

fun JSONObject.double(name: String, fallback: Double = 0.0): Double =
    if (has(name) && !isNull(name)) optDouble(name, fallback) else fallback

fun JSONObject.bool(name: String, fallback: Boolean = false): Boolean =
    if (has(name) && !isNull(name)) optBoolean(name, fallback) else fallback

fun JSONObject.obj(name: String): JSONObject = optJSONObject(name) ?: JSONObject()
fun JSONObject.arr(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(transform(it)) }
    return out
}
