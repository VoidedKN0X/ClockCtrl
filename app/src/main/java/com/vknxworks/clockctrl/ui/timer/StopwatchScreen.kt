package com.vknxworks.clockctrl.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.alarm.Watch
import com.vknxworks.clockctrl.alarm.formatMs
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.ui.common.rememberNow
import com.vknxworks.clockctrl.ui.components.TabTitle
import com.vknxworks.clockctrl.ui.components.bottomClearance
import com.vknxworks.clockctrl.ui.components.topDivider
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter

@Composable
fun StopwatchScreen() {
    val context = LocalContext.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    val sw = data.stopwatch
    val now = rememberNow(if (sw.running) 40L else 1000L)
    val elapsed = sw.elapsed(now)
    // Each lap's own time: the split minus the one before it.
    val lapTimes = sw.laps.mapIndexed { i, total -> total - (if (i == 0) 0L else sw.laps[i - 1]) }
    val best = if (lapTimes.size > 1) lapTimes.indexOf(lapTimes.min()) else -1
    val worst = if (lapTimes.size > 1) lapTimes.indexOf(lapTimes.max()) else -1

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_stopwatch))
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatMs(elapsed, hundredths = true), style = CtrlType.Hero.copy(fontSize = if (elapsed >= 3_600_000L) 48.sp else 64.sp), color = CtrlColors.Text, maxLines = 1)
            if (sw.running && sw.laps.isNotEmpty()) {
                Text(stringResource(R.string.lap_n, sw.laps.size + 1) + " · " + formatMs(elapsed - sw.laps.last(), hundredths = true), style = CtrlType.Data, color = CtrlColors.Muted)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    sw.running -> {
                        Btn(stringResource(R.string.lap), Icons.Outlined.Flag, accent = false, Modifier.weight(1f)) { Watch.lap(context) }
                        Btn(stringResource(R.string.pause), Icons.Outlined.Pause, accent = true, Modifier.weight(1f)) { Watch.pause(context) }
                    }
                    elapsed > 0 -> {
                        Btn(stringResource(R.string.reset), Icons.Outlined.Replay, accent = false, Modifier.weight(1f)) { Watch.reset(context) }
                        Btn(stringResource(R.string.resume), Icons.Outlined.PlayArrow, accent = true, Modifier.weight(1f)) { Watch.start(context) }
                    }
                    else -> Btn(stringResource(R.string.start), Icons.Outlined.PlayArrow, accent = true, Modifier.weight(1f)) { Watch.start(context) }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            val ordered = lapTimes.withIndex().reversed()
            itemsIndexed(ordered.toList(), key = { _, it -> it.index }) { _, (i, lap) ->
                val color = when (i) {
                    best -> CtrlColors.AccentText
                    worst -> CtrlColors.Error
                    else -> CtrlColors.Text
                }
                Row(Modifier.fillMaxWidth().topDivider().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lap_n, i + 1), style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
                    Text(formatMs(lap, hundredths = true), style = CtrlType.Data.copy(fontSize = 15.sp), color = color, modifier = Modifier.padding(end = 18.dp))
                    Text(formatMs(sw.laps[i], hundredths = true), style = CtrlType.Data.copy(fontSize = 15.sp), color = CtrlColors.Muted)
                }
            }
            item { Spacer(Modifier.bottomClearance(0.dp)) }
        }
    }
}

@Composable
private fun Btn(label: String, icon: ImageVector, accent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (accent) CtrlColors.Accent else CtrlColors.Surface
    val fg = if (accent) CtrlColors.OnAccent else CtrlColors.Text
    Row(
        modifier.clip(CtrlShapes.Hero).background(bg).clickable(role = Role.Button, onClick = onClick).padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = fg)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}
