package com.vknxworks.clockctrl.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.ui.theme.ClockCtrlTheme
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * The sunrise before an alarm: a full-screen page over the lock screen that slowly turns from a deep warm colour to
 * bright white while the screen brightens. Silent; the alarm itself rings at its time and its sound fades in. Like the
 * ringing screen it works before the first unlock and is never behind the app lock, and it shows only the alarm.
 */
class SunriseActivity : ComponentActivity() {
    private var alarmId by mutableIntStateOf(-1)
    private var startAt by mutableLongStateOf(0L)
    private var ringAt by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        read(intent)
        if (alarmId < 0 || ringAt <= 0L) {
            finish()
            return
        }
        // Full screen: no status or navigation bar over the sunrise (a swipe shows them).
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val dark = isSystemInDarkTheme()
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            val ringing by RingService.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(alarmId, ringAt) {
                var lastBrightness = -1f
                while (true) {
                    now = System.currentTimeMillis()
                    val b = SunriseMath.brightness(SunriseMath.progress(now, startAt, ringAt))
                    if (kotlin.math.abs(b - lastBrightness) >= 0.004f) {
                        lastBrightness = b
                        window.attributes = window.attributes.apply { screenBrightness = b }
                    }
                    when {
                        // Before the ring: the alarm was switched off, skipped, changed or deleted.
                        now < ringAt && !Sunrise.stillDue(this@SunriseActivity, alarmId, ringAt, now) -> { finish(); return@LaunchedEffect }
                        // The ring never came (exact alarms off, for example): don't stay on forever.
                        now > ringAt + LINGER_MS -> { finish(); return@LaunchedEffect }
                    }
                    delay(TICK_MS)
                }
            }
            LaunchedEffect(ringing) {
                val r = ringing
                if (r != null && !r.isTimer && r.id == alarmId) {
                    // The alarm rings: hand over to the ringing screen.
                    runCatching {
                        startActivity(Intent(this@SunriseActivity, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    finish()
                }
            }
            ClockCtrlTheme(dark) {
                SunriseScreen(alarmId, startAt, ringAt, now)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(i: Intent?) {
        val id = i?.getIntExtra(AlarmReceiver.EXTRA_ID, -1) ?: -1
        val ring = i?.getLongExtra(EXTRA_RING, 0L) ?: 0L
        if (id < 0 || ring <= 0L) return
        alarmId = id
        ringAt = ring
        startAt = i?.getLongExtra(EXTRA_START, ring) ?: ring
    }

    companion object {
        const val EXTRA_START = "sunrise_start"
        const val EXTRA_RING = "sunrise_ring"
        private const val TICK_MS = 500L
        private const val LINGER_MS = 90_000L
    }
}

@Composable
private fun SunriseScreen(alarmId: Int, startAt: Long, ringAt: Long, now: Long) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val alarm = remember(alarmId) { ClockStore.load(context).alarms.firstOrNull { it.id == alarmId } }
    var solving by remember(alarmId, ringAt) { mutableStateOf(false) }
    val challenge = alarm?.challenge ?: 0

    fun awake() {
        // Skips the ring this sunrise leads to (a one-time alarm switches off); the page then closes by itself.
        Alarms.skipNext(context, alarmId)
        activity?.finish()
    }

    if (solving && challenge > 0) {
        BackHandler { solving = false }
        Column(
            Modifier.fillMaxSize().background(CtrlColors.Background).statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.sunrise_skip_sub, time(context, ringAt)), style = MaterialTheme.typography.titleMedium, color = CtrlColors.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            ChallengePanel(challenge, key = ringAt) { awake() }
            Spacer(Modifier.height(16.dp))
            SunriseButton(stringResource(R.string.back), CtrlColors.Surface, CtrlColors.Text, Modifier.fillMaxWidth().widthIn(max = 480.dp)) { solving = false }
        }
        return
    }

    val p = SunriseMath.progress(now, startAt, ringAt)
    val bg = Color(SunriseMath.colorAt(p))
    val fg = if (SunriseMath.darkText(p)) Color(0xFF2A1A10) else Color(0xFFFFE6CC).copy(alpha = 0.55f + 0.35f * p)
    Column(
        Modifier.fillMaxSize().background(bg).padding(horizontal = Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.WbSunny, null, Modifier.size(40.dp), tint = fg)
        Spacer(Modifier.height(20.dp))
        Text(time(context, now), style = CtrlType.Hero.copy(fontSize = 84.sp), color = fg)
        Spacer(Modifier.height(8.dp))
        Text(
            alarm?.label?.ifBlank { null } ?: stringResource(R.string.sunrise_title),
            style = MaterialTheme.typography.headlineSmall,
            color = fg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.sunrise_until, time(context, ringAt)), style = CtrlType.Data, color = fg)
        Spacer(Modifier.weight(1.4f))
        SunriseButton(
            stringResource(R.string.sunrise_awake),
            fg.copy(alpha = 0.16f),
            fg,
            Modifier.fillMaxWidth().widthIn(max = 480.dp),
            outline = fg.copy(alpha = 0.4f),
        ) { if (challenge > 0) solving = true else awake() }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (challenge > 0) R.string.sunrise_awake_sub_challenge else R.string.sunrise_awake_sub),
            style = MaterialTheme.typography.bodySmall,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SunriseButton(label: String, bg: Color, fg: Color, modifier: Modifier, outline: Color? = null, onClick: () -> Unit) {
    Row(
        modifier.clip(CtrlShapes.Hero).background(bg)
            .then(if (outline != null) Modifier.border(1.dp, outline, CtrlShapes.Hero) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (outline == null) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(22.dp), tint = fg)
            Spacer(Modifier.size(8.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}

private fun time(context: android.content.Context, millis: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))
