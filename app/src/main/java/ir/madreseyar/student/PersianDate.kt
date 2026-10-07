package ir.madreseyar.student

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object PersianDate {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val faDigits = "۰۱۲۳۴۵۶۷۸۹"

    fun dateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), tehran)
        val (jy, jm, jd) = gregorianToJalali(z.year, z.monthValue, z.dayOfMonth)
        return fa("%04d/%02d/%02d %02d:%02d".format(jy, jm, jd, z.hour, z.minute))
    }

    fun smartDateTime(isoOrText:String):String {
        val raw=isoOrText.trim()
        if(raw.isBlank()) return "—"
        val epoch=runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
        if(epoch!=null) return dateTime(epoch)
        val date=shortDate(raw)
        val tm=Regex("[T ](\\d{2}):(\\d{2})").find(raw)
        return if(tm!=null) "$date ${fa(tm.groupValues[1]+":"+tm.groupValues[2])}" else date
    }

    fun shortDate(isoOrText: String): String {
        val raw = isoOrText.trim()
        if (raw.isBlank()) return "—"
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(raw) ?: return fa(raw)
        val (jy, jm, jd) = gregorianToJalali(
            m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()
        )
        return fa("%04d/%02d/%02d".format(jy, jm, jd))
    }

    fun fa(value: String): String = buildString(value.length) {
        for (c in value) append(if (c in '0'..'9') faDigits[c - '0'] else c)
    }

    // Deterministic Gregorian -> Jalali conversion, adapted from the public-domain jalaali algorithm.
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy: Int
        var gy2: Int
        if (gy > 1600) { jy = 979; gy2 = gy - 1600 } else { jy = 0; gy2 = gy - 621 }
        val gy3 = if (gm > 2) gy2 + 1 else gy2
        var days = 365 * gy2 + (gy3 + 3) / 4 - (gy3 + 99) / 100 + (gy3 + 399) / 400 - 80 + gd + gdm[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Triple(jy, jm, jd)
    }
}
