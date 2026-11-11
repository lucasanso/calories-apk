package com.calories.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.MealSlot
import com.calories.domain.repository.MealSlotRepository
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MealSlotsViewModel @Inject constructor(private val repo: MealSlotRepository) : ViewModel() {
    val slots: StateFlow<List<MealSlot>> = repo.observeSlots(includeInactive = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(slot: MealSlot) = viewModelScope.launch { runCatching { repo.save(slot) } }
    fun move(index: Int, delta: Int) = viewModelScope.launch {
        val list = slots.value.toMutableList()
        val target = index + delta
        if (target !in list.indices) return@launch
        list.add(target, list.removeAt(index))
        repo.reorder(list.map { it.id })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSlotsRoute(onBack: () -> Unit, viewModel: MealSlotsViewModel = hiltViewModel()) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MealSlot?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Refeições do dia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = MealSlot(0, "", 15 * 60, slots.size, true) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nova refeição") },
                modifier = Modifier.semantics { contentDescription = "Nova refeição" })
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { InfoNote("Desativar uma refeição a esconde dos próximos dias; o que já foi registrado continua no histórico.") }
            itemsIndexed(slots, key = { _, s -> s.id }) { i, slot ->
                SectionCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { editing = slot }.padding(vertical = 8.dp)) {
                            Text(slot.name, style = MaterialTheme.typography.titleMedium, color = if (slot.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.time(slot.defaultTimeMinutes).ifEmpty { "Sem horário" } + if (!slot.isActive) " · desativada" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Mover para cima") }
                        IconButton(onClick = { viewModel.move(i, 1) }, enabled = i < slots.lastIndex) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Mover para baixo") }
                        Switch(checked = slot.isActive, onCheckedChange = { viewModel.save(slot.copy(isActive = it)) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    }
    editing?.let { slot ->
        var name by remember(slot) { mutableStateOf(slot.name) }
        var time by remember(slot) { mutableStateOf(Fmt.time(slot.defaultTimeMinutes)) }
        val minutes = Regex("^(\\d{1,2}):(\\d{2})$").find(time)?.destructured?.let { (h, m) -> h.toInt() * 60 + m.toInt() }?.takeIf { it in 0 until 24 * 60 }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (slot.id == 0L) "Nova refeição" else "Editar refeição") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nome") }, singleLine = true)
                    OutlinedTextField(time, { time = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, label = { Text("Horário (hh:mm)") }, singleLine = true,
                        isError = time.isNotEmpty() && minutes == null)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && (time.isEmpty() || minutes != null), onClick = {
                    viewModel.save(slot.copy(name = name, defaultTimeMinutes = minutes)); editing = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
}
