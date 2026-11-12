package com.vknxworks.clockctrl

import android.content.Context
import com.vknxworks.clockctrl.data.Alarm
import com.vknxworks.clockctrl.data.lock.LockVault
import com.vknxworks.clockctrl.data.settings.AppSettings
import com.vknxworks.clockctrl.data.settings.SettingsRepository
import com.vknxworks.clockctrl.ui.lock.AppLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Manual dependency container; one per process, created once the phone is unlocked. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)

    /** Which tab to open (from a notification or another app). */
    val openTab = MutableStateFlow<Int?>(null)

    /** A new alarm another app asked for, to finish in the editor. */
    val newAlarm = MutableStateFlow<Alarm?>(null)

    /** The app lock's PIN and fingerprint key (the shared Ctrl series lock). */
    val vault = LockVault(context, "clockctrl")

    suspend fun enableLock(pin: String) {
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
    }

    fun disableLock() {
        vault.disable()
    }

    /**
     * "Forgot PIN?": the PIN can't be recovered. Your alarms keep ringing (a reset must never make you oversleep);
     * ClockCtrl's app settings are reset and the lock turned off.
     */
    suspend fun forgetEverything() {
        settings.update { AppSettings() }
        vault.disable()
        AppLock.markUnlocked()
    }

    fun onLocked() {
        vault.close()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }

    init {
        AppLock.onLocked = { onLocked() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ClockCtrlApp).container
