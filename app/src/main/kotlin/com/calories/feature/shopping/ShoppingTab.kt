package com.calories.feature.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.MealSlot
import com.calories.domain.model.ShoppingList
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.ShoppingRepository
import com.calories.domain.usecase.GenerateShoppingList
import com.calories.ui.UiEvent
import com.calories.ui.components.DateField
import com.calories.ui.components.EmptyState
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
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
class ShoppingViewModel @Inject constructor(
    shopping: ShoppingRepository,
    slots: MealSlotRepository,
    private val generateShoppingList: GenerateShoppingList,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    val lists: StateFlow<List<ShoppingList>?> = shopping.observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val slots: StateFlow<List<MealSlot>> = slots.observeSlots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun generate(from: LocalDate, to: LocalDate, slotIds: Set<Long>?) = viewModelScope.launch {
        runCatching { generateShoppingList(from, to, slotIds) }
            .onSuccess { _events.send(UiEvent.OpenItem(it)) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível gerar a lista")) }
    }
}

/** Aba "Compras": listas geradas do planejamento (somadas por alimento) e itens manuais. */
@Composable
fun ShoppingTab(
    generating: Boolean,
    onGeneratingChange: (Boolean) -> Unit,
    onOpenList: (Long) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.OpenItem -> onOpenList(e.id)
                is UiEvent.Message -> onMessage(e.text)
                else -> Unit
            }
        }
    }
    val list = lists ?: return
    if (list.isEmpty()) {
        EmptyState(
            "Nenhuma lista de compras",
            "Planeje refeições no calendário e gere a lista: as quantidades do mesmo alimento são somadas (150 g + 150 g + 100 g = 400 g).",
            modifier, icon = Icons.Outlined.ShoppingCart,
            action = { Button(onClick = { onGeneratingChange(true) }) { Text("Gerar lista") } },
        )
    } else {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { l -> ListCard(l) { onOpenList(l.id) } }
        }
    }
    if (generating) {
        GenerateDialog(
            today = viewModel.today, slots = slots,
            onGenerate = { from, to, ids -> viewModel.generate(from, to, ids); onGeneratingChange(false) },
            onDismiss = { onGeneratingChange(false) },
        )
    }
}

@Composable
private fun ListCard(list: ShoppingList, onClick: () -> Unit) {
    val done = list.items.count { it.isChecked }
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Text(list.name, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                list.fromDate?.let { f -> list.toDate?.let { t -> "Planejamento de ${Fmt.dayMonth(f)} a ${Fmt.dayMonth(t)}" } },
                "$done de ${list.items.size} comprados",
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (list.items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { done.toFloat() / list.items.size }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenerateDialog(today: LocalDate, slots: List<MealSlot>, onGenerate: (LocalDate, LocalDate, Set<Long>?) -> Unit, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today.plusDays(6)) }
    var selected by remember(slots) { mutableStateOf(slots.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gerar lista de compras") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    DateField("De", from, { from = it; if (to.isBefore(it)) to = it }, Modifier.weight(1f))
                    DateField("Até", to, { to = it }, Modifier.weight(1f))
                }
                Text("Refeições", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEach { s ->
                        FilterChip(selected = s.id in selected, onClick = { selected = if (s.id in selected) selected - s.id else selected + s.id }, label = { Text(s.name) })
                    }
                }
                InfoNote(
                    "Soma o que está planejado no calendário nesse período. Alimentos preparados (ex.: arroz cozido) aparecem com o peso pronto, " +
                        "porque as tabelas não trazem fatores de rendimento para converter em peso cru.",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(from, to, selected.takeIf { it.size < slots.size }) },
                enabled = !to.isBefore(from) && selected.isNotEmpty(),
            ) { Text("Gerar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
