package com.teknosolvo.personalhub.ui.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.util.DATE_KEY
import java.time.LocalDate

private val trackerTabs = listOf("Saat Ini", "Lokasi", "Harian", "Bulanan", "Statistik")

@Composable
fun TrackerHubScreen(
    initialTab: Int,
    onEditPlace: (Long) -> Unit,
    onEditActivity: (Long, String) -> Unit,
    onSearch: () -> Unit,
) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, trackerTabs.lastIndex)) }
    var dailyDate by rememberSaveable { mutableStateOf(LocalDate.now().format(DATE_KEY)) }

    HubScaffold(
        title = "Location Tracker",
        actions = { IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Cari") } },
        floatingActionButton = {
            when (tab) {
                1 -> ExtendedFloatingActionButton(onClick = { onEditPlace(0) }, icon = { Icon(Icons.Filled.AddLocationAlt, null) },
                    text = { Text("Lokasi") })
                2 -> ExtendedFloatingActionButton(onClick = { onEditActivity(0, dailyDate) }, icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Aktivitas") })
                else -> {}
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {},
                indicator = { pos ->
                    TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), width = 28.dp)
                },
            ) {
                trackerTabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "trackerTab") { t ->
                when (t) {
                    0 -> CurrentLocationTab(onAddPlace = { onEditPlace(0) })
                    1 -> PlacesTab(onEdit = onEditPlace)
                    2 -> DailyTab(dailyDate, onDateChange = { dailyDate = it }, onEdit = { onEditActivity(it, dailyDate) })
                    3 -> MonthlyTab(onOpenDay = { d -> dailyDate = d; tab = 2 }, onEdit = { onEditActivity(it, "") })
                    else -> StatisticsTab()
                }
            }
        }
    }
}
