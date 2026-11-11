package com.calories.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.calories.feature.shopping.ShoppingTab
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TABS = listOf("Calendário", "Planos", "Compras")

/** Planejamento: calendário de refeições planejadas, planos reutilizáveis e listas de compras. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningRoute(onOpenDay: (LocalDate) -> Unit, onOpenPlan: (Long) -> Unit, onOpenList: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creatingPlan by rememberSaveable { mutableStateOf(false) }
    var generatingList by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Planejamento") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                1 -> ExtendedFloatingActionButton(
                    onClick = { creatingPlan = true }, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Novo plano") },
                    modifier = Modifier.semantics { contentDescription = "Novo plano" },
                )
                2 -> ExtendedFloatingActionButton(
                    onClick = { generatingList = true }, icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) }, text = { Text("Gerar lista") },
                    modifier = Modifier.semantics { contentDescription = "Gerar lista" },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> CalendarTab(onOpenDay, message, modifier)
            1 -> PlansTab(creatingPlan, { creatingPlan = it }, onOpenPlan, message, modifier)
            else -> ShoppingTab(generatingList, { generatingList = it }, onOpenList, message, modifier)
        }
    }
}
