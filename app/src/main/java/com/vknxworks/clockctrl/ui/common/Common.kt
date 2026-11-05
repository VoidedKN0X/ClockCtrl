package com.vknxworks.clockctrl.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.data.settings.WeekStart
import com.vknxworks.clockctrl.ui.lock.AppLock
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** The current time, updated every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - (now % periodMs).coerceAtMost(periodMs - 1))
        }
    }
    return now
}

fun is24h(context: Context) = DateFormat.is24HourFormat(context)

/** "07:30" (24 h) or "7:30" plus "AM" (12 h). */
fun clockParts(context: Context, hour: Int, minute: Int): Pair<String, String?> =
    if (is24h(context)) "%02d:%02d".format(hour, minute) to null
    else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        "%d:%02d".format(h, minute) to (if (hour < 12) "AM" else "PM")
    }

fun firstDay(start: WeekStart): DayOfWeek = when (start) {
    WeekStart.PHONE -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
    WeekStart.MONDAY -> DayOfWeek.MONDAY
    WeekStart.SATURDAY -> DayOfWeek.SATURDAY
    WeekStart.SUNDAY -> DayOfWeek.SUNDAY
}

fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())
fun dayNarrow(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()).ifBlank { dayShort(d).take(1) }

/** "Every day", "Weekdays", "Mon, Wed, Fri" or "Once". */
fun repeatText(context: Context, days: Set<Int>, start: WeekStart): String = when {
    days.isEmpty() -> context.getString(R.string.repeat_once)
    days.size == 7 -> context.getString(R.string.repeat_daily)
    days == setOf(1, 2, 3, 4, 5) -> context.getString(R.string.repeat_weekdays)
    days == setOf(6, 7) -> context.getString(R.string.repeat_weekends)
    else -> com.vknxworks.clockctrl.data.weekDays(firstDay(start)).filter { it.value in days }.joinToString(", ") { dayShort(it) }
}

/** "in 7 h 20 min" */
fun untilText(context: Context, millis: Long): String {
    val d = Duration.ofMillis(millis.coerceAtLeast(0) + 59_999)
    val days = d.toDays()
    val hours = d.toHours() % 24
    val minutes = d.toMinutes() % 60
    return when {
        days > 0 -> context.getString(R.string.in_days_hours, days, hours)
        hours > 0 -> context.getString(R.string.in_hours_minutes, hours, minutes)
        else -> context.getString(R.string.in_minutes, minutes)
    }
}

/** The name of a sound: null is the phone's default alarm sound, "" is silent. */
fun ringtoneName(context: Context, uri: String?): String = when {
    uri == null -> context.getString(R.string.sound_default)
    uri.isEmpty() -> context.getString(R.string.sound_silent)
    else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull() ?: context.getString(R.string.sound_custom)
}

/** Android's own sound picker for alarm sounds. [onPick] gets null (default), "" (silent) or a sound. */
@Composable
fun rememberSoundPicker(onPick: (String?) -> Unit): (String?) -> Unit {
    val launcher: ManagedActivityResultLauncher<Intent, ActivityResult> =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val picked: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            onPick(
                when (picked) {
                    null -> ""
                    default -> null
                    else -> picked.toString()
                },
            )
        }
    return { current ->
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, default)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                when (current) {
                    null -> default
                    "" -> null
                    else -> Uri.parse(current)
                },
            )
        AppLock.expectReturn()
        runCatching { launcher.launch(i) }
    }
}
