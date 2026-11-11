package com.calories.feature.foods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.Food
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.FoodSource
import com.calories.domain.model.MealSlot
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.usecase.AddFoodToMeal
import com.calories.domain.usecase.DeleteCustomFood
import com.calories.domain.usecase.DuplicateAsCustomFood
import com.calories.navigation.FoodDetailRoute
import com.calories.ui.UiEvent
import com.calories.ui.components.Badge
import com.calories.ui.components.InfoNote
import com.calories.ui.components.NumberField
import com.calories.ui.components.NutrientTable
import com.calories.ui.components.QuantitySheet
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
class FoodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    slots: MealSlotRepository,
    private val addFoodToMeal: AddFoodToMeal,
    private val duplicateAsCustomFood: DuplicateAsCustomFood,
    private val deleteCustomFood: DeleteCustomFood,
    private val clock: Clock,
) : ViewModel() {
    private val foodId = savedStateHandle.toRoute<FoodDetailRoute>().foodId
    val food: StateFlow<Food?> = foods.observe(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<FoodCategory>> = foods.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources: StateFlow<List<FoodSource>> = foods.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val slots: StateFlow<List<MealSlot>> = slots.observeSlots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível concluir")) }
    }

    fun toggleFavorite() = launchSafely { food.value?.let { foods.setFavorite(it.id, !it.isFavorite) } }

    fun addToMeal(slot: MealSlot, amount: Double) = launchSafely {
        addFoodToMeal(LocalDate.now(clock), slot.id, foodId, amount)
        _events.send(UiEvent.Message("Adicionado a ${slot.name} de hoje"))
    }

    fun duplicate() = launchSafely { _events.send(UiEvent.OpenFood(duplicateAsCustomFood(foodId))) }

    fun delete() = launchSafely {
        val deleted = deleteCustomFood(foodId)
        _events.send(UiEvent.Message(if (deleted) "Alimento excluído" else "Alimento arquivado: ele é usado em refeições favoritas"))
        _events.send(UiEvent.Close)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FoodDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCopy: (Long) -> Unit,
    viewModel: FoodDetailViewModel = hiltViewModel(),
) {
    val food by viewModel.food.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var addTo by remember { mutableStateOf<MealSlot?>(null) }
    var slotMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.Message -> launch { snackbar.showSnackbar(e.text) }
                is UiEvent.OpenFood -> onOpenCopy(e.foodId)
                UiEvent.Close -> onBack()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (food?.isLibrary == false) "Meu alimento" else "Alimento") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    food?.let { f ->
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(if (f.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = if (f.isFavorite) "Remover dos favoritos" else "Favoritar")
                        }
                        if (!f.isLibrary) {
                            IconButton(onClick = { onEdit(f.id) }) { Icon(Icons.Outlined.Edit, contentDescription = "Editar") }
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Excluir") }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val f = food ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(f.name, style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(Fmt.sourceLabel(f.dataSourceCode), emphasized = !f.isLibrary)
                Badge(f.preparation.labelPt)
                categories.firstOrNull { it.id == f.categoryId }?.let { Badge(it.name) }
                f.brand?.let { Badge(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box {
                    Button(onClick = { slotMenu = true }) { Text("Adicionar a uma refeição") }
                    DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                        slots.forEach { s -> DropdownMenuItem(text = { Text("${s.name} (hoje)") }, onClick = { slotMenu = false; addTo = s }) }
                    }
                }
                if (f.isLibrary) OutlinedButton(onClick = viewModel::duplicate) {
                    Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.padding(start = 4.dp)); Text("Duplicar")
                }
            }

            SectionCard {
                Text("Por ${Fmt.amount(f.profile.referenceAmount, f.profile.referenceUnit)}", style = MaterialTheme.typography.titleMedium)
                NutrientTable(f.profile.values)
            }

            PortionCalculator(f)

            if (f.notes.isNotEmpty()) SectionCard {
                Text("Observações", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                f.notes.forEach { InfoNote("• $it", color = MaterialTheme.colorScheme.onSurface) }
            }

            SectionCard {
                Text("Fonte dos dados", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                val source = sources.firstOrNull { it.code == f.dataSourceCode }
                if (f.isLibrary) {
                    f.sourceDescription?.let { InfoNote("Descrição na fonte: $it", color = MaterialTheme.colorScheme.onSurface) }
                    f.sourceKey?.let { InfoNote("Código: $it") }
                    f.sourceGroup?.let { InfoNote("Grupo na fonte: $it") }
                    source?.let { InfoNote(it.citation, Modifier.padding(top = 6.dp)) }
                    InfoNote("Alimentos da biblioteca não podem ser editados. Use “Duplicar” para criar uma versão sua.", Modifier.padding(top = 6.dp))
                } else {
                    InfoNote("Cadastrado por você (ex.: valores do rótulo).")
                    f.derivedFromFoodId?.let { InfoNote("Criado a partir de um alimento da biblioteca.") }
                }
            }
        }
    }

    addTo?.let { slot ->
        food?.let { f ->
            QuantitySheet(title = f.name, subtitle = "Adicionar a ${slot.name} de hoje", profile = f.profile, initialAmount = null,
                confirmLabel = "Adicionar", onConfirm = { viewModel.addToMeal(slot, it); addTo = null }, onDismiss = { addTo = null })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir alimento?") },
            text = { Text("Os registros já feitos no diário continuam com os valores do momento do consumo.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PortionCalculator(food: Food) {
    var text by rememberSaveable(food.id) { mutableStateOf(Fmt.input(150.0)) }
    val amount = Fmt.parse(text)?.takeIf { it > 0 && it <= NutritionCalculator.MAX_AMOUNT }
    SectionCard {
        Text("Calcular uma porção", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        NumberField(text, { text = it }, "Quantidade", suffix = food.profile.referenceUnit.symbol, modifier = Modifier.fillMaxWidth(), isError = amount == null && text.isNotEmpty())
        amount?.let { NutrientTable(NutritionCalculator.consumed(food.profile, it)) }
    }
}
