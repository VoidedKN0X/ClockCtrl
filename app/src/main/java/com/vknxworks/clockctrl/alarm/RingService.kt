package com.vknxworks.clockctrl.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vknxworks.clockctrl.data.ClockStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What's ringing right now, for the alarm screen. */
data class Ringing(val isTimer: Boolean, val id: Int, val label: String, val startedAt: Long)

/**
 * Rings an alarm or a finished timer: sound on the alarm volume (growing louder if you chose that), vibration, and a
 * full-screen notification that shows the alarm screen over the lock screen. Works before the phone is unlocked
 * for the first time after a restart.
 */
class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var focus: AudioFocusRequest? = null
    private var volume = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(AlarmReceiver.EXTRA_ID, -1) ?: -1
        val current = _ringing.value
        // A foreground notification first, always (Android requires it right away).
        foreground(current ?: Ringing(intent?.action == ACTION_TIMER, id, "", System.currentTimeMillis()))
        when (intent?.action) {
            ACTION_ALARM -> {
                val alarm = ClockStore.load(this).alarms.firstOrNull { it.id == id }
                if (alarm == null) { finishIfIdle(); return START_NOT_STICKY }
                // Set the next ring straight away, so a repeating alarm keeps working even if this one isn't stopped.
                Scheduler.afterRing(this, id)
                if (current != null && !current.isTimer) Notifications.missed(this, current.label)
                ring(Ringing(false, id, alarm.label, System.currentTimeMillis()), alarm.ringtone, alarm.vibrate)
            }
            ACTION_TIMER -> {
                val timer = ClockStore.load(this).timers.firstOrNull { it.id == id }
                if (timer == null) { finishIfIdle(); return START_NOT_STICKY }
                Timers.ringing(this, id)
                val d = ClockStore.data.value
                ring(Ringing(true, id, timer.label, System.currentTimeMillis()), d.timerRingtone, d.timerVibrate)
            }
            ACTION_SNOOZE -> {
                val r = _ringing.value
                if (r != null && !r.isTimer) {
                    val minutes = ClockStore.load(this).snoozeMinutes
                    Scheduler.snooze(this, r.id, minutes)
                    Notifications.snoozed(this, r.id, r.label, System.currentTimeMillis() + minutes * 60_000L)
                }
                stopRinging()
            }
            ACTION_DISMISS -> stopRinging()
            ACTION_STOP_TIMER -> if (_ringing.value?.let { it.isTimer && (id < 0 || it.id == id) } == true) stopRinging()
            else -> finishIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun foreground(r: Ringing) {
        val n = Notifications.ringing(this, r)
        runCatching {
            startForeground(Notifications.ID_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }.onFailure { runCatching { startForeground(Notifications.ID_RING, n) } }
    }

    private fun ring(r: Ringing, ringtone: String?, vibrate: Boolean) {
        stopSound()
        _ringing.value = r
        foreground(r)
        // The alarm screen: over the lock screen through the full-screen notification, or straight away when allowed.
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION))
        }
        val data = ClockStore.data.value
        playSound(ringtone, if (r.isTimer) 0 else data.gradualSeconds)
        if (vibrate) startVibration()
        handler.removeCallbacksAndMessages(TIMEOUT)
        handler.postDelayed({
            val now = _ringing.value
            if (now != null && now.startedAt == r.startedAt) {
                if (!now.isTimer) Notifications.missed(this, now.label)
                stopRinging()
            }
        }, TIMEOUT, data.ringMinutes.coerceIn(1, 60) * 60_000L)
    }

    private fun audioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun playSound(ringtone: String?, gradualSeconds: Int) {
        if (ringtone == "") return // silent
        val am = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(audioAttributes()).build()
        focus?.let { runCatching { am?.requestAudioFocus(it) } }

        val candidates = listOfNotNull(
            ringtone?.let { Uri.parse(it) },
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val mp = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(audioAttributes())
                    setDataSource(this@RingService, uri)
                    isLooping = true
                    prepare()
                }
            }.getOrNull() ?: continue
            player = mp
            volume = if (gradualSeconds > 0) 0.05f else 1f
            mp.setVolume(volume, volume)
            mp.start()
            if (gradualSeconds > 0) {
                val step = 1f / gradualSeconds
                val ramp = object : Runnable {
                    override fun run() {
                        val p = player ?: return
                        volume = (volume + step).coerceAtMost(1f)
                        runCatching { p.setVolume(volume, volume) }
                        if (volume < 1f) handler.postDelayed(this, 1000)
                    }
                }
                handler.postDelayed(ramp, 1000)
            }
            return
        }
        // No sound file could be opened (for example right after a restart, before media storage is up):
        // a built-in beep on the alarm volume, so an alarm is never silent.
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
        val beep = object : Runnable {
            override fun run() {
                val t = tone ?: return
                runCatching { t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800) }
                handler.postDelayed(this, 1500)
            }
        }
        handler.post(beep)
    }

    private fun startVibration() {
        val v = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        runCatching { v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)) }
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        tone?.let { runCatching { it.stopTone() }; runCatching { it.release() } }
        tone = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
        focus?.let { f -> runCatching { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(f) } }
        focus = null
    }

    private fun stopRinging() {
        val r = _ringing.value
        stopSound()
        _ringing.value = null
        if (r != null && r.isTimer) {
            // A finished timer that was stopped goes back to its starting length.
            ClockStore.update(this) { d -> d.copy(timers = d.timers.map { if (it.id == r.id && it.ringing) it.copy(ringing = false, remainingMs = it.lengthMs, endAt = 0L) else it }) }
            Notifications.updateTimers(this, ClockStore.data.value)
        }
        finishIfIdle()
    }

    private fun finishIfIdle() {
        if (_ringing.value == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopSound()
        _ringing.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_ALARM = "com.vknxworks.clockctrl.ring.ALARM"
        const val ACTION_TIMER = "com.vknxworks.clockctrl.ring.TIMER"
        const val ACTION_SNOOZE = "com.vknxworks.clockctrl.ring.SNOOZE"
        const val ACTION_DISMISS = "com.vknxworks.clockctrl.ring.DISMISS"
        const val ACTION_STOP_TIMER = "com.vknxworks.clockctrl.ring.STOP_TIMER"
        private val TIMEOUT = Any()

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing

        fun start(context: Context, action: String, id: Int) {
            val i = Intent(context, RingService::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, id)
            runCatching { context.startForegroundService(i) }
        }

        /** Snooze, dismiss or stop from the app (the service is running while something rings). */
        fun send(context: Context, action: String, id: Int = -1) {
            if (_ringing.value == null) return
            val i = Intent(context, RingService::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, id)
            runCatching { context.startService(i) }
        }

        fun stopTimer(context: Context, id: Int) = send(context, ACTION_STOP_TIMER, id)
    }
}
