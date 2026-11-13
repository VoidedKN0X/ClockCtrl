package com.vknxworks.clockctrl

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vknxworks.clockctrl.alarm.RingService
import com.vknxworks.clockctrl.alarm.Scheduler
import com.vknxworks.clockctrl.alarm.Timers
import com.vknxworks.clockctrl.data.Alarm
import com.vknxworks.clockctrl.data.ClockStore
import java.util.Calendar

/**
 * "Set an alarm for 7:00", "set a timer for 10 minutes", from other apps and voice assistants (Android's standard
 * AlarmClock requests). Only apps with the SET_ALARM permission can send these.
 */
class ApiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { handle(intent) }
        finish()
    }

    private fun handle(intent: Intent) {
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> {
                if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
                    open(MainActivity.ACTION_SHOW_ALARMS)
                    return
                }
                val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0).coerceIn(0, 23)
                val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59)
                val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty().take(60)
                // Calendar days (Sunday = 1) to ISO days (Monday = 1).
                @Suppress("DEPRECATION")
                val days = (intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS) ?: arrayListOf())
                    .mapNotNull { d -> if (d in Calendar.SUNDAY..Calendar.SATURDAY) (if (d == Calendar.SUNDAY) 7 else d - 1) else null }.toSet()
                val vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true)
                val ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)?.let { if (it == AlarmClock.VALUE_RINGTONE_SILENT) "" else safeSound(it) }
                val data = ClockStore.load(this)
                // The same alarm already exists: switch it on instead of making a copy.
                val existing = data.alarms.firstOrNull { it.hour == hour && it.minute == minute && it.days == days && it.label == label }
                val alarm = existing?.copy(enabled = true, skipUntil = 0L)
                    ?: Alarm(ClockStore.newId(this), hour, minute, days, true, label, ringtone, vibrate)
                Scheduler.save(this, alarm)
                val time = DateFormat.getTimeFormat(this).format(
                    Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.time,
                )
                Toast.makeText(this, getString(R.string.alarm_set_for, time), Toast.LENGTH_SHORT).show()
                if (!skipUi) open(MainActivity.ACTION_SHOW_ALARMS)
            }
            AlarmClock.ACTION_SET_TIMER -> {
                val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1)
                if (seconds !in 1..86_400) {
                    open(MainActivity.ACTION_SHOW_TIMERS)
                    return
                }
                Timers.add(this, seconds * 1000L, intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty())
                Toast.makeText(this, getString(R.string.timer_started), Toast.LENGTH_SHORT).show()
                if (!skipUi) open(MainActivity.ACTION_SHOW_TIMERS)
            }
            AlarmClock.ACTION_DISMISS_ALARM -> RingService.ringing.value?.takeIf { !it.isTimer }?.let { RingService.send(this, RingService.ACTION_DISMISS, it.id) }
            AlarmClock.ACTION_SNOOZE_ALARM -> RingService.ringing.value?.takeIf { !it.isTimer }?.let { RingService.send(this, RingService.ACTION_SNOOZE, it.id) }
        }
    }

    /**
     * A sound another app asks for: only a shared sound (content:// or a sound built into an app). Anything else
     * (a file path, a web address, a malformed value) falls back to the phone's alarm sound, so another app can't
     * make ClockCtrl open its own files or leave the alarm without a sound it can play.
     */
    private fun safeSound(value: String): String? {
        val uri = runCatching { android.net.Uri.parse(value) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "content", "android.resource" -> value.take(2048)
            else -> null
        }
    }

    private fun open(action: String) {
        runCatching { startActivity(Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
