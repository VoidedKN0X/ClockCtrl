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
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vknxworks.clockctrl.data.ClockStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What's ringing right now, for the alarm screen. */
data class Ringing(
    val isTimer: Boolean,
    val id: Int,
    val label: String,
    val startedAt: Long,
    /** Dismiss sum level (0: none). Snooze is never guarded. */
    val challenge: Int = 0,
)

/**
 * Rings an alarm or a finished timer: sound on the alarm volume (growing louder if you chose that), vibration, and a
 * full-screen notification that shows the alarm screen over the lock screen. Works before the phone is unlocked
 * for the first time after a restart.
 */
class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    /** The sound file that is being opened right now, if any; it becomes [player] once it is ready. */
    private var pending: MediaPlayer? = null

    /** Goes up every time the sound is stopped; answers that arrive for an earlier sound are ignored. Main thread only. */
    private var soundGen = 0
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var focus: AudioFocusRequest? = null
    private var volume = 1f

    /** Keeps the processor awake while something rings, so the sound, the fade-in and the time limit keep running with the screen off. */
    private var wakeLock: PowerManager.WakeLock? = null

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
                Sunrise.stop(this, id)
                if (current != null && !current.isTimer) Notifications.missed(this, current.label)
                // After a sunrise the sound always fades in, even with "Gradually louder" off.
                val gradual = ClockStore.data.value.gradualSeconds.let { if (alarm.sunriseMinutes > 0) maxOf(it, SUNRISE_FADE_SECONDS) else it }
                ring(Ringing(false, id, alarm.label, System.currentTimeMillis(), alarm.challenge), alarm.ringtone, alarm.vibrate, gradual)
            }
            ACTION_TIMER -> {
                val timer = ClockStore.load(this).timers.firstOrNull { it.id == id }
                if (timer == null) { finishIfIdle(); return START_NOT_STICKY }
                Timers.ringing(this, id)
                val d = ClockStore.data.value
                ring(Ringing(true, id, timer.label, System.currentTimeMillis()), d.timerRingtone, d.timerVibrate, 0)
            }
            ACTION_SNOOZE -> {
                val r = _ringing.value
                if (r != null && !r.isTimer) {
                    val minutes = ClockStore.load(this).snoozeMinutes
                    Scheduler.snooze(this, r.id, minutes)
                    Notifications.snoozed(this, r.id, r.label, System.currentTimeMillis() + minutes * 60_000L, r.challenge)
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

    private fun ring(r: Ringing, ringtone: String?, vibrate: Boolean, gradualSeconds: Int) {
        stopSound()
        _ringing.value = r
        foreground(r)
        // The alarm screen: over the lock screen through the full-screen notification, or straight away when allowed.
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION))
        }
        val data = ClockStore.data.value
        val ringMs = data.ringMinutes.coerceIn(1, 60) * 60_000L
        // Awake for as long as this can ring (plus a margin); released when the ringing stops.
        holdAwake(ringMs + WAKE_MARGIN_MS)
        playSound(ringtone, gradualSeconds)
        if (vibrate) startVibration()
        handler.removeCallbacksAndMessages(TIMEOUT)
        handler.postDelayed({
            val now = _ringing.value
            if (now != null && now.startedAt == r.startedAt) {
                if (!now.isTimer) Notifications.missed(this, now.label)
                stopRinging()
            }
        }, TIMEOUT, ringMs)
    }

    /** Takes the wake lock for [ms] at most (Android lets go of it by itself after that), replacing an earlier one. */
    private fun holdAwake(ms: Long) {
        val old = wakeLock
        wakeLock = runCatching {
            getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClockCtrl:ring")?.apply {
                setReferenceCounted(false)
                acquire(ms)
            }
        }.getOrNull()
        old?.let { runCatching { if (it.isHeld) it.release() } }
    }

    private fun releaseAwake() {
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
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
        // Opening a sound file can take a while (or, rarely, never finish), so it is prepared in the background and
        // this thread stays free for the alarm screen. If nothing is playing after PREPARE_WATCHDOG_MS, the built-in
        // beep starts and keeps going until a sound file is ready (or for the whole ring when none ever is).
        val gen = soundGen
        handler.postDelayed({ if (gen == soundGen && player == null) startBeep() }, PREPARE_WATCHDOG_MS)
        prepareNext(candidates, 0, gradualSeconds, gen)
    }

    /**
     * Tries the sound files in [candidates] from [index] on, one at a time and in the same order as always: the
     * first one that opens is played; when none opens, the built-in beep rings instead. [gen] is the value of
     * [soundGen] when this ring started: once the sound has been stopped (or another ring took over) it no longer
     * matches and nothing here does anything any more.
     */
    private fun prepareNext(candidates: List<Uri>, index: Int, gradualSeconds: Int, gen: Int) {
        if (gen != soundGen) return
        if (index >= candidates.size) {
            // No sound file could be opened (for example right after a restart, before media storage is up).
            if (player == null) startBeep()
            return
        }
        val mp = runCatching { MediaPlayer() }.getOrNull()
        if (mp == null) { prepareNext(candidates, index + 1, gradualSeconds, gen); return }
        // Only the first answer for this file counts: ready, failed, or an exception while setting it up.
        var settled = false
        val started = runCatching {
            mp.setAudioAttributes(audioAttributes())
            mp.setDataSource(this@RingService, candidates[index])
            mp.isLooping = true
            mp.setOnPreparedListener { ready ->
                if (!settled) {
                    settled = true
                    if (gen != soundGen || pending !== ready) {
                        // Stopped in the meantime; stopSound() has already let go of it.
                        runCatching { ready.release() }
                    } else {
                        pending = null
                        if (!startPlayer(ready, gradualSeconds)) prepareNext(candidates, index + 1, gradualSeconds, gen)
                    }
                }
            }
            mp.setOnErrorListener { failed, _, _ ->
                if (!settled) {
                    // This file can't be opened: on to the next one.
                    settled = true
                    runCatching { failed.release() }
                    if (gen == soundGen && pending === failed) {
                        pending = null
                        prepareNext(candidates, index + 1, gradualSeconds, gen)
                    }
                } else if (gen == soundGen && player === failed) {
                    // The sound broke while ringing: the built-in beep takes over, so the alarm doesn't go quiet.
                    player = null
                    runCatching { failed.release() }
                    startBeep()
                }
                true
            }
            pending = mp
            mp.prepareAsync()
        }.isSuccess
        if (!started && !settled) {
            settled = true
            if (pending === mp) pending = null
            runCatching { mp.release() }
            prepareNext(candidates, index + 1, gradualSeconds, gen)
        }
    }

    /** Starts a prepared sound (growing louder if asked) and ends the stand-in beep. False when it would not start. */
    private fun startPlayer(mp: MediaPlayer, gradualSeconds: Int): Boolean {
        volume = if (gradualSeconds > 0) 0.05f else 1f
        val ok = runCatching {
            mp.setVolume(volume, volume)
            mp.start()
        }.isSuccess
        if (!ok) {
            runCatching { mp.release() }
            return false
        }
        // The beep may have been bridging the wait.
        stopBeep()
        player = mp
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
        return true
    }

    /** The built-in beep on the alarm volume, so an alarm is never silent. Does nothing when it is already beeping. */
    private fun startBeep() {
        if (tone != null) return
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
        val beep = object : Runnable {
            override fun run() {
                val t = tone ?: return
                runCatching { t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800) }
                handler.postDelayed(this, BEEP, 1500)
            }
        }
        handler.postAtTime(beep, BEEP, SystemClock.uptimeMillis())
    }

    private fun stopBeep() {
        handler.removeCallbacksAndMessages(BEEP)
        tone?.let { runCatching { it.stopTone() }; runCatching { it.release() } }
        tone = null
    }

    private fun startVibration() {
        val v = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        runCatching { v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)) }
    }

    private fun stopSound() {
        // Anything still on its way for the sound that ends here (a file being opened, the watchdog) is void from now on.
        soundGen++
        handler.removeCallbacksAndMessages(null)
        pending?.let { p ->
            pending = null
            runCatching { p.setOnPreparedListener(null) }
            runCatching { p.setOnErrorListener(null) }
            // Letting go of a file that is stuck opening can itself hang, so that never happens on this thread.
            runCatching { Thread({ runCatching { p.release() } }, "ClockCtrl-ring-release").apply { isDaemon = true }.start() }
        }
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
        releaseAwake()
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
        releaseAwake()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ALARM = "com.vknxworks.clockctrl.ring.ALARM"
        const val ACTION_TIMER = "com.vknxworks.clockctrl.ring.TIMER"
        const val ACTION_SNOOZE = "com.vknxworks.clockctrl.ring.SNOOZE"
        const val ACTION_DISMISS = "com.vknxworks.clockctrl.ring.DISMISS"
        const val ACTION_STOP_TIMER = "com.vknxworks.clockctrl.ring.STOP_TIMER"
        private val TIMEOUT = Any()
        private val BEEP = Any()

        /** How long a sound file may take to open before the built-in beep starts in its place. */
        private const val PREPARE_WATCHDOG_MS = 2_000L
        private const val SUNRISE_FADE_SECONDS = 30

        /** Extra time the wake lock may outlast the ring time limit, so the limit itself always gets to run. */
        private const val WAKE_MARGIN_MS = 60_000L

        /** How long the phone is kept awake between an alarm going off and this service taking over. */
        private const val START_WAKE_MS = 10_000L

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing

        fun start(context: Context, action: String, id: Int) {
            val i = Intent(context, RingService::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ID, id)
            // Android only keeps the phone awake until the alarm broadcast returns; the service starts a moment
            // later. This short wake lock bridges that gap (it lets go by itself), then ring() takes its own.
            runCatching {
                context.getSystemService(PowerManager::class.java)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClockCtrl:ring-start")
                    ?.acquire(START_WAKE_MS)
            }
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
