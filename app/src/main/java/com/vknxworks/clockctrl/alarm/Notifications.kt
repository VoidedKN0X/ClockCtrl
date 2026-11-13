package com.vknxworks.clockctrl.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import com.vknxworks.clockctrl.MainActivity
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.data.ClockData
import com.vknxworks.clockctrl.data.Stopwatch

/** ClockCtrl's notifications. Everything is local; nothing is sent anywhere. */
object Notifications {
    const val ID_RING = 1
    private const val ID_STOPWATCH = 2
    private const val ID_MISSED = 3
    private const val ID_TIMER_BASE = 10_000
    private const val ID_SNOOZE_BASE = 20_000
    private const val ID_SUNRISE_BASE = 30_000

    private const val CH_RING = "ringing"
    private const val CH_SUNRISE = "sunrise"
    private const val CH_SUNRISE_QUIET = "sunrise_quiet"
    private const val CH_TIMERS = "timers"
    private const val CH_STOPWATCH = "stopwatch"
    private const val CH_MISSED = "missed_alarms"
    private const val CH_SNOOZE = "snoozed"

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_RING, context.getString(R.string.ch_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.ch_ringing_desc)
            // The sound and vibration come from ClockCtrl itself, on the alarm volume.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        })
        // Sunrise alarms: the same Do Not Disturb terms as a ringing alarm, but silent (the sound starts at the ring).
        nm.createNotificationChannel(NotificationChannel(CH_SUNRISE, context.getString(R.string.ch_sunrise), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.ch_sunrise_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_SUNRISE_QUIET, context.getString(R.string.ch_sunrise_quiet), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.ch_sunrise_quiet_desc)
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_TIMERS, context.getString(R.string.ch_timers), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.ch_timers_desc)
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_STOPWATCH, context.getString(R.string.ch_stopwatch), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.ch_stopwatch_desc)
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_SNOOZE, context.getString(R.string.ch_snoozed), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.ch_snoozed_desc)
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_MISSED, context.getString(R.string.ch_missed), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.ch_missed_desc)
        })
    }

    private fun open(context: Context, action: String, req: Int): PendingIntent = PendingIntent.getActivity(
        context, req,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ringAction(context: Context, action: String, req: Int, id: Int = -1): PendingIntent = PendingIntent.getService(
        context, req,
        Intent(context, RingService::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, action: String, id: Int, req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req,
        Intent(context, ActionReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun act(context: Context, label: Int, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_alarm), context.getString(label), pi).build()

    private fun time(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(java.util.Date(millis))

    fun ringing(context: Context, r: Ringing): Notification {
        channels(context)
        val screen = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CH_RING)
            .setSmallIcon(if (r.isTimer) R.drawable.ic_stat_timer else R.drawable.ic_stat_alarm)
            .setContentTitle(r.label.ifBlank { context.getString(if (r.isTimer) R.string.timer_done else R.string.alarm) })
            .setContentText(if (r.isTimer) context.getString(R.string.timer_done) else time(context, r.startedAt))
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (r.isTimer) {
            b.addAction(act(context, R.string.stop, ringAction(context, RingService.ACTION_STOP_TIMER, 2, r.id)))
            b.addAction(act(context, R.string.plus_minute, broadcast(context, ActionReceiver.TIMER_ADD_MINUTE, r.id, 3)))
        } else {
            b.addAction(act(context, R.string.snooze, ringAction(context, RingService.ACTION_SNOOZE, 4)))
            // With a dismiss sum, dismissing happens on the alarm screen only (tapping the notification opens it).
            if (r.challenge <= 0) b.addAction(act(context, R.string.dismiss, ringAction(context, RingService.ACTION_DISMISS, 5)))
        }
        return b.build()
    }

    /**
     * A sunrise is starting. [takeOver]: a full-screen notification that opens the sunrise page over the lock screen
     * (on the same Do Not Disturb terms as a ringing alarm); otherwise a quiet notification, because the phone is
     * in use. Tapping either opens the page.
     */
    fun sunrise(context: Context, alarm: com.vknxworks.clockctrl.data.Alarm, startAt: Long, ringAt: Long, takeOver: Boolean) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val page = PendingIntent.getActivity(
            context, 7_000_000 + alarm.id,
            Intent(context, SunriseActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_ID, alarm.id)
                .putExtra(SunriseActivity.EXTRA_START, startAt)
                .putExtra(SunriseActivity.EXTRA_RING, ringAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, if (takeOver) CH_SUNRISE else CH_SUNRISE_QUIET)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.label.ifBlank { context.getString(R.string.sunrise_title) })
            .setContentText(context.getString(R.string.sunrise_until, time(context, ringAt)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(page)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setTimeoutAfter((ringAt - System.currentTimeMillis()).coerceAtLeast(0) + 60_000L)
        if (takeOver) b.setFullScreenIntent(page, true)
        // Skipping from the notification would get round the dismiss sum; with one, skip on the page.
        if (alarm.challenge <= 0) {
            b.addAction(act(context, R.string.sunrise_skip, broadcast(context, ActionReceiver.ALARM_SKIP, alarm.id, 8_000_000 + alarm.id)))
        }
        runCatching { nm.notify(ID_SUNRISE_BASE + alarm.id, b.build()) }
    }

    fun cancelSunrise(context: Context, alarmId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_SUNRISE_BASE + alarmId)
    }

    /** One notification per running or paused timer, counting down by itself. */
    fun updateTimers(context: Context, data: ClockData) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        for (t in data.timers) {
            val id = ID_TIMER_BASE + t.id
            if (t.ringing || t.reset) {
                nm.cancel(id)
                continue
            }
            val b = Notification.Builder(context, CH_TIMERS)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(t.label.ifBlank { context.getString(R.string.timer) })
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(open(context, MainActivity.ACTION_SHOW_TIMERS, 100 + t.id))
                .setOngoing(t.running)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
            if (t.running) {
                b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(t.endAt).setShowWhen(true)
                    .setContentText(context.getString(R.string.timer_ends, time(context, t.endAt)))
                    .addAction(act(context, R.string.pause, broadcast(context, ActionReceiver.TIMER_PAUSE, t.id, 1000 + t.id)))
                    .addAction(act(context, R.string.plus_minute, broadcast(context, ActionReceiver.TIMER_ADD_MINUTE, t.id, 2000 + t.id)))
            } else {
                b.setShowWhen(false).setContentText(context.getString(R.string.timer_paused, formatMs(t.remainingMs)))
                    .addAction(act(context, R.string.resume, broadcast(context, ActionReceiver.TIMER_RESUME, t.id, 3000 + t.id)))
                    .addAction(act(context, R.string.reset, broadcast(context, ActionReceiver.TIMER_RESET, t.id, 4000 + t.id)))
            }
            runCatching { nm.notify(id, b.build()) }
        }
        // Timers that were deleted
        nm.activeNotifications.filter { it.id in ID_TIMER_BASE until ID_SNOOZE_BASE && data.timers.none { t -> ID_TIMER_BASE + t.id == it.id } }
            .forEach { nm.cancel(it.id) }
    }

    fun cancelTimer(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_TIMER_BASE + id)
    }

    fun updateStopwatch(context: Context, sw: Stopwatch) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!sw.running && sw.accumulatedMs == 0L) {
            nm.cancel(ID_STOPWATCH)
            return
        }
        val now = System.currentTimeMillis()
        val b = Notification.Builder(context, CH_STOPWATCH)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.stopwatch))
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(open(context, MainActivity.ACTION_SHOW_STOPWATCH, 200))
            .setOngoing(sw.running)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (sw.running) {
            b.setUsesChronometer(true).setWhen(now - sw.elapsed(now)).setShowWhen(true)
                .setContentText(if (sw.laps.isNotEmpty()) context.getString(R.string.lap_n, sw.laps.size + 1) else null)
                .addAction(act(context, R.string.pause, broadcast(context, ActionReceiver.WATCH_PAUSE, 0, 201)))
                .addAction(act(context, R.string.lap, broadcast(context, ActionReceiver.WATCH_LAP, 0, 202)))
        } else {
            b.setShowWhen(false).setContentText(context.getString(R.string.paused_at, formatMs(sw.accumulatedMs, hundredths = true)))
                .addAction(act(context, R.string.resume, broadcast(context, ActionReceiver.WATCH_RESUME, 0, 203)))
                .addAction(act(context, R.string.reset, broadcast(context, ActionReceiver.WATCH_RESET, 0, 204)))
        }
        runCatching { nm.notify(ID_STOPWATCH, b.build()) }
    }

    fun snoozed(context: Context, alarmId: Int, label: String, until: Long, challenge: Int = 0) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val b = Notification.Builder(context, CH_SNOOZE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label.ifBlank { context.getString(R.string.alarm) })
            .setContentText(context.getString(R.string.snoozed_until, time(context, until)))
            .setContentIntent(open(context, MainActivity.ACTION_SHOW_ALARMS, 300))
            .setTimeoutAfter((until - System.currentTimeMillis()).coerceAtLeast(1000))
        // An alarm with a dismiss sum is dismissed by solving it when it rings again, not from here.
        if (challenge <= 0) b.addAction(act(context, R.string.dismiss, broadcast(context, ActionReceiver.SNOOZE_CANCEL, alarmId, 5000 + alarmId)))
        runCatching { nm.notify(ID_SNOOZE_BASE + alarmId, b.build()) }
    }

    fun cancelSnoozed(context: Context, alarmId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_SNOOZE_BASE + alarmId)
    }

    fun missed(context: Context, label: String) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val n = Notification.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.missed_alarm))
            .setContentText(listOf(label, time(context, System.currentTimeMillis())).filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(open(context, MainActivity.ACTION_SHOW_ALARMS, 301))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID_MISSED, n) }
    }
}

/** 1:05:09, 4:32 or (with hundredths) 4:32.07 */
fun formatMs(ms: Long, hundredths: Boolean = false): String {
    val total = ms.coerceAtLeast(0)
    val s = total / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    return if (hundredths) base + ".%02d".format((total % 1000) / 10) else base
}
