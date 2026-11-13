package com.vknxworks.clockctrl.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.vknxworks.clockctrl.data.ClockStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Sunrise alarms: a pre-alarm a few minutes before the ring brightens the screen on a full-screen page (over the
 * lock screen, also before the first unlock). If the phone is being used at that moment, a quiet notification
 * shows instead. The ring itself is the normal alarm, set with Android's alarm-clock API as always.
 */
object Sunrise {
    /** Sunrises showing now: alarm id to the ring they lead up to (epoch ms). */
    private val shown = ConcurrentHashMap<Int, Long>()

    /** The pre-alarm fired: start the sunrise if the alarm still rings soon. Only touches [ClockStore]. */
    fun begin(context: Context, alarmId: Int) {
        val alarm = ClockStore.load(context).alarms.firstOrNull { it.id == alarmId } ?: return
        if (!alarm.enabled || alarm.sunriseMinutes <= 0) return
        val ringAt = alarm.next()?.toInstant()?.toEpochMilli() ?: return
        val now = System.currentTimeMillis()
        if (!SunriseMath.shouldBegin(ringAt, alarm.sunriseMinutes, now)) return
        if (RingService.ringing.value?.let { !it.isTimer && it.id == alarmId } == true) return
        shown[alarmId] = ringAt
        val startAt = SunriseMath.startAtMillis(ringAt, alarm.sunriseMinutes) ?: return
        Notifications.sunrise(context, alarm, startAt, ringAt, takeOver = !inUse(context))
    }

    /** Someone is using the phone: the screen is on and unlocked. */
    fun inUse(context: Context): Boolean {
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive == true
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false
        return interactive && !locked
    }

    /**
     * Ends a sunrise (its notification; the page closes by itself once the alarm no longer leads to that ring).
     * With [keepRingAt], a sunrise leading to that same ring is left alone.
     */
    fun stop(context: Context, alarmId: Int, keepRingAt: Long? = null) {
        val at = shown[alarmId]
        if (keepRingAt != null && (at == null || at == keepRingAt)) return
        shown.remove(alarmId)
        Notifications.cancelSunrise(context, alarmId)
    }

    /** Whether the sunrise page for [alarmId] leading to [ringAt] should still show. */
    fun stillDue(context: Context, alarmId: Int, ringAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        val alarm = ClockStore.load(context).alarms.firstOrNull { it.id == alarmId } ?: return false
        if (!alarm.enabled || alarm.sunriseMinutes <= 0) return false
        return alarm.next()?.toInstant()?.toEpochMilli() == ringAt && now < ringAt
    }
}
