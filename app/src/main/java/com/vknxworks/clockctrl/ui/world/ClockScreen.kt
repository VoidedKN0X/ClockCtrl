package com.vknxworks.clockctrl.ui.world

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.alarm.Scheduler
import com.vknxworks.clockctrl.container
import com.vknxworks.clockctrl.data.ClockStore
import com.vknxworks.clockctrl.ui.common.clockParts
import com.vknxworks.clockctrl.ui.common.rememberNow
import com.vknxworks.clockctrl.ui.components.CtrlCard
import com.vknxworks.clockctrl.ui.components.CtrlHeroCard
import com.vknxworks.clockctrl.ui.components.CtrlSquareButton
import com.vknxworks.clockctrl.ui.components.CtrlTextField
import com.vknxworks.clockctrl.ui.components.MessageView
import com.vknxworks.clockctrl.ui.components.ScreenTopBar
import com.vknxworks.clockctrl.ui.components.TabTitle
import com.vknxworks.clockctrl.ui.components.bottomClearance
import com.vknxworks.clockctrl.ui.nav.CityPickerRoute
import com.vknxworks.clockctrl.ui.nav.LocalNavigator
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlType
import com.vknxworks.clockctrl.ui.theme.Gutter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** "Europe/Brussels" → "Brussels"; "America/Argentina/Buenos_Aires" → "Buenos Aires". */
fun cityName(id: String): String = id.substringAfterLast('/').replace('_', ' ')

fun zoneLabel(id: String): String = runCatching {
    ZoneId.of(id).getDisplayName(TextStyle.FULL, Locale.getDefault())
}.getOrDefault(id)

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow(if (settings.showSeconds) 1000L else 10_000L)
    val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val next = remember(data, now / 60_000) { Scheduler.nextAlarm(data) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TabTitle(
                stringResource(R.string.tab_clock),
                trailing = { CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.add_city), onClick = { nav.navigate(CityPickerRoute) }) },
            )
        }
        item {
            CtrlHeroCard(
                label = cityName(ZoneId.systemDefault().id),
                meta = local.format(DateTimeFormatter.ofPattern("zzz", Locale.getDefault())),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            ) {
                val (time, ampm) = clockParts(context, local.hour, local.minute)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, style = CtrlType.Hero, color = CtrlColors.OnAccent)
                    if (settings.showSeconds) Text(":%02d".format(local.second), style = CtrlType.Readout, color = CtrlColors.OnAccent, modifier = Modifier.padding(bottom = 8.dp))
                    if (ampm != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(ampm, style = CtrlType.LabelStrong.copy(fontSize = 16.sp), color = CtrlColors.OnAccent, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                Text(local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())), style = MaterialTheme.typography.titleMedium, color = CtrlColors.OnAccent)
                next?.let { (_, at) ->
                    val (t, ap) = clockParts(context, at.hour, at.minute)
                    Text(
                        stringResource(R.string.next_alarm_at, at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), t + (ap?.let { " $it" } ?: "")),
                        style = CtrlType.Data, color = CtrlColors.OnAccent,
                    )
                }
            }
        }
        if (data.cities.isEmpty()) {
            item {
                MessageView(
                    Icons.Outlined.Public, stringResource(R.string.no_cities), stringResource(R.string.no_cities_body),
                    modifier = Modifier.heightIn(min = 280.dp), actionLabel = stringResource(R.string.add_city), onAction = { nav.navigate(CityPickerRoute) },
                )
            }
        } else {
            items(data.cities, key = { it }) { id -> CityRow(id, now, onRemove = { ClockStore.update(context) { d -> d.copy(cities = d.cities - id) } }) }
        }
        item { Spacer(Modifier.bottomClearance(0.dp)) }
    }
}

@Composable
private fun CityRow(id: String, now: Long, onRemove: () -> Unit) {
    val context = LocalContext.current
    val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return
    val there = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
    val here = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val diffMin = (there.offset.totalSeconds - here.offset.totalSeconds) / 60
    val day = when (there.toLocalDate().compareTo(here.toLocalDate())) {
        0 -> stringResource(R.string.today)
        in 1..Int.MAX_VALUE -> stringResource(R.string.tomorrow)
        else -> stringResource(R.string.yesterday)
    }
    val diff = when {
        diffMin == 0 -> stringResource(R.string.same_time)
        diffMin % 60 == 0 -> "%+d h".format(diffMin / 60)
        else -> "%+d h %02d".format(diffMin / 60, kotlin.math.abs(diffMin % 60))
    }
    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cityName(id), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$day · $diff", style = CtrlType.Data, color = CtrlColors.Muted)
            }
            val (t, ap) = clockParts(context, there.hour, there.minute)
            Text(t + (ap?.let { " $it" } ?: ""), style = CtrlType.Readout.copy(fontSize = 30.sp), color = CtrlColors.Text)
            Spacer(Modifier.width(8.dp))
            CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.remove_city), onClick = onRemove, size = 40.dp, tint = CtrlColors.Muted)
        }
    }
}

/** Every city Android knows a time zone for, searchable by city and zone name. */
@Composable
fun CityPickerScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val data by ClockStore.data.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember {
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && !it.startsWith("US/") && !it.startsWith("Canada/") }
            .map { it to zoneLabel(it) }
            .sortedBy { cityName(it.first) }
    }
    val shown = remember(query, all) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { (id, label) -> cityName(id).lowercase().contains(q) || label.lowercase().contains(q) || id.lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.add_city))
        CtrlTextField(
            value = query,
            onValueChange = { query = it.take(60) },
            placeholder = { Text(stringResource(R.string.search_city)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({
                CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.clear), onClick = { query = "" }, size = 36.dp, tint = CtrlColors.Muted)
            }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(shown, key = { it.first }) { (id, label) ->
                val added = id in data.cities
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !added) {
                        ClockStore.update(context) { d -> if (id in d.cities) d else d.copy(cities = d.cities + id) }
                        nav.back()
                    }.heightIn(min = 60.dp).padding(horizontal = Gutter, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cityName(id), style = MaterialTheme.typography.bodyLarge, color = if (added) CtrlColors.Muted else CtrlColors.Text)
                        Text("$label · ${id.substringBefore('/')}", style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (added) Text(stringResource(R.string.added), style = CtrlType.Data, color = CtrlColors.AccentText)
                }
            }
        }
    }
}
