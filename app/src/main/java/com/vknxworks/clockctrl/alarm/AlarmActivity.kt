package com.vknxworks.clockctrl.alarm

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.ui.theme.ClockCtrlTheme
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter
import kotlinx.coroutines.delay

/**
 * The ringing alarm or timer, over the lock screen. It works before the first unlock after a restart and is never
 * behind the app lock: an alarm must always be stoppable. It shows only the alarm.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(DARK_BAR) else SystemBarStyle.light(LIGHT_BAR, DARK_BAR)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose { }
            }
            val ringing by RingService.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(ringing) {
                if (ringing == null) {
                    // Give the service a moment to start after a tap from the notification.
                    delay(1500)
                    if (RingService.ringing.value == null) finishAndRemoveTask()
                }
            }
            ClockCtrlTheme(dark) {
                ringing?.let { AlarmScreen(it) } ?: Box(Modifier.fillMaxSize().background(CtrlColors.Background))
            }
        }
    }

    /** Volume buttons snooze an alarm (or stop a timer), like on most phones. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val r = RingService.ringing.value ?: return super.onKeyDown(keyCode, event)
            RingService.send(this, if (r.isTimer) RingService.ACTION_STOP_TIMER else RingService.ACTION_SNOOZE, r.id)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun AlarmScreen(r: Ringing) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(
        Modifier.fillMaxSize().background(CtrlColors.Background).statusBarsPadding().navigationBarsPadding().padding(horizontal = Gutter, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(84.dp).clip(CtrlShapes.Hero).background(CtrlColors.Accent), contentAlignment = Alignment.Center) {
            Icon(if (r.isTimer) Icons.Outlined.Timer else Icons.Outlined.Alarm, null, Modifier.size(40.dp), tint = CtrlColors.OnAccent)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)),
            style = CtrlType.Hero.copy(fontSize = 84.sp),
            color = CtrlColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            r.label.ifBlank { stringResource(if (r.isTimer) R.string.timer_done else R.string.alarm) },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (r.isTimer) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.timer_done_since, formatMs(now - r.startedAt)), style = CtrlType.Data, color = CtrlColors.Muted)
        }
        Spacer(Modifier.weight(1.4f))
        Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (r.isTimer) {
                Big(Icons.Outlined.Timer, stringResource(R.string.plus_minute), CtrlColors.Surface, CtrlColors.Text, Modifier.weight(1f)) {
                    Timers.addMinute(context, r.id)
                }
                Big(Icons.Outlined.TimerOff, stringResource(R.string.stop), CtrlColors.Accent, CtrlColors.OnAccent, Modifier.weight(1f)) {
                    RingService.send(context, RingService.ACTION_STOP_TIMER, r.id)
                }
            } else {
                Big(Icons.Outlined.Snooze, stringResource(R.string.snooze), CtrlColors.Surface, CtrlColors.Text, Modifier.weight(1f)) {
                    RingService.send(context, RingService.ACTION_SNOOZE, r.id)
                }
                Big(Icons.Outlined.AlarmOff, stringResource(R.string.dismiss), CtrlColors.Accent, CtrlColors.OnAccent, Modifier.weight(1f)) {
                    RingService.send(context, RingService.ACTION_DISMISS, r.id)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Big(icon: ImageVector, label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(CtrlShapes.Hero).background(bg).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = fg)
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}

private val DARK_BAR = android.graphics.Color.rgb(8, 8, 10)
private val LIGHT_BAR = android.graphics.Color.rgb(245, 245, 241)
