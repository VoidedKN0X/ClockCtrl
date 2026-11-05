package com.vknxworks.clockctrl

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.clockctrl.ui.lock.AppLock

/**
 * Alarms can ring before the phone is unlocked for the first time after a restart. The app's own settings and app
 * lock live in storage that only opens after that unlock, so they are set up then, not at start.
 */
class ClockCtrlApp : Application() {
    @Volatile private var _container: AppContainer? = null

    val container: AppContainer
        get() = _container ?: synchronized(this) { _container ?: AppContainer(this).also { _container = it } }

    private var observing = false

    override fun onCreate() {
        super.onCreate()
        // Alarms, timers and cities live in device-protected storage, readable even before the first unlock.
        com.vknxworks.clockctrl.data.ClockStore.load(this)
        if (getSystemService(UserManager::class.java)?.isUserUnlocked != false) {
            setUp()
        } else {
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    runCatching { unregisterReceiver(this) }
                    setUp()
                }
            }, IntentFilter(Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
        }
    }

    private fun setUp() {
        if (observing) return
        observing = true
        val c = container
        // The app lock: locks after the chosen time away ("Lock again").
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(c.vault.enabled, c.settings.settings.value.lockTimeout.millis)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(c.vault.enabled, c.settings.settings.value.lockTimeout.millis)
            }
        })
    }
}
