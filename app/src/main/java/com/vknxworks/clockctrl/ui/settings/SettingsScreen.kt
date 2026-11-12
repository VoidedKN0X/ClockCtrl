package com.vknxworks.clockctrl.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.container
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.lock.LockVault
import com.vknxworks.clockctrl.data.settings.AppSettings
import com.vknxworks.clockctrl.data.settings.LockTimeout
import com.vknxworks.clockctrl.data.settings.WeekStart
import com.vknxworks.clockctrl.ui.common.rememberSoundPicker
import com.vknxworks.clockctrl.ui.common.ringtoneName
import com.vknxworks.clockctrl.ui.components.Group
import com.vknxworks.clockctrl.ui.components.GroupChoice
import com.vknxworks.clockctrl.ui.components.GroupRow
import com.vknxworks.clockctrl.ui.components.GroupSwitch
import com.vknxworks.clockctrl.ui.components.ScreenTopBar
import com.vknxworks.clockctrl.ui.lock.AppLock
import com.vknxworks.clockctrl.ui.lock.PinRequest
import com.vknxworks.clockctrl.ui.lock.findActivity
import kotlinx.coroutines.launch

/** Settings, opened from the You page (tap the profile picture). ClockCtrl follows the phone's dark theme. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { c.settings.update(transform) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 4.dp, bottom = 24.dp)) {
            AlarmsGroup(settings) { set(it) }
            TimersGroup()
            ClockGroup(settings) { set(it) }
            PrivacyAndSecurityGroup(settings)
        }
    }
}

@Composable
private fun minutes(n: Int) = pluralStringResource(R.plurals.minutes, n, n)

@Composable
private fun AlarmsGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    Group(stringResource(R.string.tab_alarms), footer = stringResource(R.string.alarms_footer)) {
        GroupChoice(
            stringResource(R.string.snooze_length),
            listOf(1, 3, 5, 10, 15, 20, 30).map { it to minutes(it) },
            data.snoozeMinutes,
            icon = Icons.Outlined.Snooze,
            fallbackLabel = minutes(data.snoozeMinutes),
        ) { m -> ClockStore.update(context) { it.copy(snoozeMinutes = m) } }
        GroupChoice(
            stringResource(R.string.silence_after),
            listOf(1, 5, 10, 15, 20, 30).map { it to minutes(it) },
            data.ringMinutes,
            icon = Icons.Outlined.TimerOff,
            fallbackLabel = minutes(data.ringMinutes),
        ) { m -> ClockStore.update(context) { it.copy(ringMinutes = m) } }
        GroupChoice(
            stringResource(R.string.gradual_volume),
            listOf(
                0 to stringResource(R.string.gradual_off),
                15 to stringResource(R.string.seconds_n, 15),
                30 to stringResource(R.string.seconds_n, 30),
                60 to stringResource(R.string.minute_one),
                120 to minutes(2),
            ),
            data.gradualSeconds,
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
        ) { s -> ClockStore.update(context) { it.copy(gradualSeconds = s) } }
        GroupChoice(
            stringResource(R.string.week_starts),
            WeekStart.entries.map { it to stringResource(it.label) },
            settings.weekStart,
            icon = Icons.Outlined.CalendarViewWeek,
        ) { w -> set { it.copy(weekStart = w) } }
        GroupRow(
            stringResource(R.string.alarm_volume),
            stringResource(R.string.alarm_volume_sub),
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            showChevron = true,
            onClick = { open(context, Intent(Settings.ACTION_SOUND_SETTINGS)) },
        )
    }
}

@Composable
private fun TimersGroup() {
    val context = LocalContext.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    val pick = rememberSoundPicker { uri -> ClockStore.update(context) { it.copy(timerRingtone = uri) } }
    val soundName = remember(data.timerRingtone) { ringtoneName(context, data.timerRingtone) }
    Group(stringResource(R.string.group_timers)) {
        GroupRow(stringResource(R.string.timer_sound), soundName, icon = Icons.Outlined.MusicNote, showChevron = true, onClick = { pick(data.timerRingtone) })
        GroupSwitch(stringResource(R.string.vibrate), null, data.timerVibrate, Icons.Outlined.Vibration) { on -> ClockStore.update(context) { it.copy(timerVibrate = on) } }
    }
}

@Composable
private fun ClockGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    Group(stringResource(R.string.tab_clock)) {
        GroupSwitch(stringResource(R.string.show_seconds), null, settings.showSeconds, Icons.Outlined.Timer) { on -> set { it.copy(showSeconds = on) } }
        GroupRow(
            stringResource(R.string.date_time),
            stringResource(R.string.date_time_sub),
            icon = Icons.Outlined.AccessTime,
            showChevron = true,
            onClick = { open(context, Intent(Settings.ACTION_DATE_SETTINGS)) },
        )
    }
}

private fun open(context: android.content.Context, intent: Intent) {
    AppLock.expectReturn()
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show() }
}

/** Privacy and security: the shared Ctrl series app lock, then hiding ClockCtrl in screenshots. */
@Composable
private fun PrivacyAndSecurityGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.app_lock_footer)) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            checked = lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                checked = c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                settings.lockTimeout,
                icon = Icons.Outlined.Timer,
            ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            checked = settings.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            checked = settings.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
    }
}
