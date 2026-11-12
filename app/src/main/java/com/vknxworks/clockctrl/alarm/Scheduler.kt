package com.vknxworks.clockctrl.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vknxworks.clockctrl.MainActivity
import com.vknxworks.clockctrl.data.Alarm
import com.vknxworks.clockctrl.data.ClockData
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.Timer
import java.time.ZonedDateTime

/**
 * Sets every alarm with Android's alarm-clock API (shown in the status bar and on the lock screen, rings on time even
 * in battery saving), and timers as exact alarms. Called after every change, after a restart and when the time or
 * time zone changes.
 */
object Scheduler {
    private const val REQ_SNOOZE = 1_000_000
    private const val REQ_TIMER = 2_000_000

    private fun am(context: Context) = context.getSystemService(AlarmManager::class.java)

    /** Exact alarms are allowed (Android 13+ grants this to alarm clock apps). */
    fun canSchedule(context: Context): Boolean = runCatching { am(context)?.canScheduleExactAlarms() != false }.getOrDefault(true)

    private fun ringIntent(context: Context, action: String, id: Int, req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req,
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun showAlarms(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun rescheduleAll(context: Context) {
        val data = ClockStore.load(context)
        data.alarms.forEach { schedule(context, it) }
        data.timers.forEach { scheduleTimer(context, it) }
        Notifications.updateTimers(context, data)
        Notifications.updateStopwatch(context, data.stopwatch)
    }

    fun schedule(context: Context, alarm: Alarm) {
        val pi = ringIntent(context, AlarmReceiver.ACTION_RING, alarm.id, alarm.id)
        val manager = am(context) ?: return
        val next = if (alarm.enabled) alarm.next() else null
        if (next == null) {
            manager.cancel(pi)
            return
        }
        setClock(context, next.toInstant().toEpochMilli(), pi)
    }

    fun cancel(context: Context, alarmId: Int) {
        am(context)?.cancel(ringIntent(context, AlarmReceiver.ACTION_RING, alarmId, alarmId))
        am(context)?.cancel(ringIntent(context, AlarmReceiver.ACTION_RING, alarmId, REQ_SNOOZE + alarmId))
    }

    fun cancelSnooze(context: Context, alarmId: Int) {
        am(context)?.cancel(ringIntent(context, AlarmReceiver.ACTION_RING, alarmId, REQ_SNOOZE + alarmId))
    }

    fun snooze(context: Context, alarmId: Int, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setClock(context, at, ringIntent(context, AlarmReceiver.ACTION_RING, alarmId, REQ_SNOOZE + alarmId))
    }

    private fun setClock(context: Context, at: Long, pi: PendingIntent) {
        val manager = am(context) ?: return
        runCatching { manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showAlarms(context)), pi) }
            .onFailure {
                // Exact alarms turned off in Android's settings: the closest Android still allows.
                runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            }
    }

    fun scheduleTimer(context: Context, timer: Timer) {
        val pi = ringIntent(context, AlarmReceiver.ACTION_TIMER, timer.id, REQ_TIMER + timer.id)
        val manager = am(context) ?: return
        if (!timer.running) {
            manager.cancel(pi)
            return
        }
        runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pi) }
            .onFailure { runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pi) } }
    }

    /** Saves the alarm and sets it. */
    fun save(context: Context, alarm: Alarm) {
        ClockStore.update(context) { d ->
            val list = if (d.alarms.any { it.id == alarm.id }) d.alarms.map { if (it.id == alarm.id) alarm else it } else d.alarms + alarm
            d.copy(alarms = list.sortedWith(compareBy({ it.hour }, { it.minute })))
        }
        // A changed alarm replaces any snooze that was pending.
        am(context)?.cancel(ringIntent(context, AlarmReceiver.ACTION_RING, alarm.id, REQ_SNOOZE + alarm.id))
        schedule(context, alarm)
    }

    fun delete(context: Context, alarmId: Int) {
        cancel(context, alarmId)
        ClockStore.update(context) { d -> d.copy(alarms = d.alarms.filter { it.id != alarmId }) }
    }

    /** After an alarm rang: a one-time alarm switches off, a repeating one is set for its next day. */
    fun afterRing(context: Context, alarmId: Int) {
        val d = ClockStore.load(context)
        val a = d.alarms.firstOrNull { it.id == alarmId } ?: return
        val updated = if (a.repeating) a.copy(skipUntil = 0L) else a.copy(enabled = false, date = null)
        save(context, updated)
    }

    /** The next alarm that will ring, for the clock screen. */
    fun nextAlarm(data: ClockData): Pair<Alarm, ZonedDateTime>? =
        data.alarms.filter { it.enabled }.mapNotNull { a -> a.next()?.let { a to it } }.minByOrNull { it.second }
}
