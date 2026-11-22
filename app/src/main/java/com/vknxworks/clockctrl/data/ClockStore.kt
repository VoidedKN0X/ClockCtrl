package com.vknxworks.clockctrl.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors

@Serializable
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** ISO days (1 = Monday ... 7 = Sunday). Empty: rings once. */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    /** Ringtone URI; null is the phone's default alarm sound, "" is silent. */
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    /** A one-time alarm on a fixed date (yyyy-MM-dd), or null for the next time it comes round. */
    val date: String? = null,
    /** Skip the next ring of a repeating alarm, until this moment (epoch ms). */
    val skipUntil: Long = 0L,
    /** Sunrise: the screen brightens for this many minutes before the alarm rings (0: off). */
    val sunriseMinutes: Int = 0,
    /** Dismiss only after solving a sum: 0 off, 1 easy, 2 medium, 3 hard. Snooze is never guarded. */
    val challenge: Int = 0,
) {
    val repeating: Boolean get() = days.isNotEmpty()

    /** The next time this alarm rings after [from], or null when it never will (a past one-time date). */
    fun next(from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val zone = from.zone
        val fixed = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (fixed != null && !repeating) {
            val t = LocalDateTime.of(fixed, java.time.LocalTime.of(hour, minute)).atZone(zone)
            return if (t.isAfter(from)) t else null
        }
        val after = if (skipUntil > from.toInstant().toEpochMilli()) ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(skipUntil), zone) else from
        var day = after.toLocalDate()
        repeat(8) {
            val t = LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(zone)
            if (t.isAfter(after) && (!repeating || day.dayOfWeek.value in days)) return t
            day = day.plusDays(1)
        }
        return null
    }
}

@Serializable
data class Timer(
    val id: Int,
    val label: String = "",
    /** How long it was set for. */
    val lengthMs: Long,
    /** Left when paused (or the full length when reset). */
    val remainingMs: Long,
    /** When it ends (epoch ms) while running; 0 when not running. */
    val endAt: Long = 0L,
    val ringing: Boolean = false,
) {
    val running: Boolean get() = endAt > 0L && !ringing
    fun left(now: Long = System.currentTimeMillis()): Long = if (running) (endAt - now).coerceAtLeast(0) else remainingMs
    val reset: Boolean get() = !running && !ringing && remainingMs == lengthMs

    /** After Stop on a finished timer: no longer ringing, back to its starting length. Unchanged when it isn't ringing. */
    fun stoppedAfterRinging(): Timer = if (ringing) copy(ringing = false, remainingMs = lengthMs, endAt = 0L) else this
}

@Serializable
data class Stopwatch(
    /** Wall-clock start of the current run (epoch ms), 0 while stopped. */
    val runningSince: Long = 0L,
    /** Time counted before the current run. */
    val accumulatedMs: Long = 0L,
    /** Lap split times (total time at each lap). */
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = runningSince > 0L
    fun elapsed(now: Long = System.currentTimeMillis()): Long = accumulatedMs + if (running) (now - runningSince).coerceAtLeast(0) else 0
}

@Serializable
data class ClockData(
    val alarms: List<Alarm> = emptyList(),
    val timers: List<Timer> = emptyList(),
    val stopwatch: Stopwatch = Stopwatch(),
    /** World clock: time zone ids. */
    val cities: List<String> = emptyList(),
    val nextId: Int = 1,
    /** Alarm settings live here too (not in the app's settings file) so they work before the first unlock. */
    val snoozeMinutes: Int = 10,
    /** An alarm nobody stops goes quiet after this long (and shows as missed). */
    val ringMinutes: Int = 10,
    /** The alarm sound grows from quiet to full over this many seconds (0: full right away). */
    val gradualSeconds: Int = 30,
    /** Sound for finished timers: null is the phone's default alarm sound, "" is silent. */
    val timerRingtone: String? = null,
    val timerVibrate: Boolean = true,
)

/**
 * Alarms, timers, the stopwatch and world clock, in one JSON file in device-protected storage: alarms must be
 * readable right after a restart, before the phone is unlocked for the first time.
 */
object ClockStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _data = MutableStateFlow(ClockData())
    val data: StateFlow<ClockData> = _data
    @Volatile private var loaded = false
    private val lock = Any()

    private fun file(context: Context): AtomicFile {
        val dp = context.applicationContext.createDeviceProtectedStorageContext()
        return AtomicFile(File(dp.filesDir, "clock.json"))
    }

    fun load(context: Context): ClockData {
        if (loaded) return _data.value
        synchronized(lock) {
            if (!loaded) {
                _data.value = readOrSetAside(file(context).baseFile, System.currentTimeMillis())
                loaded = true
            }
        }
        return _data.value
    }

    /** Changes the data and saves it at once (small file, atomic write). */
    fun update(context: Context, transform: (ClockData) -> ClockData): ClockData {
        synchronized(lock) {
            load(context)
            val next = transform(_data.value)
            write(context, next)
            _data.value = next
            return next
        }
    }

    /**
     * Changes the data at once but saves it a moment later on a background thread, so a tap doesn't wait for the
     * disk. Only for the stopwatch buttons in the open app: alarms and timers always use [update].
     *
     * The background save writes whatever the newest data is when it runs, under the same lock as [update],
     * so it can never put an older state over a newer one, whatever order the saves happen in.
     */
    fun updateDeferred(context: Context, transform: (ClockData) -> ClockData): ClockData {
        val app = context.applicationContext
        val next = synchronized(lock) {
            load(app)
            transform(_data.value).also { _data.value = it }
        }
        runCatching {
            saver.execute {
                runCatching { synchronized(lock) { write(app, _data.value) } }
            }
        }.onFailure {
            // The background thread couldn't take the job: save here instead.
            synchronized(lock) { write(app, _data.value) }
        }
        return next
    }

    /** One thread, so deferred saves run one after the other. */
    private val saver = Executors.newSingleThreadExecutor()

    /** Writes [data] to the file. Call with [lock] held. */
    private fun write(context: Context, data: ClockData) {
        val f = file(context)
        val out = runCatching { f.startWrite() }.getOrNull()
        if (out != null) {
            try {
                out.write(json.encodeToString(ClockData.serializer(), data).toByteArray(Charsets.UTF_8))
                f.finishWrite(out)
            } catch (e: Exception) {
                f.failWrite(out)
            }
        }
    }

    /** Turns the file's bytes into data; throws when they aren't a valid ClockCtrl file. */
    internal fun decode(bytes: ByteArray): ClockData = json.decodeFromString<ClockData>(String(bytes, Charsets.UTF_8))

    /**
     * Reads [base] (the clock.json behind the AtomicFile). A missing file is normal (first start) and gives empty
     * data. A file that is there but can't be read or decoded is never written over: it is renamed to
     * `clock.unreadable-<now>.json` next to it first, so the alarms in it can still be recovered by hand, and the
     * app carries on with empty data. Call with [lock] held (or from a test).
     */
    internal fun readOrSetAside(base: File, now: Long): ClockData {
        val f = AtomicFile(base)
        val bytes = try {
            f.readFully()
        } catch (e: FileNotFoundException) {
            return ClockData()
        } catch (e: Exception) {
            setAside(base, now)
            return ClockData()
        }
        return try {
            decode(bytes)
        } catch (e: Exception) {
            setAside(base, now)
            ClockData()
        }
    }

    /** Moves an unreadable clock.json out of the way. Returns the new file, or null when there was nothing to move or it failed. */
    internal fun setAside(base: File, now: Long): File? {
        if (!base.exists()) return null
        val target = File(base.parentFile, "clock.unreadable-$now.json")
        return if (runCatching { base.renameTo(target) }.getOrDefault(false)) target else null
    }

    fun newId(context: Context): Int {
        var id = 0
        update(context) { d -> id = d.nextId; d.copy(nextId = d.nextId + 1) }
        return id
    }
}

/** Days in the order the week starts on (Monday in most of Europe, Sunday in the US). */
fun weekDays(firstDay: DayOfWeek): List<DayOfWeek> = (0 until 7).map { firstDay.plus(it.toLong()) }

fun zoneNow(zone: ZoneId): ZonedDateTime = ZonedDateTime.now(zone)
