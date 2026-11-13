package com.vknxworks.clockctrl.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.Stopwatch

/** An alarm or timer is due: the ring service takes over. Not exported; only ClockCtrl's own alarms reach it. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        when (intent.action) {
            ACTION_RING -> RingService.start(context, RingService.ACTION_ALARM, id)
            ACTION_TIMER -> RingService.start(context, RingService.ACTION_TIMER, id)
            ACTION_SUNRISE -> Sunrise.begin(context, id)
        }
    }

    companion object {
        const val ACTION_RING = "com.vknxworks.clockctrl.RING"
        const val ACTION_SUNRISE = "com.vknxworks.clockctrl.SUNRISE"
        const val ACTION_TIMER = "com.vknxworks.clockctrl.TIMER_DONE"
        const val EXTRA_ID = "id"
    }
}

/**
 * Sets every alarm again after a restart (also before the first unlock), an app update, a change of time or time
 * zone, or when exact alarms are allowed again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> {
                val pending = goAsync()
                Thread {
                    try {
                        Scheduler.rescheduleAll(context)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}

/** The buttons on timer, stopwatch and alarm notifications. Not exported. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, -1)
        when (intent.action) {
            TIMER_PAUSE -> Timers.pause(context, id)
            TIMER_RESUME -> Timers.resume(context, id)
            TIMER_ADD_MINUTE -> Timers.addMinute(context, id)
            TIMER_RESET -> Timers.reset(context, id)
            WATCH_PAUSE -> Watch.pause(context)
            WATCH_RESUME -> Watch.start(context)
            WATCH_LAP -> Watch.lap(context)
            WATCH_RESET -> Watch.reset(context)
            ALARM_SKIP -> Alarms.skipNext(context, id)
            SNOOZE_CANCEL -> {
                Scheduler.cancelSnooze(context, id)
                Notifications.cancelSnoozed(context, id)
            }
        }
    }

    companion object {
        const val TIMER_PAUSE = "com.vknxworks.clockctrl.TIMER_PAUSE"
        const val TIMER_RESUME = "com.vknxworks.clockctrl.TIMER_RESUME"
        const val TIMER_ADD_MINUTE = "com.vknxworks.clockctrl.TIMER_ADD_MINUTE"
        const val TIMER_RESET = "com.vknxworks.clockctrl.TIMER_RESET"
        const val WATCH_PAUSE = "com.vknxworks.clockctrl.WATCH_PAUSE"
        const val WATCH_RESUME = "com.vknxworks.clockctrl.WATCH_RESUME"
        const val WATCH_LAP = "com.vknxworks.clockctrl.WATCH_LAP"
        const val WATCH_RESET = "com.vknxworks.clockctrl.WATCH_RESET"
        const val ALARM_SKIP = "com.vknxworks.clockctrl.ALARM_SKIP"
        const val SNOOZE_CANCEL = "com.vknxworks.clockctrl.SNOOZE_CANCEL"
    }
}

/** Alarm actions that keep the schedule right. */
object Alarms {
    /** Skip the next ring of an alarm (a one-time alarm switches off). */
    fun skipNext(context: Context, id: Int) {
        val a = ClockStore.load(context).alarms.firstOrNull { it.id == id } ?: return
        if (!a.repeating) {
            Scheduler.save(context, a.copy(enabled = false, date = null))
            return
        }
        val next = a.next() ?: return
        Scheduler.save(context, a.copy(skipUntil = next.toInstant().toEpochMilli() + 60_000L))
    }
}

/** Timer actions, used by the app and the notification buttons alike. */
object Timers {
    fun add(context: Context, lengthMs: Long, label: String, start: Boolean = true): Int {
        val id = ClockStore.newId(context)
        val now = System.currentTimeMillis()
        val t = com.vknxworks.clockctrl.data.Timer(id, label.take(60), lengthMs, lengthMs, if (start) now + lengthMs else 0L)
        ClockStore.update(context) { d -> d.copy(timers = d.timers + t) }
        Scheduler.scheduleTimer(context, t)
        Notifications.updateTimers(context, ClockStore.data.value)
        return id
    }

    private fun change(context: Context, id: Int, f: (com.vknxworks.clockctrl.data.Timer) -> com.vknxworks.clockctrl.data.Timer) {
        val d = ClockStore.update(context) { d -> d.copy(timers = d.timers.map { if (it.id == id) f(it) else it }) }
        d.timers.firstOrNull { it.id == id }?.let { Scheduler.scheduleTimer(context, it) }
        Notifications.updateTimers(context, d)
    }

    fun pause(context: Context, id: Int) = change(context, id) { t -> if (t.running) t.copy(remainingMs = t.left(), endAt = 0L) else t }
    fun resume(context: Context, id: Int) = change(context, id) { t ->
        if (t.running || t.remainingMs <= 0) t else t.copy(endAt = System.currentTimeMillis() + t.remainingMs)
    }
    fun addMinute(context: Context, id: Int) {
        val t = ClockStore.load(context).timers.firstOrNull { it.id == id } ?: return
        if (t.ringing) {
            // +1 minute on a ringing timer: stop the sound and run one more minute.
            change(context, id) { it.copy(ringing = false, remainingMs = 60_000L, endAt = System.currentTimeMillis() + 60_000L) }
            RingService.stopTimer(context, id)
        } else {
            change(context, id) { x ->
                if (x.running) x.copy(endAt = x.endAt + 60_000L, lengthMs = x.lengthMs + 60_000L)
                else x.copy(remainingMs = x.remainingMs + 60_000L, lengthMs = x.lengthMs + 60_000L)
            }
        }
    }
    fun reset(context: Context, id: Int) {
        val wasRinging = ClockStore.load(context).timers.firstOrNull { it.id == id }?.ringing == true
        change(context, id) { it.copy(remainingMs = it.lengthMs, endAt = 0L, ringing = false) }
        if (wasRinging) RingService.stopTimer(context, id)
    }
    fun delete(context: Context, id: Int) {
        val wasRinging = ClockStore.load(context).timers.firstOrNull { it.id == id }?.ringing == true
        val d = ClockStore.update(context) { d -> d.copy(timers = d.timers.filter { it.id != id }) }
        Scheduler.scheduleTimer(context, com.vknxworks.clockctrl.data.Timer(id, "", 0, 0))
        if (wasRinging) RingService.stopTimer(context, id)
        Notifications.cancelTimer(context, id)
        Notifications.updateTimers(context, d)
    }
    fun rename(context: Context, id: Int, label: String) = change(context, id) { it.copy(label = label.take(60)) }
    fun ringing(context: Context, id: Int) = change(context, id) { it.copy(ringing = true, remainingMs = 0L, endAt = 0L) }
}

/** Stopwatch actions. */
object Watch {
    private fun set(context: Context, f: (Stopwatch) -> Stopwatch) {
        val d = ClockStore.update(context) { d -> d.copy(stopwatch = f(d.stopwatch)) }
        Notifications.updateStopwatch(context, d.stopwatch)
    }

    fun start(context: Context) = set(context) { if (it.running) it else it.copy(runningSince = System.currentTimeMillis()) }
    fun pause(context: Context) = set(context) { if (!it.running) it else Stopwatch(0L, it.elapsed(), it.laps) }
    fun lap(context: Context) = set(context) { if (!it.running || it.laps.size >= 999) it else it.copy(laps = it.laps + it.elapsed()) }
    fun reset(context: Context) = set(context) { Stopwatch() }
}
