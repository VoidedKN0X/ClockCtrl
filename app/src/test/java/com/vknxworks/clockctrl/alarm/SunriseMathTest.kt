package com.vknxworks.clockctrl.alarm

import com.vknxworks.clockctrl.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SunriseMathTest {
    private val brussels = ZoneId.of("Europe/Brussels")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime = LocalDateTime.of(y, mo, d, h, mi).atZone(zone)
    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    @Test
    fun startIsMinutesBeforeTheRing() {
        val ring = at(brussels, 2026, 6, 10, 7, 0)
        assertEquals(at(brussels, 2026, 6, 10, 6, 45), SunriseMath.startAt(ring, 15))
        assertEquals(ms(ring) - 30 * 60_000L, SunriseMath.startAtMillis(ms(ring), 30))
    }

    @Test
    fun noSunriseWhenOff() {
        val ring = at(brussels, 2026, 6, 10, 7, 0)
        assertNull(SunriseMath.startAt(ring, 0))
        assertNull(SunriseMath.startAtMillis(ms(ring), 0))
        assertNull(SunriseMath.preAlarmAt(ms(ring), 0, ms(ring) - 3_600_000L))
    }

    @Test
    fun crossesMidnightIntoThePreviousDay() {
        val ring = at(brussels, 2026, 6, 10, 0, 5)
        val start = SunriseMath.startAt(ring, 10)!!
        assertEquals(LocalDateTime.of(2026, 6, 9, 23, 55), start.toLocalDateTime())
        val ring2 = at(brussels, 2027, 1, 1, 0, 10)
        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 40), SunriseMath.startAt(ring2, 30)!!.toLocalDateTime())
    }

    @Test
    fun springForwardKeepsRealMinutes() {
        // 29 March 2026, Brussels: 02:00 CET jumps to 03:00 CEST.
        val ring = at(brussels, 2026, 3, 29, 3, 10)
        assertEquals(ZoneOffset.ofHours(2), ring.offset)
        val start = SunriseMath.startAt(ring, 15)!!
        assertEquals(Duration.ofMinutes(15), Duration.between(start, ring))
        // On the clock that's 01:55 winter time, an hour and a quarter earlier.
        assertEquals(LocalDateTime.of(2026, 3, 29, 1, 55), start.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(1), start.offset)
    }

    @Test
    fun fallBackKeepsRealMinutes() {
        // 25 October 2026, Brussels: 03:00 CEST falls back to 02:00 CET. 03:05 is after the change (CET).
        val ring = at(brussels, 2026, 10, 25, 3, 5)
        assertEquals(ZoneOffset.ofHours(1), ring.offset)
        val start = SunriseMath.startAt(ring, 30)!!
        assertEquals(Duration.ofMinutes(30), Duration.between(start, ring))
        // 02:35 in the second (winter-time) 02:00–03:00 hour.
        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 35), start.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(1), start.offset)

        // 02:30 happens twice: the alarm rings at the first one (summer time), the sunrise starts 10 real minutes before.
        val ambiguous = at(brussels, 2026, 10, 25, 2, 30)
        assertEquals(ZoneOffset.ofHours(2), ambiguous.offset)
        val s2 = SunriseMath.startAt(ambiguous, 10)!!
        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 20), s2.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), s2.offset)
    }

    @Test
    fun sunriseAcrossTheDstNightInNewYork() {
        // 8 March 2026, New York: 02:00 EST jumps to 03:00 EDT. An alarm at 03:15 with a 30-minute sunrise.
        val ring = at(newYork, 2026, 3, 8, 3, 15)
        val start = SunriseMath.startAt(ring, 30)!!
        assertEquals(LocalDateTime.of(2026, 3, 8, 1, 45), start.toLocalDateTime())
        assertEquals(30 * 60_000L, ms(ring) - ms(start))
    }

    @Test
    fun preAlarmOnlyWhenItsStartIsAhead() {
        val ring = ms(at(brussels, 2026, 6, 10, 7, 0))
        val start = ring - 15 * 60_000L
        assertEquals(start, SunriseMath.preAlarmAt(ring, 15, start - 1))
        assertNull(SunriseMath.preAlarmAt(ring, 15, start))
        assertNull(SunriseMath.preAlarmAt(ring, 15, ring - 60_000L))
    }

    @Test
    fun beginsOnlyWhenTheRingIsNearAndAhead() {
        val ring = 10_000_000_000L
        assertTrue(SunriseMath.shouldBegin(ring, 15, ring - 15 * 60_000L))
        assertTrue(SunriseMath.shouldBegin(ring, 15, ring - 15 * 60_000L - 60_000L)) // a minute early is fine
        assertTrue(SunriseMath.shouldBegin(ring, 15, ring - 60_000L)) // delivered late
        assertFalse(SunriseMath.shouldBegin(ring, 15, ring)) // already ringing
        assertFalse(SunriseMath.shouldBegin(ring, 15, ring - 60 * 60_000L)) // stale trigger for another ring
        assertFalse(SunriseMath.shouldBegin(ring, 0, ring - 60_000L))
    }

    @Test
    fun withAlarmNextOverMidnight() {
        // A Monday-only alarm at 00:10; Sunday 23:50 is before its 15-minute sunrise (23:55).
        val alarm = Alarm(id = 1, hour = 0, minute = 10, days = setOf(1), sunriseMinutes = 15)
        val sundayLate = at(brussels, 2026, 6, 7, 23, 50)
        val next = alarm.next(sundayLate)!!
        assertEquals(LocalDateTime.of(2026, 6, 8, 0, 10), next.toLocalDateTime())
        assertEquals(ms(at(brussels, 2026, 6, 7, 23, 55)), SunriseMath.preAlarmAt(ms(next), 15, ms(sundayLate)))
        // At 23:58 the sunrise has started: no new pre-alarm, but a late one still begins it.
        val during = at(brussels, 2026, 6, 7, 23, 58)
        assertNull(SunriseMath.preAlarmAt(ms(alarm.next(during)!!), 15, ms(during)))
        assertTrue(SunriseMath.shouldBegin(ms(next), 15, ms(during)))
    }

    @Test
    fun withAlarmNextOnTheSpringForwardDay() {
        // An everyday alarm at 02:30 doesn't exist on 29 March in Brussels: it rings at 03:30 summer time.
        val alarm = Alarm(id = 2, hour = 2, minute = 30, days = (1..7).toSet(), sunriseMinutes = 30)
        val from = at(brussels, 2026, 3, 29, 0, 30)
        val next = alarm.next(from)!!
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), next.toLocalDateTime())
        val pre = SunriseMath.preAlarmAt(ms(next), 30, ms(from))!!
        assertEquals(30 * 60_000L, ms(next) - pre)
        // 01:00 UTC is 03:00 summer time.
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 0), java.time.Instant.ofEpochMilli(pre).atZone(brussels).toLocalDateTime())
    }

    @Test
    fun skippedAlarmMovesTheSunriseToTheNextRing() {
        val alarm = Alarm(id = 3, hour = 7, minute = 0, days = (1..7).toSet(), sunriseMinutes = 10)
        val now = at(brussels, 2026, 6, 10, 6, 52) // during today's sunrise
        val today = alarm.next(now)!!
        val skipped = alarm.copy(skipUntil = ms(today) + 60_000L)
        val tomorrow = skipped.next(now)!!
        assertEquals(LocalDateTime.of(2026, 6, 11, 7, 0), tomorrow.toLocalDateTime())
        assertEquals(ms(tomorrow) - 10 * 60_000L, SunriseMath.preAlarmAt(ms(tomorrow), 10, ms(now)))
    }

    @Test
    fun progressBrightnessAndColour() {
        assertEquals(0f, SunriseMath.progress(0, 0, 1000), 0f)
        assertEquals(0.5f, SunriseMath.progress(500, 0, 1000), 0.0001f)
        assertEquals(1f, SunriseMath.progress(2000, 0, 1000), 0f)
        assertEquals(0f, SunriseMath.progress(-5, 0, 1000), 0f)
        assertEquals(1f, SunriseMath.brightness(1f), 0f)
        assertTrue(SunriseMath.brightness(0f) <= 0.02f)
        var last = -1f
        for (i in 0..20) {
            val b = SunriseMath.brightness(i / 20f)
            assertTrue(b >= last)
            last = b
        }
        assertEquals(0xFFFFFFFF.toInt(), SunriseMath.colorAt(1f))
        assertEquals(0xFF120300.toInt(), SunriseMath.colorAt(0f))
        // Warm in the middle: more red than blue.
        val mid = SunriseMath.colorAt(0.5f)
        assertTrue(((mid shr 16) and 0xFF) > (mid and 0xFF))
        assertFalse(SunriseMath.darkText(0.2f))
        assertTrue(SunriseMath.darkText(0.9f))
    }
}
