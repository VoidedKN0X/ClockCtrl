package com.vknxworks.clockctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object AlarmsRoute
@Serializable data object ClockRoute
@Serializable data object TimersRoute
@Serializable data object StopwatchRoute

// Opened from the profile picture: the You page, and Settings from there
@Serializable data object YouRoute
@Serializable data object SettingsRoute

// Pushed screens
/** Adding (-1) or changing an alarm. */
@Serializable data class AlarmEditRoute(val id: Int = -1)
@Serializable data object CityPickerRoute
