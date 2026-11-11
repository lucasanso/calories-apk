package com.calories.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.DaySummary
import com.calories.domain.model.DiaryEntry
import com.calories.domain.model.MealSlot
import com.calories.domain.model.MealSummary
import com.calories.domain.nutrition.Nutrient
import com.calories.ui.UiEvent
import com.calories.ui.components.CalorieRing
import com.calories.ui.components.EmptyState
import com.calories.ui.components.MacroBar
import com.calories.ui.components.QuantitySheet
import com.calories.ui.components.SectionCard
import com.calories.ui.components.StatColumn
import com.calories.ui.components.TotalsTable
import com.calories.ui.components.nutrientColor
import com.calories.ui.format.Fmt
import com.calories.ui.theme.TabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TodayRoute(
    onAddFood: (LocalDate, Long) -> Unit,
    onOpenFood: (Long) -> Unit,
    onOpenGoals: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            if (e is UiEvent.Message) {
                val result = snackbar.showSnackbar(e.text, actionLabel = e.undo?.let { "Desfazer" }, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) e.undo?.invoke()
            }
        }
    }
    TodayScreen(
        date = date,
        today = viewModel.today,
        summary = summary,
        slots = slots,
        snackbar = snackbar,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onPickDate = viewModel::setDate,
        onAddFood = { slotId -> onAddFood(date, slotId) },
        onOpenFood = onOpenFood,
        onOpenGoals = onOpenGoals,
        onUpdateAmount = viewModel::updateAmount,
        onDelete = viewModel::delete,
        onMove = viewModel::move,
        onRepeatPreviousDay = viewModel::repeatPreviousDay,
        onClear = viewModel::clear,
        onSaveTemplate = viewModel::saveAsTemplate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    date: LocalDate,
    today: LocalDate,
    summary: DaySummary?,
    slots: List<MealSlot>,
    snackbar: SnackbarHostState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onAddFood: (Long) -> Unit,
    onOpenFood: (Long) -> Unit,
    onOpenGoals: () -> Unit,
    onUpdateAmount: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Long, Long) -> Unit,
    onRepeatPreviousDay: (Long) -> Unit,
    onClear: (Long) -> Unit,
    onSaveTemplate: (Long, String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var templateFor by remember { mutableStateOf<MealSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Fmt.relativeDate(date, today), style = MaterialTheme.typography.titleLarge)
                        if (date != today) Text(Fmt.fullDate(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onPreviousDay) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Dia anterior") }
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data") }
                    IconButton(onClick = onNextDay) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo dia") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { SummaryCard(summary, onOpenGoals) }
            item(key = "macros") { MacrosCard(summary) }
            items(summary.meals, key = { "meal-${it.slotId}-${it.mealId}" }) { meal ->
                MealCard(
                    meal = meal,
                    onAdd = { meal.slotId?.let(onAddFood) },
                    onEntryClick = { editing = it },
                    onRepeatPreviousDay = { meal.slotId?.let(onRepeatPreviousDay) },
                    onClear = { meal.slotId?.let(onClear) },
                    onSaveTemplate = { templateFor = meal },
                )
            }
            item(key = "totals") { DayTotalsCard(summary) }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPickDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }

    editing?.let { entry ->
        EntrySheet(entry, slots, onDismiss = { editing = null }, onSave = { onUpdateAmount(entry.id, it); editing = null },
            onDelete = { onDelete(entry.id); editing = null }, onMove = { onMove(entry.id, it); editing = null },
            onOpenFood = entry.foodId?.let { id -> { editing = null; onOpenFood(id) } })
    }

    templateFor?.let { meal ->
        var name by rememberSaveable { mutableStateOf(meal.name) }
        AlertDialog(
            onDismissRequest = { templateFor = null },
            title = { Text("Salvar como refeição favorita") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Os alimentos e quantidades poderão ser reaplicados em qualquer dia.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { meal.slotId?.let { onSaveTemplate(it, name) }; templateFor = null }, enabled = name.isNotBlank()) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { templateFor = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SummaryCard(summary: DaySummary, onOpenGoals: () -> Unit) {
    val consumed = summary.totals.energyKcal
    val goal = summary.goal?.energyKcal
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalorieRing(consumed = consumed, goal = goal, size = 150.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatLine("Consumido", "${Fmt.kcal(consumed)} kcal")
                if (goal != null) {
                    StatLine("Meta", "${Fmt.kcal(goal)} kcal")
                    StatLine("Atingido", Fmt.percent(consumed / goal))
                } else {
                    Text("Sem meta definida", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenGoals, contentPadding = PaddingValues(0.dp)) { Text("Definir meta") }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
    }
}

@Composable
private fun MacrosCard(summary: DaySummary) {
    val goal = summary.goal
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(
                Nutrient.PROTEIN to goal?.proteinG, Nutrient.CARBOHYDRATE to goal?.carbohydrateG,
                Nutrient.FAT to goal?.fatG, Nutrient.FIBER to goal?.fiberG,
            ).forEach { (n, g) ->
                val total = summary.totals[n]
                MacroBar(
                    label = n.labelPt.substringBefore(' ').let { if (n == Nutrient.FAT) "Gorduras" else it },
                    consumedText = Fmt.total(n, total, withUnit = g == null),
                    consumed = total.sum,
                    goal = g,
                    goalText = g?.let { Fmt.nutrient(n, it) },
                    color = nutrientColor(n)!!,
                )
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: MealSummary,
    onAdd: () -> Unit,
    onEntryClick: (DiaryEntry) -> Unit,
    onRepeatPreviousDay: () -> Unit,
    onClear: () -> Unit,
    onSaveTemplate: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.name, style = MaterialTheme.typography.titleMedium)
                val kcal = if (meal.entries.isEmpty()) "Nada registrado" else "${Fmt.kcal(meal.totals.energyKcal)} kcal"
                Text(listOfNotNull(meal.timeMinutes?.let(Fmt::time), kcal).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (meal.slotId != null) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Adicionar alimento em ${meal.name}") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções de ${meal.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Repetir do dia anterior") }, onClick = { menu = false; onRepeatPreviousDay() })
                        DropdownMenuItem(text = { Text("Salvar como favorita") }, onClick = { menu = false; onSaveTemplate() }, enabled = meal.entries.isNotEmpty())
                        DropdownMenuItem(text = { Text("Limpar refeição") }, onClick = { menu = false; onClear() }, enabled = meal.entries.isNotEmpty())
                    }
                }
            }
        }
        AnimatedVisibility(meal.entries.isNotEmpty()) {
            Column(Modifier.padding(end = 12.dp)) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                meal.entries.forEach { e -> EntryRow(e, onClick = { onEntryClick(e) }) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DiaryEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.foodName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Fmt.amount(entry.amount, entry.unit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(Fmt.value(Nutrient.ENERGY, entry.consumed[Nutrient.ENERGY] ?: com.calories.domain.nutrition.NutrientValue.NotAvailable, detail = false), style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
    }
}

@Composable
private fun DayTotalsCard(summary: DaySummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text("Resumo nutricional do dia", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Recolher" else "Expandir")
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                if (summary.entryCount == 0) EmptyState("Nada registrado", "Adicione alimentos às refeições para ver os totais.")
                else TotalsTable(summary.totals)
            }
        }
    }
}

@Composable
private fun EntrySheet(
    entry: DiaryEntry,
    slots: List<MealSlot>,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    onMove: (Long) -> Unit,
    onOpenFood: (() -> Unit)?,
) {
    var moveMenu by remember { mutableStateOf(false) }
    QuantitySheet(
        title = entry.foodName,
        subtitle = "Valores registrados no momento do consumo (${Fmt.sourceLabel(entry.dataSourceCode)} · ${entry.preparation.labelPt})",
        profile = entry.reference,
        initialAmount = entry.amount,
        confirmLabel = "Salvar quantidade",
        onConfirm = onSave,
        onDismiss = onDismiss,
        extraActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Remover", color = MaterialTheme.colorScheme.error) }
                Box {
                    TextButton(onClick = { moveMenu = true }) { Text("Mover para…") }
                    DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                        slots.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { moveMenu = false; onMove(s.id) }) }
                    }
                }
                onOpenFood?.let { TextButton(onClick = it) { Text("Ver alimento") } }
            }
        },
    )
}
