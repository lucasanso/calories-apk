package com.calories.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.PlannedItem
import com.calories.domain.nutrition.NutrientProfile
import com.calories.ui.UiEvent
import com.calories.ui.components.DateField
import com.calories.ui.components.InfoNote
import com.calories.ui.format.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class DayDialog { APPLY, COPY, LOG_ALL, CLEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedDayRoute(
    onBack: () -> Unit,
    onAddFood: (LocalDate, Long) -> Unit,
    onOpenDiary: (LocalDate) -> Unit,
    viewModel: PlannedDayViewModel = hiltViewModel(),
) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plans by viewModel.planList.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DayDialog?>(null) }
    var logSlot by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var editing by remember { mutableStateOf<Pair<PlannedItem, NutrientProfile>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.Message -> launch {
                    val r = snackbar.showSnackbar(e.text, actionLabel = e.undo?.let { "Desfazer" }, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) e.undo?.invoke()
                }
                is UiEvent.LoggedToDiary -> launch {
                    val r = snackbar.showSnackbar(e.text, actionLabel = "Ver no diário", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) onOpenDiary(e.date)
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planejamento", style = MaterialTheme.typography.titleLarge)
                        Text(Fmt.relativeDate(date, viewModel.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    IconButton(onClick = viewModel::previousDay) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Dia anterior") }
                    IconButton(onClick = viewModel::nextDay) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo dia") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções do dia") }
                        val hasItems = (state?.itemCount ?: 0) > 0
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Aplicar plano…") }, onClick = { menu = false; dialog = DayDialog.APPLY })
                            DropdownMenuItem(text = { Text("Copiar dia para…") }, enabled = hasItems, onClick = { menu = false; dialog = DayDialog.COPY })
                            DropdownMenuItem(text = { Text("Registrar tudo no diário") }, enabled = hasItems, onClick = { menu = false; dialog = DayDialog.LOG_ALL })
                            DropdownMenuItem(text = { Text("Limpar dia") }, enabled = hasItems, onClick = { menu = false; dialog = DayDialog.CLEAR })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "totals") { PlanTotalsCard(s.totals, s.goal, "planejadas") }
            items(s.groups, key = { it.slot.id }) { g ->
                SlotGroupCard(
                    title = g.slot.name,
                    totals = g.totals,
                    rows = g.items.map { it.toRow() },
                    onAdd = { onAddFood(date, g.slot.id) },
                    onRowClick = { row ->
                        val item = g.items.first { it.id == row.id }
                        scope.launch { viewModel.profileOf(item.foodId)?.let { editing = item to it } }
                    },
                    menu = listOf(MenuAction("Registrar no diário", enabled = g.items.isNotEmpty()) { logSlot = g.slot.id to g.slot.name }),
                )
            }
            item(key = "note") {
                InfoNote("O planejamento usa os valores atuais de cada alimento. Ao registrar no diário, os valores do momento ficam guardados no histórico.")
            }
        }
    }

    editing?.let { (item, profile) ->
        PlanItemSheet(
            row = item.toRow(), profile = profile,
            onSave = { viewModel.updateAmount(item.id, it); editing = null },
            onDelete = { viewModel.delete(item); editing = null },
            onDismiss = { editing = null },
        )
    }
    logSlot?.let { (slotId, name) ->
        ConfirmDialog(
            title = "Registrar $name no diário?",
            text = "Os alimentos planejados entram no diário de ${Fmt.fullDate(date)}. O que já estiver registrado continua lá.",
            confirm = "Registrar",
            onConfirm = { viewModel.logToDiary(slotId); logSlot = null },
            onDismiss = { logSlot = null },
        )
    }
    when (dialog) {
        DayDialog.APPLY -> ApplyPlanDialog(
            plans = plans, fixedPlan = null, initialDate = date, singleDay = true,
            onApply = { id, _, mode -> viewModel.applyPlan(id, mode); dialog = null },
            onDismiss = { dialog = null },
        )
        DayDialog.COPY -> CopyDayDialog(date, onCopy = { target, mode -> viewModel.copyTo(target, mode); dialog = null }, onDismiss = { dialog = null })
        DayDialog.LOG_ALL -> ConfirmDialog(
            title = "Registrar o dia no diário?",
            text = "${state?.itemCount ?: 0} alimento(s) planejados entram no diário de ${Fmt.fullDate(date)}. O que já estiver registrado continua lá.",
            confirm = "Registrar",
            onConfirm = { viewModel.logToDiary(null); dialog = null },
            onDismiss = { dialog = null },
        )
        DayDialog.CLEAR -> ConfirmDialog(
            title = "Limpar o planejamento do dia?",
            text = "Remove os alimentos planejados de ${Fmt.fullDate(date)}. O diário não é alterado.",
            confirm = "Limpar",
            onConfirm = { viewModel.clear(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun CopyDayDialog(from: LocalDate, onCopy: (LocalDate, ApplyMode) -> Unit, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(from.plusDays(1)) }
    var mode by remember { mutableStateOf(ApplyMode.REPLACE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copiar dia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField("Copiar para", target, { target = it }, Modifier.fillMaxWidth())
                Choice(mode == ApplyMode.REPLACE, "Substituir o que estiver planejado") { mode = ApplyMode.REPLACE }
                Choice(mode == ApplyMode.APPEND, "Somar ao que já está planejado") { mode = ApplyMode.APPEND }
            }
        },
        confirmButton = { TextButton(onClick = { onCopy(target, mode) }, enabled = target != from) { Text("Copiar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
