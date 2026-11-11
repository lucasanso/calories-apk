package com.calories.feature.planning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.DietPlan
import com.calories.domain.model.PlannedDayOverview
import com.calories.domain.repository.PlanRepository
import com.calories.domain.usecase.ApplyPlanToDates
import com.calories.ui.UiEvent
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
import com.calories.ui.theme.TabularNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    plans: PlanRepository,
    private val applyPlanToDates: ApplyPlanToDates,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    private val _month = MutableStateFlow(YearMonth.from(today))
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    val overview: StateFlow<Map<LocalDate, PlannedDayOverview>> = _month
        .flatMapLatest { m -> plans.observeOverview(m.atDay(1), m.atEndOfMonth()) }
        .map { list -> list.filter { it.itemCount > 0 }.associateBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val planList: StateFlow<List<DietPlan>> = plans.observePlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun previousMonth() = _month.update { it.minusMonths(1) }
    fun nextMonth() = _month.update { it.plusMonths(1) }
    fun thisMonth() { _month.value = YearMonth.from(today) }

    fun apply(planId: Long, dates: List<LocalDate>, mode: ApplyMode) = viewModelScope.launch {
        runCatching { applyPlanToDates(planId, dates, mode) }
            .onSuccess { _events.send(UiEvent.Message("Plano aplicado em $it dia(s)")) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível aplicar")) }
    }
}

/** Aba "Calendário": mês com as calorias planejadas por dia. */
@Composable
fun CalendarTab(onOpenDay: (LocalDate) -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val plans by viewModel.planList.collectAsStateWithLifecycle()
    var applying by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.events.collect { if (it is UiEvent.Message) onMessage(it.text) } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "month") {
            SectionCard(padding = PaddingValues(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior") }
                    Text(Fmt.monthYear(month), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês") }
                }
                MonthGrid(month, viewModel.today, overview, onOpenDay)
                if (month != YearMonth.from(viewModel.today)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = viewModel::thisMonth) { Text("Ir para o mês atual") }
                    }
                }
            }
        }
        item(key = "summary") {
            SectionCard {
                val days = overview.values
                Text(
                    if (days.isEmpty()) "Nada planejado neste mês" else "${days.size} dia(s) planejados · média de ${Fmt.kcal(days.sumOf { it.energyKcal } / days.size)} kcal/dia",
                    style = MaterialTheme.typography.titleSmall,
                )
                InfoNote("Toque num dia para planejar as refeições. Planejar não conta como consumo: no dia, use “Registrar no diário”.", Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { applying = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.EventRepeat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aplicar plano em vários dias")
                }
            }
        }
    }
    if (applying) {
        ApplyPlanDialog(
            plans = plans, fixedPlan = null, initialDate = maxOf(viewModel.today, month.atDay(1)), singleDay = false,
            onApply = { id, dates, mode -> viewModel.apply(id, dates, mode); applying = false },
            onDismiss = { applying = false },
        )
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, overview: Map<LocalDate, PlannedDayOverview>, onDay: (LocalDate) -> Unit) {
    val lead = month.atDay(1).dayOfWeek.value - 1 // semana começa na segunda
    val rows = (lead + month.lengthOfMonth() + 6) / 7
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            DayOfWeek.entries.forEach { d ->
                Text(Fmt.weekdayShort(d), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(0.8f).padding(2.dp)) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            DayCell(date, overview[date], date == today) { onDay(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, planned: PlannedDayOverview?, isToday: Boolean, onClick: () -> Unit) {
    val description = Fmt.fullDate(date) + (planned?.let { ", ${Fmt.kcal(it.energyKcal)} kcal planejadas" } ?: ", nada planejado")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (planned != null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (planned != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (isToday) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, fontWeight = if (isToday) FontWeight.Bold else null)
            planned?.let { Text(Fmt.kcal(it.energyKcal), style = MaterialTheme.typography.labelSmall.merge(TabularNumbers), maxLines = 1) }
        }
    }
}
