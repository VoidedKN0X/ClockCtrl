package com.vknxworks.clockctrl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val alarms = listOf(
        Alarm(id = 4, hour = 6, minute = 45, days = setOf(1, 2, 3, 4, 5), label = "Work", ringtone = "content://media/x/1", sunriseMinutes = 15, challenge = 2, skipUntil = 123L),
        Alarm(id = 9, hour = 9, minute = 0, date = "2020-01-01", ringtone = ""),
    )

    @Test fun alarmsRoundTripWithNewIds() {
        val text = Backups.exportAlarms(alarms, "0.4.0", "2026-11-21T10:00:00Z")
        val b = Backups.parseAlarms(text)
        assertEquals(2, b.alarms.size)
        val restored = Backups.toAlarms(b, firstId = 50, today = LocalDate.of(2026, 11, 21)) { false }
        assertEquals(listOf(50, 51), restored.map { it.id })
        val work = restored[0]
        assertEquals(6, work.hour)
        assertEquals(setOf(1, 2, 3, 4, 5), work.days)
        assertEquals(15, work.sunriseMinutes)
        assertEquals(2, work.challenge)
        assertEquals(0L, work.skipUntil)
        // A ringtone this phone can't play becomes the default sound; silent stays silent.
        assertNull(work.ringtone)
        assertEquals("", restored[1].ringtone)
        // A one-time alarm on a past date is switched off.
        assertFalse(restored[1].enabled)
        assertNull(restored[1].date)
    }

    @Test fun playableRingtoneIsKept() {
        val b = Backups.parseAlarms(Backups.exportAlarms(alarms, "x", "y"))
        val r = Backups.toAlarms(b, 1, LocalDate.of(2026, 1, 1)) { true }
        assertEquals("content://media/x/1", r[0].ringtone)
    }

    private fun assertInvalid(text: String, newer: Boolean = false) {
        try {
            Backups.parseAlarms(text)
            fail("accepted: $text")
        } catch (e: BackupFormatException) {
            assertEquals(newer, e.newer)
        }
    }

    @Test fun rejectsBadFiles() {
        assertInvalid("not json")
        assertInvalid("""{"format":"clockctrl-settings","version":1}""")
        assertInvalid("""{"format":"clockctrl-alarms","version":2}""", newer = true)
        assertInvalid("""{"format":"clockctrl-alarms","version":1,"alarms":[{"hour":24,"minute":0}]}""")
        assertInvalid("""{"format":"clockctrl-alarms","version":1,"alarms":[{"hour":7,"minute":0,"days":[8]}]}""")
        assertInvalid("""{"format":"clockctrl-alarms","version":1,"alarms":[{"hour":7,"minute":0,"challenge":4}]}""")
        assertInvalid("""{"format":"clockctrl-alarms","version":1,"alarms":[{"hour":7,"minute":0,"date":"tomorrow"}]}""")
    }

    @Test fun checkRejectsOutOfRangeAlarms() {
        fun bad(a: BackupAlarm) = try {
            Backups.check(AlarmsBackup(alarms = listOf(a)))
            false
        } catch (e: BackupFormatException) {
            true
        }
        assertTrue(bad(BackupAlarm(hour = 24, minute = 0)))
        assertTrue(bad(BackupAlarm(hour = 7, minute = 60)))
        assertTrue(bad(BackupAlarm(hour = 7, minute = 0, days = setOf(0))))
        assertTrue(bad(BackupAlarm(hour = 7, minute = 0, challenge = 4)))
        assertTrue(bad(BackupAlarm(hour = 7, minute = 0, date = "tomorrow")))
        assertFalse(bad(BackupAlarm(hour = 7, minute = 0, days = setOf(1, 7), date = "2027-02-28")))
        try {
            Backups.check(AlarmsBackup(version = 2))
            fail()
        } catch (e: BackupFormatException) {
            assertTrue(e.newer)
        }
        val restored = Backups.toAlarms(AlarmsBackup(alarms = listOf(BackupAlarm(hour = 7, minute = 5, date = "2027-01-01", ringtone = "x"))), 3, LocalDate.of(2026, 11, 21)) { false }
        assertEquals(3, restored.single().id)
        assertTrue(restored.single().enabled)
        assertEquals("2027-01-01", restored.single().date)
        assertNull(restored.single().ringtone)
    }

    @Test fun applySettingsDirectly() {
        val d = Backups.applyClockSettings(SettingsBackup(snoozeMinutes = 0, gradualSeconds = 15, cities = listOf("A", "B", "A")), ClockData(snoozeMinutes = 10)) { true }
        assertEquals(10, d.snoozeMinutes)
        assertEquals(15, d.gradualSeconds)
        assertEquals(listOf("A", "B"), d.cities)
    }

    @Test fun settingsApplyOnlyValidValues() {
        val b = Backups.parseSettings(
            Backups.exportSettings(SettingsBackup(snoozeMinutes = 5, ringMinutes = 999, gradualSeconds = 60, timerVibrate = false, cities = listOf("Europe/Brussels", "Nowhere/City", "Europe/Brussels"))),
        )
        val d = Backups.applyClockSettings(b, ClockData(ringMinutes = 10)) { it != "Nowhere/City" }
        assertEquals(5, d.snoozeMinutes)
        assertEquals(10, d.ringMinutes)
        assertEquals(60, d.gradualSeconds)
        assertFalse(d.timerVibrate)
        assertEquals(listOf("Europe/Brussels"), d.cities)
    }

    @Test fun lapsAsCsvAndText() {
        val splits = listOf(12_340L, 30_000L, 3_725_070L)
        val csv = Backups.lapsCsv(splits, listOf("Lap", "Lap time", "Total"))
        assertEquals("Lap,Lap time,Total\r\n1,0:12.34,0:12.34\r\n2,0:17.66,0:30.00\r\n3,1:01:35.07,1:02:05.07\r\n", csv)
        val text = Backups.lapsText(splits.take(1)) { "Lap $it" }
        assertEquals("Lap 1\t0:12.34\t0:12.34", text)
        assertTrue(Backups.duration(-5).startsWith("0:00"))
    }
}
