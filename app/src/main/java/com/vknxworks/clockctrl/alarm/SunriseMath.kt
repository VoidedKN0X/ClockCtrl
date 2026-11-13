package com.vknxworks.clockctrl.alarm

import java.time.Duration
import java.time.ZonedDateTime

/**
 * The timing and colours of a sunrise alarm, kept free of Android so they can be unit-tested.
 *
 * The sunrise lasts a real number of minutes before the ring: it is worked out on the instant time line, so a
 * daylight-saving change in between doesn't make it an hour longer or shorter, and an alarm just after midnight
 * starts its sunrise on the evening before.
 */
object SunriseMath {
    /** The lengths offered in the alarm editor. */
    val CHOICES = listOf(5, 10, 15, 30)

    /** A pre-alarm that arrives up to this late still starts the sunrise (Android may deliver it a little late). */
    const val LATE_GRACE_MS = 2 * 60_000L

    /** When the sunrise starts for a ring at [ring], or null when the alarm has no sunrise. */
    fun startAt(ring: ZonedDateTime, minutes: Int): ZonedDateTime? {
        if (minutes <= 0) return null
        return ring.toInstant().minus(Duration.ofMinutes(minutes.toLong())).atZone(ring.zone)
    }

    /** When the sunrise starts, in epoch ms, or null when the alarm has no sunrise. */
    fun startAtMillis(ringAt: Long, minutes: Int): Long? = if (minutes <= 0) null else ringAt - minutes * 60_000L

    /**
     * The pre-alarm to set for a ring at [ringAt] (epoch ms), or null when there is none to set: no sunrise, or
     * its start has already passed (an alarm set a few minutes ahead simply rings without one).
     */
    fun preAlarmAt(ringAt: Long, minutes: Int, now: Long): Long? {
        val start = startAtMillis(ringAt, minutes) ?: return null
        return if (start > now) start else null
    }

    /** A pre-alarm that fires at [now] starts the sunrise only when the ring is still ahead and not too far off. */
    fun shouldBegin(ringAt: Long, minutes: Int, now: Long): Boolean {
        if (minutes <= 0 || ringAt <= now) return false
        return ringAt - now <= minutes * 60_000L + LATE_GRACE_MS
    }

    /** How far the sunrise has come, 0 at its start to 1 at the ring. */
    fun progress(now: Long, startAt: Long, ringAt: Long): Float {
        if (ringAt <= startAt) return 1f
        return ((now - startAt).toFloat() / (ringAt - startAt)).coerceIn(0f, 1f)
    }

    /** Screen brightness (0..1) for a point in the sunrise: very dim at first, full at the ring. */
    fun brightness(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return (MIN_BRIGHTNESS + (1f - MIN_BRIGHTNESS) * p * p).coerceIn(MIN_BRIGHTNESS, 1f)
    }

    private const val MIN_BRIGHTNESS = 0.01f

    /** From a deep warm red through orange and peach to bright white. */
    private val STOPS = listOf(
        0.00f to 0xFF120300.toInt(),
        0.25f to 0xFF4A0E02.toInt(),
        0.50f to 0xFFB33A0A.toInt(),
        0.70f to 0xFFF08A3C.toInt(),
        0.85f to 0xFFFFD3A1.toInt(),
        1.00f to 0xFFFFFFFF.toInt(),
    )

    /** The page colour (ARGB) for a point in the sunrise. */
    fun colorAt(progress: Float): Int {
        val p = progress.coerceIn(0f, 1f)
        for (i in 1 until STOPS.size) {
            val (p1, c1) = STOPS[i]
            if (p <= p1) {
                val (p0, c0) = STOPS[i - 1]
                return mix(c0, c1, (p - p0) / (p1 - p0))
            }
        }
        return STOPS.last().second
    }

    /** Text on the page turns dark once the background is light enough. */
    fun darkText(progress: Float): Boolean = progress >= 0.72f

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
