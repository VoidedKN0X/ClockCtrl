package com.vknxworks.clockctrl.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.BuildConfig
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.alarm.Scheduler
import com.vknxworks.clockctrl.container
import com.vknxworks.clockctrl.data.AlarmsBackup
import com.vknxworks.clockctrl.data.BackupFormatException
import com.vknxworks.clockctrl.data.Backups
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.data.SettingsBackup
import com.vknxworks.clockctrl.data.settings.WeekStart
import com.vknxworks.clockctrl.ui.components.CtrlAlertDialog
import com.vknxworks.clockctrl.ui.components.Group
import com.vknxworks.clockctrl.ui.components.GroupRow
import com.vknxworks.clockctrl.ui.lock.AppLock
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Export and import of the alarms and of the settings, each as a JSON file in a place you pick. A settings
 * file never holds the app lock or its PIN, and importing one can't turn the lock off or switch "Hide in
 * screenshots" / "Hide in recent apps" off. Every import says what it will change and asks first.
 */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val data by ClockStore.data.collectAsStateWithLifecycle()
    var pendingAlarms by remember { mutableStateOf<AlarmsBackup?>(null) }
    var pendingSettings by remember { mutableStateOf<SettingsBackup?>(null) }

    fun toast(id: Int) = Toast.makeText(context, id, Toast.LENGTH_LONG).show()

    fun saveTo(uri: Uri, text: String) {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
                }
            }.isSuccess
            toast(if (ok) R.string.backup_saved else R.string.backup_save_failed)
        }
    }

    fun readFrom(uri: Uri, onText: (String) -> Unit) {
        scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { readSmall(context, uri) } }.getOrNull()
            if (text == null) toast(R.string.backup_not_valid) else onText(text)
        }
    }

    fun failed(e: Throwable) = toast(if (e is BackupFormatException && e.newer) R.string.backup_too_new else R.string.backup_not_valid)

    val exportAlarms = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) saveTo(uri, Backups.exportAlarms(ClockStore.data.value.alarms, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString()))
    }
    val importAlarms = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) readFrom(uri) { text -> runCatching { Backups.parseAlarms(text) }.onSuccess { pendingAlarms = it }.onFailure(::failed) }
    }
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val s = c.settings.settings.value
        val d = ClockStore.data.value
        saveTo(
            uri,
            Backups.exportSettings(
                SettingsBackup(
                    appVersion = BuildConfig.VERSION_NAME,
                    createdAt = OffsetDateTime.now().toString(),
                    weekStart = s.weekStart.name,
                    showSeconds = s.showSeconds,
                    hideScreenshots = s.hideScreenshots,
                    hideRecents = s.hideRecents,
                    snoozeMinutes = d.snoozeMinutes,
                    ringMinutes = d.ringMinutes,
                    gradualSeconds = d.gradualSeconds,
                    timerVibrate = d.timerVibrate,
                    cities = d.cities,
                ),
            ),
        )
    }
    val importSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) readFrom(uri) { text -> runCatching { Backups.parseSettings(text) }.onSuccess { pendingSettings = it }.onFailure(::failed) }
    }
    val types = arrayOf("application/json", "text/*", "application/octet-stream")

    Group(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer)) {
        GroupRow(
            stringResource(R.string.backup_export_alarms),
            pluralStringResource(R.plurals.backup_alarm_count, data.alarms.size, data.alarms.size),
            icon = Icons.Outlined.FileDownload,
            onClick = if (data.alarms.isEmpty()) null else ({ AppLock.expectReturn(); exportAlarms.launch("clockctrl-alarms-${LocalDate.now()}.json") }),
        )
        GroupRow(
            stringResource(R.string.backup_import_alarms),
            stringResource(R.string.backup_import_alarms_sub),
            icon = Icons.Outlined.AlarmAdd,
            onClick = { AppLock.expectReturn(); importAlarms.launch(types) },
        )
        GroupRow(
            stringResource(R.string.backup_export_settings),
            stringResource(R.string.backup_export_settings_sub),
            icon = Icons.Outlined.FileUpload,
            onClick = { AppLock.expectReturn(); exportSettings.launch("clockctrl-settings-${LocalDate.now()}.json") },
        )
        GroupRow(
            stringResource(R.string.backup_import_settings),
            stringResource(R.string.backup_import_settings_sub),
            icon = Icons.Outlined.SettingsBackupRestore,
            onClick = { AppLock.expectReturn(); importSettings.launch(types) },
        )
    }

    pendingAlarms?.let { b ->
        val here = data.alarms.size
        CtrlAlertDialog(
            onDismissRequest = { pendingAlarms = null },
            title = { Text(stringResource(R.string.backup_import_alarms_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    pluralStringResource(R.plurals.backup_import_alarms_body, b.alarms.size, b.alarms.size, b.createdAt?.take(10) ?: "?") +
                        if (here > 0) "\n\n" + pluralStringResource(R.plurals.backup_import_alarms_here, here, here) else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAlarms = null
                    scope.launch { importAlarmsNow(context, b, replace = false); toast(R.string.backup_alarms_added) }
                }) { Text(stringResource(R.string.backup_add)) }
                if (here > 0) {
                    TextButton(onClick = {
                        pendingAlarms = null
                        scope.launch { importAlarmsNow(context, b, replace = true); toast(R.string.backup_alarms_replaced) }
                    }) { Text(stringResource(R.string.backup_replace), color = CtrlColors.Error) }
                }
            },
            dismissButton = { TextButton(onClick = { pendingAlarms = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }

    pendingSettings?.let { b ->
        CtrlAlertDialog(
            onDismissRequest = { pendingSettings = null },
            title = { Text(stringResource(R.string.backup_import_settings_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    stringResource(R.string.backup_import_settings_body, b.createdAt?.take(10) ?: "?", b.cities?.size ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSettings = null
                    scope.launch {
                        val ok = runCatching {
                            withContext(Dispatchers.IO) {
                                val zones = ZoneId.getAvailableZoneIds()
                                ClockStore.update(context) { d -> Backups.applyClockSettings(b, d) { it in zones } }
                            }
                            c.settings.update { cur ->
                                cur.copy(
                                    weekStart = b.weekStart?.let { n -> WeekStart.entries.firstOrNull { it.name == n } } ?: cur.weekStart,
                                    showSeconds = b.showSeconds ?: cur.showSeconds,
                                    // Only ever turned on by a file, never off.
                                    hideScreenshots = cur.hideScreenshots || b.hideScreenshots == true,
                                    hideRecents = cur.hideRecents || b.hideRecents == true,
                                )
                            }
                        }.isSuccess
                        toast(if (ok) R.string.backup_restored else R.string.backup_restore_failed)
                    }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingSettings = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}

/**
 * Adds the alarms of [b] (or, with [replace], deletes every alarm here first), each through [Scheduler] so
 * Android's alarms match the list.
 */
private suspend fun importAlarmsNow(context: Context, b: AlarmsBackup, replace: Boolean) = withContext(Dispatchers.IO) {
    if (replace) ClockStore.load(context).alarms.forEach { Scheduler.delete(context, it.id) }
    // Reserve the ids in one step.
    var first = 0
    ClockStore.update(context) { d -> first = d.nextId; d.copy(nextId = d.nextId + b.alarms.size) }
    val alarms = Backups.toAlarms(b, first, LocalDate.now()) { uri -> canPlay(context, uri) }
    alarms.forEach { Scheduler.save(context, it) }
}

/** Whether a ringtone from another phone can be opened here. */
private fun canPlay(context: Context, uri: String): Boolean = runCatching {
    context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
}.getOrDefault(false)

private fun readSmall(context: Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > Backups.MAX_BYTES) return@use null
        }
        out.toString(Charsets.UTF_8.name())
    }
