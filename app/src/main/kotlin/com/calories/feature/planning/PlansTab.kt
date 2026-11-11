package com.calories.feature.planning

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
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.DietPlan
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.repository.PlanRepository
import com.calories.domain.usecase.CreatePlan
import com.calories.domain.usecase.CreatePlanFromDiaryDay
import com.calories.ui.UiEvent
import com.calories.ui.components.DateField
import com.calories.ui.components.EmptyState
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
import com.calories.ui.theme.TabularNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PlansViewModel @Inject constructor(
    plans: PlanRepository,
    private val createPlan: CreatePlan,
    private val createPlanFromDiaryDay: CreatePlanFromDiaryDay,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    val plans: StateFlow<List<DietPlan>?> = plans.observePlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun create(name: String, description: String, fromDiary: LocalDate?) = viewModelScope.launch {
        runCatching { if (fromDiary != null) createPlanFromDiaryDay(fromDiary, name) else createPlan(name, description) }
            .onSuccess { _events.send(UiEvent.OpenItem(it)) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível criar o plano")) }
    }
}

/** Aba "Planos": modelos de dia reutilizáveis (Plano A, B, C…). */
@Composable
fun PlansTab(
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.OpenItem -> onOpenPlan(e.id)
                is UiEvent.Message -> onMessage(e.text)
                else -> Unit
            }
        }
    }
    val list = plans ?: return
    if (list.isEmpty()) {
        EmptyState(
            "Nenhum plano ainda",
            "Monte modelos de dia (ex.: Plano A — treino, Plano B — descanso) e aplique no calendário quantas vezes quiser.",
            modifier, icon = Icons.Outlined.EventNote,
            action = { Button(onClick = { onCreatingChange(true) }) { Text("Criar plano") } },
        )
    } else {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { p -> PlanCard(p) { onOpenPlan(p.id) } }
        }
    }
    if (creating) {
        CreatePlanDialog(viewModel.today, onCreate = { name, desc, from -> viewModel.create(name, desc, from); onCreatingChange(false) }, onDismiss = { onCreatingChange(false) })
    }
}

@Composable
private fun PlanCard(plan: DietPlan, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium)
                plan.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
            }
            Text("${Fmt.total(Nutrient.ENERGY, plan.totals[Nutrient.ENERGY], withUnit = false)} kcal", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
        }
        Spacer(Modifier.height(4.dp))
        val t = plan.totals
        Text(
            "${plan.items.size} alimento(s) · P ${Fmt.total(Nutrient.PROTEIN, t[Nutrient.PROTEIN])} · C ${Fmt.total(Nutrient.CARBOHYDRATE, t[Nutrient.CARBOHYDRATE])} · G ${Fmt.total(Nutrient.FAT, t[Nutrient.FAT])}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreatePlanDialog(today: LocalDate, onCreate: (String, String, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var fromDiary by rememberSaveable { mutableStateOf(false) }
    var date by remember { mutableStateOf(today) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo plano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome") }, placeholder = { Text("Plano A — dias de treino") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!fromDiary) OutlinedTextField(description, { description = it.take(120) }, label = { Text("Descrição (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { fromDiary = !fromDiary }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fromDiary, onCheckedChange = null)
                    Text("Copiar os alimentos de um dia do diário", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (fromDiary) {
                    DateField("Dia do diário", date, { date = it }, Modifier.fillMaxWidth(), maxDate = today)
                    InfoNote("O plano recebe os alimentos e quantidades registrados nesse dia, em cada refeição.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, description, date.takeIf { fromDiary }) }, enabled = name.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
