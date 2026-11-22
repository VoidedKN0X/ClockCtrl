package com.vknxworks.clockctrl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ClockStoreTest {
    @Test fun stoppingARingingTimerResetsIt() {
        val t = Timer(id = 3, label = "Tea", lengthMs = 180_000L, remainingMs = 0L, endAt = 0L, ringing = true)
        val s = t.stoppedAfterRinging()
        assertFalse(s.ringing)
        assertEquals(180_000L, s.remainingMs)
        assertEquals(0L, s.endAt)
        assertTrue(s.reset)
    }

    @Test fun stoppingLeavesATimerThatNoLongerRingsAlone() {
        // For example one that got +1 minute in the meantime: it must keep running.
        val running = Timer(id = 3, lengthMs = 180_000L, remainingMs = 60_000L, endAt = 5_000_000L, ringing = false)
        assertSame(running, running.stoppedAfterRinging())
    }

    @Test fun decodeReadsAValidFile() {
        val text = """{"alarms":[{"id":1,"hour":7,"minute":30}],"nextId":2,"someFutureField":true}"""
        val d = ClockStore.decode(text.toByteArray())
        assertEquals(1, d.alarms.size)
        assertEquals(7, d.alarms[0].hour)
        assertEquals(2, d.nextId)
    }

    @Test fun decodeRefusesADamagedFile() {
        for (bad in listOf("{\"alarms\":[{\"id\":1,", "not json", "{\"alarms\":\"x\"}")) {
            try {
                ClockStore.decode(bad.toByteArray())
                fail("decoded $bad")
            } catch (e: Exception) {
                // expected
            }
        }
    }

    @Test fun setAsideKeepsTheUnreadableFile() {
        val dir = Files.createTempDirectory("clock").toFile()
        try {
            val base = File(dir, "clock.json").apply { writeText("damaged") }
            val moved = ClockStore.setAside(base, 1234L)
            assertNotNull(moved)
            assertEquals("clock.unreadable-1234.json", moved!!.name)
            assertEquals("damaged", moved.readText())
            assertFalse(base.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun setAsideWithNoFileDoesNothing() {
        val dir = Files.createTempDirectory("clock").toFile()
        try {
            assertNull(ClockStore.setAside(File(dir, "clock.json"), 1L))
            assertEquals(0, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
