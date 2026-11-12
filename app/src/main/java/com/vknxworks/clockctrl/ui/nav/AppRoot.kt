package com.vknxworks.clockctrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.container
import com.vknxworks.clockctrl.ui.components.CtrlBottomBar
import com.vknxworks.clockctrl.ui.components.LocalBottomClearance
import com.vknxworks.clockctrl.ui.components.TabItem
import com.vknxworks.clockctrl.alarm.Scheduler
import com.vknxworks.clockctrl.ui.alarms.AlarmEditScreen
import com.vknxworks.clockctrl.ui.alarms.AlarmsScreen
import com.vknxworks.clockctrl.ui.settings.SettingsScreen
import com.vknxworks.clockctrl.ui.timer.StopwatchScreen
import com.vknxworks.clockctrl.ui.timer.TimerScreen
import com.vknxworks.clockctrl.ui.world.CityPickerScreen
import com.vknxworks.clockctrl.ui.world.ClockScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vknxworks.clockctrl.ui.settings.YouScreen
import com.vknxworks.clockctrl.ui.theme.CtrlColors

@Composable
fun AppRoot() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MainScaffold()
    }
}

private data class TopDest(val route: Any, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun TopDest.item(): TabItem = TabItem(stringResource(label), icon, selectedIcon)

/** Only content tabs: Settings and About open from the profile picture. */
private val topDestinations = listOf(
    TopDest(AlarmsRoute, R.string.tab_alarms, Icons.Outlined.Alarm, Icons.Filled.Alarm),
    TopDest(ClockRoute, R.string.tab_clock, Icons.Outlined.Public, Icons.Filled.Public),
    TopDest(TimersRoute, R.string.tab_timer, Icons.Outlined.Timer, Icons.Filled.Timer),
    TopDest(StopwatchRoute, R.string.tab_stopwatch, Icons.Outlined.Timelapse, Icons.Filled.Timelapse),
)

@Composable
private fun MainScaffold() {
    val context = LocalContext.current
    val c = context.container
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A notification, the alarm icon in the status bar or another app asks for a tab.
    val openTab by c.openTab.collectAsStateWithLifecycle()
    LaunchedEffect(openTab) {
        val tab = openTab ?: return@LaunchedEffect
        // Wait until the screens exist (opening the app from a notification).
        navController.currentBackStackEntryFlow.first()
        c.openTab.value = null
        // Leave the alarm editor or a settings page first, so the tab really shows.
        navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
        selectTab(tab.coerceIn(0, topDestinations.lastIndex))
    }
    // Opening the app also puts every alarm back with Android, in case it lost them (a force stop clears them).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { Scheduler.rescheduleAll(context.applicationContext) } }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, d ->
                            val top = d.item()
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = top.label) },
                                label = { Text(top.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

@Composable
private fun PhoneLayout(navController: NavHostController, showTabs: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            AppNavHost(navController)
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { overlayHeight = with(density) { it.height.toDp() } },
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    CtrlBottomBar(items = topDestinations.map { it.item() }, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = AlarmsRoute) {
        composable<AlarmsRoute> { AlarmsScreen() }
        composable<ClockRoute> { ClockScreen() }
        composable<TimersRoute> { TimerScreen() }
        composable<StopwatchRoute> { StopwatchScreen() }
        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<AlarmEditRoute> { entry -> AlarmEditScreen(entry.toRoute<AlarmEditRoute>().id) }
        composable<CityPickerRoute> { CityPickerScreen() }
    }
}
