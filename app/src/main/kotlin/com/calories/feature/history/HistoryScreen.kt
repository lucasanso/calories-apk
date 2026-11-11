package com.calories.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.DailyTotals
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.repository.DiaryRepository
import com.calories.ui.components.Dot
import com.calories.ui.components.EmptyState
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import com.calories.ui.components.nutrientColor
import com.calories.ui.format.Fmt
import com.calories.ui.theme.LocalNutrientColors
import com.calories.ui.theme.TabularNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(diary: DiaryRepository, clock: Clock) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    val days: StateFlow<List<DailyTotals>?> = diary.observeDailyTotals(today.minusDays(365), today.plusDays(30))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(onOpenDay: (LocalDate) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Histórico") }) }, contentWindowInsets = WindowInsets(0)) { padding ->
        val list = days ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState("Sem registros ainda", "Os dias com alimentos registrados aparecem aqui.", Modifier.padding(padding), icon = Icons.Outlined.History)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val recent = list.filter { !it.date.isAfter(viewModel.today) && it.date.isAfter(viewModel.today.minusDays(7)) }
                if (recent.isNotEmpty()) {
                    InfoNote("Média dos últimos 7 dias com registro (${recent.size} dia(s)): ${Fmt.kcal(recent.sumOf { it.totals.energyKcal } / recent.size)} kcal/dia. " +
                        "Gráficos semanais e mensais chegam na próxima versão.")
                }
            }
            items(list, key = { it.date.toEpochDay() }) { day -> DayRow(day, viewModel.today) { onOpenDay(day.date) } }
        }
    }
}

@Composable
private fun DayRow(day: DailyTotals, today: LocalDate, onClick: () -> Unit) {
    val goal = day.goal?.energyKcal
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.relativeDate(day.date, today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${Fmt.kcal(day.totals.energyKcal)}${goal?.let { " / ${Fmt.kcal(it)}" } ?: ""} kcal",
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = if (goal != null && day.totals.energyKcal > goal) LocalNutrientColors.current.over else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (goal != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (day.totals.energyKcal / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT).forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(nutrientColor(n)!!)
                    Column {
                        Text(Fmt.total(n, day.totals[n]), style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
                        Text(n.labelPt.substringBefore(' ').let { if (n == Nutrient.FAT) "Gorduras" else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
