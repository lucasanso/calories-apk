package com.calories.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.DailyTotals
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.PeriodSeries
import com.calories.ui.charts.BarDatum
import com.calories.ui.charts.CalorieBarChart
import com.calories.ui.charts.MacroDatum
import com.calories.ui.charts.MacroShareChart
import com.calories.ui.components.Dot
import com.calories.ui.components.EmptyState
import com.calories.ui.components.InfoNote
import com.calories.ui.components.MacroBar
import com.calories.ui.components.SectionCard
import com.calories.ui.components.StatColumn
import com.calories.ui.components.nutrientColor
import com.calories.ui.format.Fmt
import com.calories.ui.theme.LocalNutrientColors
import com.calories.ui.theme.TabularNumbers
import java.time.LocalDate

/** Aba "Consumo": semana ou mês, gráficos de calorias e macros, comparação com a meta e lista de dias. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumptionTab(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "period") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HistoryPeriod.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = s.period == p,
                            onClick = { viewModel.setPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, HistoryPeriod.entries.size),
                        ) { Text(p.label) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previous) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Período anterior") }
                    Text(
                        if (s.period == HistoryPeriod.WEEK) Fmt.period(s.range.start, s.range.endInclusive, viewModel.today)
                        else Fmt.monthYear(java.time.YearMonth.from(s.range.start)),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::next, enabled = s.canGoForward) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo período") }
                }
                if (s.canGoForward) TextButton(onClick = viewModel::goToToday, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar para hoje") }
            }
        }
        if (s.stats.daysWithEntries == 0) {
            item(key = "empty") { EmptyState("Nada registrado neste período", "Os dias com alimentos registrados aparecem aqui, com gráficos e médias.", icon = Icons.Outlined.History) }
            return@LazyColumn
        }
        item(key = "calories") { CaloriesCard(s) }
        item(key = "macros") { MacrosCard(s) }
        item(key = "days-title") { Text("Dias registrados", style = MaterialTheme.typography.titleMedium) }
        items(s.daysWithEntries.reversed(), key = { it.date.toEpochDay() }) { day ->
            DayRow(day.totals!!, viewModel.today) { onOpenDay(day.date) }
        }
    }
}

@Composable
private fun CaloriesCard(s: ConsumptionState) {
    val avg = s.stats.averages[Nutrient.ENERGY] ?: 0.0
    val avgGoal = PeriodSeries.averageGoal(s.days) { it.energyKcal }
    val week = s.period == HistoryPeriod.WEEK
    val bars = s.days.map { d ->
        BarDatum(
            label = if (week) Fmt.weekdayShort(d.date.dayOfWeek) else d.date.dayOfMonth.takeIf { it == 1 || it % 5 == 0 }?.toString() ?: "",
            value = d.totals?.totals?.energyKcal,
            goal = d.goal?.energyKcal,
        )
    }
    SectionCard {
        Text("Calorias por dia", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn(Fmt.kcal(avg), "média kcal/dia")
            avgGoal?.let { StatColumn(Fmt.kcal(it), "meta média") }
            StatColumn("${s.stats.daysWithEntries}/${s.days.size}", "dias registrados")
        }
        Spacer(Modifier.height(16.dp))
        CalorieBarChart(
            data = bars,
            description = "Gráfico de calorias: média de ${Fmt.kcal(avg)} kcal por dia em ${s.stats.daysWithEntries} dia(s) registrados" +
                (avgGoal?.let { ", meta média de ${Fmt.kcal(it)} kcal" } ?: ""),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Dot(MaterialTheme.colorScheme.primary); Text("  Consumo", style = MaterialTheme.typography.labelSmall) }
            Row(verticalAlignment = Alignment.CenterVertically) { Dot(LocalNutrientColors.current.over); Text("  Acima de 110% da meta", style = MaterialTheme.typography.labelSmall) }
            Text("- - meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.stats.daysWithGoal > 0) {
            Spacer(Modifier.height(12.dp))
            InfoNote(
                "Dentro da meta (±10%) em ${s.stats.daysWithinGoal} de ${s.stats.daysWithGoal} dia(s) com meta. " +
                    (avgGoal?.let { g -> "Diferença média: ${signed(avg - g)} kcal/dia." } ?: ""),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        InfoNote("Médias consideram só os dias com registro.", Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MacrosCard(s: ConsumptionState) {
    val week = s.period == HistoryPeriod.WEEK
    SectionCard {
        Text("Macronutrientes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT, Nutrient.FIBER).forEach { n ->
                val avg = s.stats.averages[n] ?: 0.0
                val goal = PeriodSeries.averageGoal(s.days) { g ->
                    when (n) {
                        Nutrient.PROTEIN -> g.proteinG
                        Nutrient.CARBOHYDRATE -> g.carbohydrateG
                        Nutrient.FAT -> g.fatG
                        else -> g.fiberG
                    }
                }
                MacroBar(
                    label = "${n.labelPt.substringBefore(' ').let { if (n == Nutrient.FAT) "Gorduras" else it }} (média)",
                    consumedText = Fmt.nutrient(n, avg, withUnit = goal == null),
                    consumed = avg,
                    goal = goal,
                    goalText = goal?.let { Fmt.nutrient(n, it) },
                    color = nutrientColor(n)!!,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Energia por macronutriente", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        MacroShareChart(
            s.days.map { d ->
                val t = d.totals?.totals
                MacroDatum(
                    label = if (week) Fmt.weekdayShort(d.date.dayOfWeek) else d.date.dayOfMonth.takeIf { it == 1 || it % 5 == 0 }?.toString() ?: "",
                    proteinG = t?.get(Nutrient.PROTEIN)?.sum ?: 0.0,
                    carbohydrateG = t?.get(Nutrient.CARBOHYDRATE)?.sum ?: 0.0,
                    fatG = t?.get(Nutrient.FAT)?.sum ?: 0.0,
                )
            },
        )
        InfoNote("Proporção da energia de cada dia (4 kcal/g para proteínas e carboidratos, 9 kcal/g para gorduras).", Modifier.padding(top = 8.dp))
    }
}

private fun signed(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "") + Fmt.kcal(kotlin.math.abs(v))

@Composable
internal fun DayRow(day: DailyTotals, today: LocalDate, onClick: () -> Unit) {
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
