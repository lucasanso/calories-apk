package com.calories.feature.foodpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.Food
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.MealSlot
import com.calories.domain.model.MealTemplate
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.MealTemplateRepository
import com.calories.domain.usecase.AddFoodToMeal
import com.calories.domain.usecase.ApplyMealTemplate
import com.calories.feature.foods.FoodFilterRow
import com.calories.feature.foods.FoodResults
import com.calories.feature.foods.FoodSearchField
import com.calories.feature.foods.FoodSearchState
import com.calories.navigation.FoodPickerRoute
import com.calories.ui.UiEvent
import com.calories.ui.components.EmptyState
import com.calories.ui.components.QuantitySheet
import com.calories.ui.format.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FoodPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    slots: MealSlotRepository,
    templates: MealTemplateRepository,
    private val addFoodToMeal: AddFoodToMeal,
    private val applyMealTemplate: ApplyMealTemplate,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<FoodPickerRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val search = FoodSearchState(foods)
    val results: StateFlow<List<Food>?> = search.results.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<FoodCategory>> = foods.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates: StateFlow<List<MealTemplate>> = templates.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _slot = kotlinx.coroutines.flow.MutableStateFlow<MealSlot?>(null)
    val slot: StateFlow<MealSlot?> = _slot

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { viewModelScope.launch { _slot.value = slots.get(route.slotId) } }

    suspend fun lastAmount(foodId: Long): Double? = foods.lastAmount(foodId)

    fun add(food: Food, amount: Double) = viewModelScope.launch {
        runCatching { addFoodToMeal(date, route.slotId, food.id, amount) }
            .onSuccess { _events.send(UiEvent.Message("${food.name} · ${Fmt.amount(amount, food.profile.referenceUnit)} adicionado")) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível adicionar")) }
    }

    fun applyTemplate(template: MealTemplate) = viewModelScope.launch {
        runCatching { applyMealTemplate(template.id, date, route.slotId) }
            .onSuccess { _events.send(UiEvent.Message("“${template.name}” aplicada ($it alimento(s))")); _events.send(UiEvent.Close) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível aplicar")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerRoute(onBack: () -> Unit, viewModel: FoodPickerViewModel = hiltViewModel()) {
    val query by viewModel.search.query.collectAsStateWithLifecycle()
    val filter by viewModel.search.filter.collectAsStateWithLifecycle()
    val categoryId by viewModel.search.categoryId.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val slot by viewModel.slot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showTemplates by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pair<Food, Double?>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.Message -> launch { snackbar.showSnackbar(e.text) }
                UiEvent.Close -> onBack()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Adicionar em ${slot?.name ?: ""}", style = MaterialTheme.typography.titleLarge)
                        Text(Fmt.fullDate(viewModel.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FoodSearchField(query, { viewModel.search.query.value = it; showTemplates = false }, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            FoodFilterRow(
                filter = filter,
                onFilter = { viewModel.search.filter.value = it; showTemplates = false },
                categories = categories, categoryId = categoryId, onCategory = { viewModel.search.categoryId.value = it },
                extra = { FilterChip(selected = showTemplates, onClick = { showTemplates = !showTemplates }, label = { Text("Refeições favoritas") }) },
            )
            if (showTemplates) {
                if (templates.isEmpty()) {
                    EmptyState("Nenhuma refeição favorita", "No dia, abra o menu de uma refeição e escolha “Salvar como favorita”.", icon = Icons.Outlined.BookmarkBorder)
                } else {
                    LazyColumn {
                        items(templates, key = { it.id }) { t ->
                            ListItem(
                                headlineContent = { Text(t.name) },
                                supportingContent = { Text(t.items.joinToString(" · ") { "${it.foodName.substringBefore(',')} ${Fmt.number(it.amount, 1, true)} ${it.unitSymbol}" }, maxLines = 2) },
                                trailingContent = { Text("${Fmt.kcal(t.items.sumOf { it.energyKcal ?: 0.0 })} kcal") },
                                modifier = Modifier.clickable { viewModel.applyTemplate(t) },
                            )
                        }
                    }
                }
            } else {
                FoodResults(results, filter, query, onOpen = { id ->
                    val food = results?.firstOrNull { it.id == id } ?: return@FoodResults
                    scope.launch { selected = food to viewModel.lastAmount(id) }
                }, contentPadding = PaddingValues(bottom = 24.dp))
            }
        }
    }

    selected?.let { (food, last) ->
        QuantitySheet(
            title = food.name,
            subtitle = "${Fmt.sourceLabel(food.dataSourceCode)} · ${food.preparation.labelPt}",
            profile = food.profile,
            initialAmount = last,
            confirmLabel = "Adicionar",
            onConfirm = { viewModel.add(food, it); selected = null },
            onDismiss = { selected = null },
        )
    }
}
