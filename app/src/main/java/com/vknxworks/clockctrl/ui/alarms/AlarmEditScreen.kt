package com.vknxworks.clockctrl.ui.alarms

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.alarm.Alarms
import com.vknxworks.clockctrl.alarm.Level
import com.vknxworks.clockctrl.alarm.Scheduler
import com.vknxworks.clockctrl.alarm.SunriseMath
import com.vknxworks.clockctrl.ui.components.GroupChoice
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.WbSunny
import com.vknxworks.clockctrl.container
import com.vknxworks.clockctrl.data.Alarm
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.weekDays
import com.vknxworks.clockctrl.ui.common.dayNarrow
import com.vknxworks.clockctrl.ui.common.dayShort
import com.vknxworks.clockctrl.ui.common.firstDay
import com.vknxworks.clockctrl.ui.common.is24h
import com.vknxworks.clockctrl.ui.common.rememberSoundPicker
import com.vknxworks.clockctrl.ui.common.ringtoneName
import com.vknxworks.clockctrl.ui.common.untilText
import com.vknxworks.clockctrl.ui.components.ConfirmDialog
import com.vknxworks.clockctrl.ui.components.CtrlSquareButton
import com.vknxworks.clockctrl.ui.components.CtrlTextField
import com.vknxworks.clockctrl.ui.components.Group
import com.vknxworks.clockctrl.ui.components.GroupRow
import com.vknxworks.clockctrl.ui.components.GroupSwitch
import com.vknxworks.clockctrl.ui.components.ScreenTopBar
import com.vknxworks.clockctrl.ui.components.SectionLabel
import com.vknxworks.clockctrl.ui.nav.LocalNavigator
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter

/** Adding (id -1) or changing an alarm. */
@Composable
fun AlarmEditScreen(id: Int) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val original = remember(id) { ClockStore.data.value.alarms.firstOrNull { it.id == id } }
    val start = remember(id) {
        original ?: java.time.LocalTime.now().plusHours(1).let { Alarm(id = -1, hour = it.hour, minute = 0) }
    }
    val time = rememberTimePickerState(start.hour, start.minute, is24h(context))
    var days by remember { mutableStateOf(start.days) }
    var label by remember { mutableStateOf(start.label) }
    var ringtone by remember { mutableStateOf(start.ringtone) }
    var vibrate by remember { mutableStateOf(start.vibrate) }
    var sunrise by remember { mutableStateOf(start.sunriseMinutes) }
    var challenge by remember { mutableStateOf(start.challenge) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickSound = rememberSoundPicker { ringtone = it }

    fun build(): Alarm {
        // A changed time or repeat makes it a fresh alarm: no skip and no fixed date left over.
        val changed = original == null || original.hour != time.hour || original.minute != time.minute || original.days != days
        return start.copy(
            hour = time.hour, minute = time.minute, days = days, enabled = true, label = label.trim(),
            ringtone = ringtone, vibrate = vibrate, sunriseMinutes = sunrise, challenge = challenge,
            skipUntil = if (changed) 0L else start.skipUntil,
            date = if (changed) null else start.date,
        )
    }

    fun save() {
        var a = build()
        if (a.id < 0) a = a.copy(id = ClockStore.newId(context))
        Scheduler.save(context, a)
        a.next()?.let {
            Toast.makeText(context, context.getString(R.string.alarm_rings, untilText(context, it.toInstant().toEpochMilli() - System.currentTimeMillis())), Toast.LENGTH_SHORT).show()
        }
        nav.back()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            stringResource(if (original == null) R.string.new_alarm else R.string.edit_alarm),
            actions = {
                if (original != null) CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { confirmDelete = true }, tint = CtrlColors.Error)
                CtrlSquareButton(Icons.Outlined.Check, stringResource(R.string.save), onClick = ::save, tint = CtrlColors.AccentText)
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = time,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = CtrlColors.Surface,
                        selectorColor = CtrlColors.Accent,
                        containerColor = CtrlColors.Surface,
                        clockDialSelectedContentColor = CtrlColors.OnAccent,
                        clockDialUnselectedContentColor = CtrlColors.Text,
                        periodSelectorSelectedContainerColor = CtrlColors.Accent,
                        periodSelectorSelectedContentColor = CtrlColors.OnAccent,
                        periodSelectorUnselectedContentColor = CtrlColors.Text,
                        periodSelectorBorderColor = CtrlColors.ControlOutline,
                        timeSelectorSelectedContainerColor = CtrlColors.Accent,
                        timeSelectorSelectedContentColor = CtrlColors.OnAccent,
                        timeSelectorUnselectedContainerColor = CtrlColors.Surface,
                        timeSelectorUnselectedContentColor = CtrlColors.Text,
                    ),
                )
            }

            Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                SectionLabel(stringResource(R.string.repeat), Modifier.padding(start = 4.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays(firstDay(settings.weekStart)).forEach { d ->
                        val on = d.value in days
                        Box(
                            Modifier.weight(1f).size(44.dp).clip(CtrlShapes.Control)
                                .background(if (on) CtrlColors.Accent else CtrlColors.Surface)
                                .border(1.dp, if (on) CtrlColors.Accent else CtrlColors.ControlOutline, CtrlShapes.Control)
                                .clickable(role = Role.Checkbox) { days = if (on) days - d.value else days + d.value }
                                .semantics { contentDescription = dayShort(d); selected = on },
                            contentAlignment = Alignment.Center,
                        ) { Text(dayNarrow(d), style = CtrlType.LabelStrong, color = if (on) CtrlColors.OnAccent else CtrlColors.Text) }
                    }
                }
            }

            Group {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtrlTextField(
                        value = label,
                        onValueChange = { label = it.take(60) },
                        placeholder = { Text(stringResource(R.string.label_hint)) },
                        leadingIcon = { androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.Label, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GroupRow(stringResource(R.string.sound), ringtoneName(context, ringtone), icon = Icons.Outlined.MusicNote, showChevron = true, onClick = { pickSound(ringtone) })
                GroupSwitch(stringResource(R.string.vibrate), null, vibrate, Icons.Outlined.Vibration) { vibrate = it }
                if (original != null && original.enabled && original.repeating) {
                    GroupRow(
                        stringResource(if (original.skipUntil > System.currentTimeMillis()) R.string.unskip_next else R.string.skip_next),
                        stringResource(R.string.skip_next_sub),
                        icon = Icons.Outlined.SkipNext,
                        onClick = {
                            if (original.skipUntil > System.currentTimeMillis()) Scheduler.save(context, original.copy(skipUntil = 0L))
                            else Alarms.skipNext(context, original.id)
                            nav.back()
                        },
                    )
                }
            }

            Group(stringResource(R.string.group_wake_up), footer = stringResource(R.string.wake_up_footer)) {
                GroupChoice(
                    stringResource(R.string.sunrise),
                    listOf(0 to stringResource(R.string.sunrise_off)) + SunriseMath.CHOICES.map { it to stringResource(R.string.sunrise_before, it) },
                    sunrise,
                    icon = Icons.Outlined.WbSunny,
                    fallbackLabel = stringResource(R.string.sunrise_before, sunrise),
                ) { sunrise = it }
                GroupChoice(
                    stringResource(R.string.challenge),
                    listOf(
                        Level.OFF to stringResource(R.string.challenge_off),
                        Level.EASY to stringResource(R.string.challenge_easy),
                        Level.MEDIUM to stringResource(R.string.challenge_medium),
                        Level.HARD to stringResource(R.string.challenge_hard),
                    ),
                    challenge,
                    icon = Icons.Outlined.Calculate,
                ) { challenge = it }
            }
        }
    }

    if (confirmDelete && original != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_alarm_title),
            body = stringResource(R.string.delete_alarm_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                Scheduler.delete(context, original.id)
                nav.back()
            },
        )
    }
}
