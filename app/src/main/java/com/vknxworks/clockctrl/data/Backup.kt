package com.vknxworks.clockctrl.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One alarm in an alarms file: everything but its id and a pending "skip next". */
@Serializable
data class BackupAlarm(
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    /** Ringtone URI as it was on the phone that wrote the file; null is the default sound, "" silent. */
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    val date: String? = null,
    val sunriseMinutes: Int = 0,
    val challenge: Int = 0,
)

/** An alarms file: the alarms of one phone, to set them up again on this one or another. */
@Serializable
data class AlarmsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val alarms: List<BackupAlarm> = emptyList(),
) {
    companion object {
        const val FORMAT = "clockctrl-alarms"
        const val VERSION = 1
    }
}

/**
 * A settings file: the app settings and the alarm and timer settings, and the world clock's cities. Never the app
 * lock or its PIN. Every field is optional, so files from older or newer versions restore what they can.
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val weekStart: String? = null,
    val showSeconds: Boolean? = null,
    /** Restoring can only turn these on, never off. */
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    val snoozeMinutes: Int? = null,
    val ringMinutes: Int? = null,
    val gradualSeconds: Int? = null,
    val timerVibrate: Boolean? = null,
    val cities: List<String>? = null,
) {
    companion object {
        const val FORMAT = "clockctrl-settings"
        const val VERSION = 1
    }
}

/** Thrown for a file that isn't the expected kind of ClockCtrl file, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

object Backups {
    /** A settings or alarms file is a few kilobytes; anything far bigger isn't one. */
    const val MAX_BYTES = 512 * 1024

    /** The most alarms one file may hold. */
    const val MAX_ALARMS = 500

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun exportAlarms(alarms: List<Alarm>, appVersion: String, now: String): String =
        json.encodeToString(
            AlarmsBackup.serializer(),
            AlarmsBackup(
                appVersion = appVersion,
                createdAt = now,
                alarms = alarms.map {
                    BackupAlarm(it.hour, it.minute, it.days, it.enabled, it.label, it.ringtone, it.vibrate, it.date, it.sunriseMinutes, it.challenge)
                },
            ),
        )

    /** Reads and checks an alarms file; anything out of range makes it invalid rather than half-read. */
    fun parseAlarms(text: String): AlarmsBackup {
        val b = runCatching { json.decodeFromString(AlarmsBackup.serializer(), text) }.getOrNull()
            ?: throw BackupFormatException(newer = false)
        return check(b)
    }

    /** Checks a decoded alarms file (see [parseAlarms]). */
    fun check(b: AlarmsBackup): AlarmsBackup {
        if (b.format != AlarmsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (b.version > AlarmsBackup.VERSION) throw BackupFormatException(newer = true)
        if (b.alarms.size > MAX_ALARMS) throw BackupFormatException(newer = false)
        val ok = b.alarms.all { a ->
            a.hour in 0..23 && a.minute in 0..59 && a.days.all { it in 1..7 } &&
                a.sunriseMinutes in 0..60 && a.challenge in 0..3 && a.label.length <= 200 &&
                (a.date == null || runCatching { java.time.LocalDate.parse(a.date) }.isSuccess)
        }
        if (!ok) throw BackupFormatException(newer = false)
        return b
    }

    /**
     * The alarms of [backup] as new alarms with ids from [firstId] on. A one-time alarm on a date that has
     * passed is switched off, as it would be here. [ringtoneOk] says whether a ringtone URI from the file can be
     * played on this phone; one that can't becomes the phone's default alarm sound.
     */
    fun toAlarms(backup: AlarmsBackup, firstId: Int, today: java.time.LocalDate, ringtoneOk: (String) -> Boolean): List<Alarm> =
        backup.alarms.mapIndexed { i, a ->
            val past = a.date != null && a.days.isEmpty() && java.time.LocalDate.parse(a.date).isBefore(today)
            Alarm(
                id = firstId + i,
                hour = a.hour,
                minute = a.minute,
                days = a.days,
                enabled = a.enabled && !past,
                label = a.label.take(60),
                ringtone = a.ringtone?.let { r -> if (r.isEmpty() || ringtoneOk(r)) r else null },
                vibrate = a.vibrate,
                date = if (past) null else a.date,
                sunriseMinutes = a.sunriseMinutes,
                challenge = a.challenge,
            )
        }

    fun exportSettings(s: SettingsBackup): String = json.encodeToString(SettingsBackup.serializer(), s)

    fun parseSettings(text: String): SettingsBackup {
        val b = runCatching { json.decodeFromString(SettingsBackup.serializer(), text) }.getOrNull()
            ?: throw BackupFormatException(newer = false)
        if (b.format != SettingsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (b.version > SettingsBackup.VERSION) throw BackupFormatException(newer = true)
        return b
    }

    /** The alarm and timer settings and cities of [b] applied to [d]; values out of range are left as they are. */
    fun applyClockSettings(b: SettingsBackup, d: ClockData, validZone: (String) -> Boolean): ClockData = d.copy(
        snoozeMinutes = b.snoozeMinutes?.takeIf { it in 1..60 } ?: d.snoozeMinutes,
        ringMinutes = b.ringMinutes?.takeIf { it in 1..60 } ?: d.ringMinutes,
        gradualSeconds = b.gradualSeconds?.takeIf { it in 0..300 } ?: d.gradualSeconds,
        timerVibrate = b.timerVibrate ?: d.timerVibrate,
        cities = b.cities?.filter(validZone)?.distinct()?.take(100) ?: d.cities,
    )

    // ---------- Laps ----------

    /**
     * The stopwatch laps as CSV (comma-separated, CRLF), oldest first: lap number, the lap's own time and the
     * total at that lap, as h:mm:ss.cc. [header] are the three column names in the phone's language.
     */
    fun lapsCsv(splits: List<Long>, header: List<String>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { csvField(it) }).append("\r\n")
        splits.forEachIndexed { i, total ->
            val lap = total - (if (i == 0) 0L else splits[i - 1])
            sb.append(i + 1).append(',').append(duration(lap)).append(',').append(duration(total)).append("\r\n")
        }
        return sb.toString()
    }

    /** The laps as plain text lines for the clipboard: "Lap 1  0:12.34  0:12.34". [lapName] makes "Lap 1". */
    fun lapsText(splits: List<Long>, lapName: (Int) -> String): String =
        splits.mapIndexed { i, total ->
            val lap = total - (if (i == 0) 0L else splits[i - 1])
            "${lapName(i + 1)}\t${duration(lap)}\t${duration(total)}"
        }.joinToString("\n")

    /** 1:05:09.07 or 4:32.07. */
    fun duration(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val s = total / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val cs = (total % 1000) / 10
        return if (h > 0) "%d:%02d:%02d.%02d".format(java.util.Locale.ROOT, h, m, sec, cs)
        else "%d:%02d.%02d".format(java.util.Locale.ROOT, m, sec, cs)
    }

    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
