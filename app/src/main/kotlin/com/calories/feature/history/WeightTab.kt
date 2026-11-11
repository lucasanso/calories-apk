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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.WeightRecord
import com.calories.domain.nutrition.WeightRules
import com.calories.ui.UiEvent
import com.calories.ui.charts.WeightLineChart
import com.calories.ui.components.DateField
import com.calories.ui.components.EmptyState
import com.calories.ui.components.InfoNote
import com.calories.ui.components.NumberField
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
import com.calories.ui.theme.TabularNumbers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Aba "Peso": último registro, gráfico com média móvel e lista editável. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTab(snackbar: SnackbarHostState, modifier: Modifier = Modifier, viewModel: WeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WeightRecord?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            if (e is UiEvent.Message) launch {
                val r = snackbar.showSnackbar(e.text, actionLabel = e.undo?.let { "Desfazer" }, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) e.undo?.invoke()
            }
        }
    }
    val s = state ?: return
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "latest") {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Peso atual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.latest?.let { "${Fmt.number(it.weightKg, 1)} kg" } ?: "—", style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers))
                        s.latest?.let { Text(Fmt.relativeDate(it.date, viewModel.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    FilledTonalButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Registrar")
                    }
                }
            }
        }
        item(key = "windows") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightWindow.entries.forEach { w -> FilterChip(selected = s.window == w, onClick = { viewModel.setWindow(w) }, label = { Text(w.label) }) }
            }
        }
        if (s.records.isEmpty()) {
            item(key = "empty") { EmptyState("Nenhum peso neste período", "Registre seu peso para acompanhar a evolução. Um registro por dia.", icon = Icons.Outlined.MonitorWeight) }
        } else {
            item(key = "chart") {
                SectionCard {
                    s.change?.let { c ->
                        Text(
                            "${if (c > 0) "+" else if (c < 0) "−" else ""}${Fmt.number(kotlin.math.abs(c), 1)} kg em ${s.window.label.lowercase()}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    WeightLineChart(
                        values = s.records.map { it.weightKg },
                        average = s.movingAverage.takeIf { s.records.size >= 3 },
                        description = "Gráfico de peso com ${s.records.size} registro(s), de ${Fmt.number(s.records.first().weightKg, 1)} " +
                            "a ${Fmt.number(s.records.last().weightKg, 1)} kg",
                    )
                    InfoNote("A média móvel suaviza variações do dia a dia (água, sal, horário da pesagem).", Modifier.padding(top = 8.dp))
                }
            }
            item(key = "list") {
                SectionCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    val desc = s.records.reversed()
                    desc.forEachIndexed { i, r ->
                        val previous = desc.getOrNull(i + 1)
                        WeightRow(r, previous, viewModel.today, onClick = { editing = r }, onDelete = { viewModel.delete(r) })
                        if (i < desc.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        WeightDialog(
            initial = editing,
            today = viewModel.today,
            onSave = { date, kg, note -> viewModel.save(date, kg, note); adding = false; editing = null },
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
private fun WeightRow(record: WeightRecord, previous: WeightRecord?, today: LocalDate, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.relativeDate(record.date, today), style = MaterialTheme.typography.bodyMedium)
            record.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        previous?.let { p ->
            val d = record.weightKg - p.weightKg
            Text(
                "${if (d > 0) "+" else if (d < 0) "−" else ""}${Fmt.number(kotlin.math.abs(d), 1)}",
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text("${Fmt.number(record.weightKg, 1)} kg", style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers))
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Remover peso de ${Fmt.fullDate(record.date)}") }
    }
}

@Composable
private fun WeightDialog(initial: WeightRecord?, today: LocalDate, onSave: (LocalDate, Double?, String?) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var text by rememberSaveable { mutableStateOf(Fmt.input(initial?.weightKg)) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    val kg = Fmt.parse(text)
    val errors = WeightRules.validate(kg, date, today)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Registrar peso" else "Editar peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField("Data", date, { date = it }, Modifier.fillMaxWidth(), maxDate = today)
                NumberField(
                    value = text, onValueChange = { text = it }, label = "Peso", suffix = "kg",
                    isError = text.isNotEmpty() && errors.isNotEmpty(),
                    supporting = if (text.isNotEmpty()) errors.firstOrNull() else "Um registro por dia; registrar de novo na mesma data substitui.",
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(note, { note = it.take(120) }, label = { Text("Observação (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date, kg, note) }, enabled = errors.isEmpty()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
