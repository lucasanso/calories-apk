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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.DietPlan
import com.calories.domain.model.PlanFoodItem
import com.calories.domain.nutrition.NutrientProfile
import com.calories.ui.UiEvent
import com.calories.ui.components.InfoNote
import kotlinx.coroutines.launch

private enum class PlanDialog { RENAME, APPLY, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorRoute(
    onBack: () -> Unit,
    onAddFood: (planId: Long, slotId: Long) -> Unit,
    onOpenPlan: (Long) -> Unit,
    viewModel: PlanEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    var editing by remember { mutableStateOf<Pair<PlanFoodItem, NutrientProfile>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.Message -> launch {
                    val r = snackbar.showSnackbar(e.text, actionLabel = e.undo?.let { "Desfazer" }, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) e.undo?.invoke()
                }
                is UiEvent.OpenItem -> onOpenPlan(e.id)
                UiEvent.Close -> onBack()
                else -> Unit
            }
        }
    }
    val plan = state?.plan

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(plan?.name ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        plan?.description?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções do plano") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; dialog = PlanDialog.RENAME })
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; viewModel.duplicate() })
                            DropdownMenuItem(text = { Text("Excluir plano") }, onClick = { menu = false; dialog = PlanDialog.DELETE })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (plan != null && plan.items.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { dialog = PlanDialog.APPLY },
                icon = { Icon(Icons.Outlined.EventRepeat, contentDescription = null) },
                text = { Text("Aplicar no calendário") },
                modifier = Modifier.semantics { contentDescription = "Aplicar no calendário" },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state ?: return@Scaffold
        val p = s.plan ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "totals") { PlanTotalsCard(p.totals, s.goal, "no dia") }
            items(s.groups, key = { it.slot.id }) { g ->
                SlotGroupCard(
                    title = g.slot.name,
                    totals = g.totals,
                    rows = g.items.map { it.toRow() },
                    onAdd = { onAddFood(p.id, g.slot.id) },
                    onRowClick = { row ->
                        val item = g.items.first { it.id == row.id }
                        scope.launch { viewModel.profileOf(item.foodId)?.let { editing = item to it } }
                    },
                )
            }
            item(key = "note") {
                InfoNote("Aplicar copia o plano para as datas escolhidas: mudar o plano depois não altera os dias já planejados.")
            }
        }
    }

    editing?.let { (item, profile) ->
        PlanItemSheet(
            row = item.toRow(), profile = profile,
            onSave = { viewModel.updateAmount(item.id, it); editing = null },
            onDelete = { viewModel.deleteItem(item); editing = null },
            onDismiss = { editing = null },
        )
    }
    when (dialog) {
        PlanDialog.RENAME -> plan?.let { RenamePlanDialog(it, onSave = { n, d -> viewModel.rename(n, d); dialog = null }, onDismiss = { dialog = null }) }
        PlanDialog.APPLY -> plan?.let {
            ApplyPlanDialog(
                plans = emptyList(), fixedPlan = it, initialDate = viewModel.today, singleDay = false,
                onApply = { _, dates, mode -> viewModel.apply(dates, mode); dialog = null },
                onDismiss = { dialog = null },
            )
        }
        PlanDialog.DELETE -> ConfirmDialog(
            title = "Excluir “${plan?.name}”?",
            text = "O plano é apagado. Os dias em que ele já foi aplicado no calendário continuam planejados.",
            confirm = "Excluir", destructive = true,
            onConfirm = { viewModel.delete(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun RenamePlanDialog(plan: DietPlan, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(plan.name) }
    var description by rememberSaveable { mutableStateOf(plan.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear plano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(120) }, label = { Text("Descrição (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description) }, enabled = name.isNotBlank()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
