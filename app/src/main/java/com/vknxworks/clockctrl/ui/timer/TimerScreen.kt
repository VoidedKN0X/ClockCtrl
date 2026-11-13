package com.vknxworks.clockctrl.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.alarm.RingService
import com.vknxworks.clockctrl.alarm.Timers
import com.vknxworks.clockctrl.alarm.formatMs
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.Timer
import com.vknxworks.clockctrl.ui.common.rememberNow
import com.vknxworks.clockctrl.ui.components.CtrlButton
import com.vknxworks.clockctrl.ui.components.CtrlProgressBar
import com.vknxworks.clockctrl.ui.components.CtrlSquareButton
import com.vknxworks.clockctrl.ui.components.NameDialog
import com.vknxworks.clockctrl.ui.components.TabTitle
import com.vknxworks.clockctrl.ui.components.bottomClearance
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Timer?>(null) }
    val now = rememberNow(250L)
    val showInput = adding || data.timers.isEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(
            stringResource(R.string.tab_timer),
            trailing = {
                if (data.timers.isNotEmpty()) {
                    CtrlSquareButton(if (adding) Icons.Outlined.Close else Icons.Outlined.Add, stringResource(if (adding) R.string.cancel else R.string.add_timer), onClick = { adding = !adding })
                }
            },
        )
        if (showInput) {
            TimerInput(onStart = { ms ->
                Timers.add(context, ms, "")
                adding = false
            })
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(data.timers.reversed(), key = { it.id }) { t -> TimerCard(t, now, onRename = { renaming = t }) }
                item { Spacer(Modifier.bottomClearance(0.dp)) }
            }
        }
    }
    renaming?.let { t ->
        NameDialog(stringResource(R.string.timer_label), t.label, onDismiss = { renaming = null }) { label ->
            Timers.rename(context, t.id, label)
            renaming = null
        }
    }
}

@Composable
private fun TimerCard(t: Timer, now: Long, onRename: () -> Unit) {
    val context = LocalContext.current
    val left = t.left(now)
    val ringing = t.ringing
    val progress = if (t.lengthMs > 0) left / t.lengthMs.toFloat() else 0f
    Column(
        Modifier.fillMaxWidth().clip(CtrlShapes.Card)
            .background(if (ringing) CtrlColors.Accent else CtrlColors.Surface)
            .border(1.dp, if (ringing) CtrlColors.Accent else CtrlColors.ControlOutline, CtrlShapes.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fg = if (ringing) CtrlColors.OnAccent else CtrlColors.Text
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (t.label.ifBlank { stringResource(R.string.timer_of, formatMs(t.lengthMs)) }).uppercase(),
                style = CtrlType.LabelStrong, color = if (ringing) CtrlColors.OnAccent else CtrlColors.Muted, modifier = Modifier.weight(1f),
            )
            if (!ringing) CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.timer_label), onClick = onRename, size = 36.dp, tint = CtrlColors.Muted)
            CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { Timers.delete(context, t.id) }, size = 36.dp, tint = if (ringing) CtrlColors.OnAccent else CtrlColors.Muted)
        }
        Text(
            if (ringing) stringResource(R.string.timer_done) else formatMs(left + if (t.running) 999 else 0),
            style = CtrlType.Hero.copy(fontSize = 56.sp), color = fg,
        )
        if (!ringing) CtrlProgressBar(progress, color = if (t.running) CtrlColors.Accent else CtrlColors.ProgressFill)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                ringing -> {
                    Pill(stringResource(R.string.stop), Icons.Outlined.Close, accent = false, onAccent = true, Modifier.weight(1f)) { RingService.stopTimer(context, t.id) }
                    Pill(stringResource(R.string.plus_minute), Icons.Outlined.Add, accent = false, onAccent = true, Modifier.weight(1f)) { Timers.addMinute(context, t.id) }
                }
                t.running -> {
                    Pill(stringResource(R.string.pause), Icons.Outlined.Pause, accent = true, onAccent = false, Modifier.weight(1f)) { Timers.pause(context, t.id) }
                    Pill(stringResource(R.string.plus_minute), Icons.Outlined.Add, accent = false, onAccent = false, Modifier.weight(1f)) { Timers.addMinute(context, t.id) }
                }
                else -> {
                    Pill(stringResource(if (t.reset) R.string.start else R.string.resume), Icons.Outlined.PlayArrow, accent = true, onAccent = false, Modifier.weight(1f)) { Timers.resume(context, t.id) }
                    Pill(stringResource(R.string.reset), Icons.Outlined.Replay, accent = false, onAccent = false, Modifier.weight(1f), enabled = !t.reset) { Timers.reset(context, t.id) }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Boolean, onAccent: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when {
        onAccent -> CtrlColors.OnAccent
        accent -> CtrlColors.Accent
        else -> CtrlColors.Raised
    }
    val fg = when {
        !enabled -> CtrlColors.Inactive
        onAccent -> CtrlColors.Accent
        accent -> CtrlColors.OnAccent
        else -> CtrlColors.Text
    }
    Row(
        modifier.clip(CtrlShapes.Button).background(bg).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}

/** Typing a time like on a microwave: digits fill in from the right (hh:mm:ss). Plus quick presets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerInput(onStart: (Long) -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    val padded = digits.padStart(6, '0')
    val h = padded.substring(0, 2).toInt()
    val m = padded.substring(2, 4).toInt()
    val s = padded.substring(4, 6).toInt()
    val totalMs = (h * 3600L + m * 60L + s) * 1000L

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Part(padded.substring(0, 2), "h", digits.length > 4)
            Part(padded.substring(2, 4), "m", digits.length > 2)
            Part(padded.substring(4, 6), "s", digits.isNotEmpty())
            Box(
                Modifier.padding(start = 8.dp, bottom = 10.dp).size(44.dp).clip(CtrlShapes.Control)
                    .combinedClickable(enabled = digits.isNotEmpty(), onClick = { digits = digits.dropLast(1) }, onLongClick = { digits = "" }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.backspace), tint = if (digits.isEmpty()) CtrlColors.Inactive else CtrlColors.Muted) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().widthIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 10, 30).forEach { min ->
                Box(
                    Modifier.weight(1f).clip(CtrlShapes.Chip).background(CtrlColors.Surface).border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Chip)
                        .clickable { onStart(min * 60_000L) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.preset_min, min), style = CtrlType.Data, color = CtrlColors.Text) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "")
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) Spacer(Modifier.weight(1f).height(58.dp))
                        else Box(
                            Modifier.weight(1f).height(58.dp).clip(CtrlShapes.Key).background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, CtrlShapes.Key)
                                .clickable(role = Role.Button) { digits = (digits + k).trimStart('0').take(6) },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, style = CtrlType.Key, color = CtrlColors.Text) }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        CtrlButton(
            stringResource(R.string.start),
            onClick = { onStart(totalMs); digits = "" },
            icon = Icons.Outlined.PlayArrow,
            enabled = totalMs in 1000L..(99L * 3600_000L + 59 * 60_000L + 59_000L),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )
        Spacer(Modifier.bottomClearance(12.dp))
    }
}

@Composable
private fun Part(value: String, unit: String, set: Boolean) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(value, style = CtrlType.Hero.copy(fontSize = 48.sp), color = if (set) CtrlColors.Text else CtrlColors.Inactive)
        Text(unit, style = CtrlType.LabelStrong.copy(fontSize = 16.sp), color = CtrlColors.Muted, modifier = Modifier.padding(bottom = 10.dp, start = 2.dp))
    }
}
