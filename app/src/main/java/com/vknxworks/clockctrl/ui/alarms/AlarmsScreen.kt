package com.vknxworks.clockctrl.ui.alarms

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.alarm.Scheduler
import com.vknxworks.clockctrl.container
import com.vknxworks.clockctrl.data.Alarm
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.weekDays
import com.vknxworks.clockctrl.ui.common.clockParts
import com.vknxworks.clockctrl.ui.common.firstDay
import com.vknxworks.clockctrl.ui.common.dayNarrow
import com.vknxworks.clockctrl.ui.common.rememberNow
import com.vknxworks.clockctrl.ui.common.untilText
import com.vknxworks.clockctrl.ui.components.CtrlSquareButton
import com.vknxworks.clockctrl.ui.components.CtrlSwitch
import com.vknxworks.clockctrl.ui.components.MessageView
import com.vknxworks.clockctrl.ui.components.TabTitle
import com.vknxworks.clockctrl.ui.components.WarningCard
import com.vknxworks.clockctrl.ui.components.bottomClearance
import com.vknxworks.clockctrl.ui.lock.AppLock
import com.vknxworks.clockctrl.ui.nav.AlarmEditRoute
import com.vknxworks.clockctrl.ui.nav.LocalNavigator
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter

@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow(30_000L)
    val next = remember(data, now) { Scheduler.nextAlarm(data) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(
            stringResource(R.string.tab_alarms),
            label = next?.let { (_, at) -> stringResource(R.string.next_alarm, untilText(context, at.toInstant().toEpochMilli() - now)) },
            trailing = { CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.add_alarm), onClick = { nav.navigate(AlarmEditRoute()) }) },
        )
        Warnings()
        if (data.alarms.isEmpty()) {
            MessageView(
                Icons.Outlined.AlarmAdd, stringResource(R.string.no_alarms), stringResource(R.string.no_alarms_body),
                modifier = Modifier.bottomClearance(),
                actionLabel = stringResource(R.string.add_alarm), onAction = { nav.navigate(AlarmEditRoute()) },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(data.alarms, key = { it.id }) { a ->
                    AlarmCard(a, firstDay(settings.weekStart), now, onClick = { nav.navigate(AlarmEditRoute(a.id)) }) { on ->
                        Scheduler.save(context, a.copy(enabled = on, skipUntil = 0L, date = if (on) a.date else null))
                    }
                }
                item { Spacer(Modifier.bottomClearance(0.dp)) }
            }
        }
    }
}

@Composable
private fun AlarmCard(a: Alarm, firstDay: java.time.DayOfWeek, now: Long, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val (time, ampm) = clockParts(context, a.hour, a.minute)
    val on = a.enabled
    val fg = if (on) CtrlColors.Text else CtrlColors.Muted
    Column(
        Modifier.fillMaxWidth().clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, if (on) CtrlColors.ControlOutline else CtrlColors.Outline, CtrlShapes.Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, style = CtrlType.Readout, color = fg)
                    if (ampm != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(ampm, style = CtrlType.LabelStrong, color = fg, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                val sub = listOfNotNull(
                    a.label.ifBlank { null },
                    if (on) a.next()?.let { untilText(context, it.toInstant().toEpochMilli() - now) } else null,
                    if (on && a.skipUntil > now) stringResource(R.string.skipping_next) else null,
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CtrlSwitch(on, onCheckedChange = onToggle)
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekDays(firstDay).forEach { d ->
                val active = d.value in a.days
                Box(
                    Modifier.size(28.dp).clip(CtrlShapes.Chip).background(if (active && on) CtrlColors.Accent else if (active) CtrlColors.Raised else CtrlColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(dayNarrow(d), style = CtrlType.LabelStrong, color = if (active && on) CtrlColors.OnAccent else if (active) CtrlColors.Text else CtrlColors.Inactive)
                }
            }
            if (a.days.isEmpty()) Text(stringResource(R.string.repeat_once), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
    }
}

/** What would stop an alarm from showing or ringing, with a way to fix it. */
@Composable
private fun Warnings() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val nm = context.getSystemService(NotificationManager::class.java)
    val notificationsOn = remember(tick) { nm?.areNotificationsEnabled() != false }
    val fullScreen = remember(tick) { nm?.canUseFullScreenIntent() != false }
    val exact = remember(tick) { context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false }
    var asked by remember { mutableIntStateOf(0) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++; tick++ }

    fun open(action: String) {
        AppLock.expectReturn()
        runCatching { context.startActivity(Intent(action, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
    }

    val warnings = buildList<Pair<Int, () -> Unit>> {
        if (!notificationsOn) add(R.string.warn_notifications to {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            AppLock.expectReturn()
            if (!granted && asked == 0) {
                notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Refused before, or turned off in Android's settings: open ClockCtrl's notification settings.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        })
        if (notificationsOn && !fullScreen) add(R.string.warn_full_screen to { open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) })
        if (!exact) add(R.string.warn_exact to { open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) })
    }
    warnings.forEach { (text, fix) ->
        WarningCard(stringResource(text), Icons.Outlined.NotificationsOff, Modifier.padding(horizontal = Gutter, vertical = 4.dp).clickable(onClick = fix))
    }
}
