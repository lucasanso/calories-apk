package com.calories.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate

private val TABS = listOf("Consumo", "Peso")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(onOpenDay: (LocalDate) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Histórico") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when (tab) {
            0 -> ConsumptionTab(onOpenDay, Modifier.padding(padding))
            else -> WeightTab(snackbar, Modifier.padding(padding))
        }
    }
}
