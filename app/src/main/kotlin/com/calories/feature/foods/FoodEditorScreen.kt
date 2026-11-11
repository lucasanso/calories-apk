package com.calories.feature.foods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodCategory
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import com.calories.domain.repository.FoodRepository
import com.calories.domain.usecase.CustomFoodValidator
import com.calories.domain.usecase.SaveCustomFood
import com.calories.navigation.FoodEditorRoute
import com.calories.ui.UiEvent
import com.calories.ui.components.InfoNote
import com.calories.ui.components.NumberField
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val categoryId: Long? = null,
    val preparation: Preparation = Preparation.READY_TO_EAT,
    val referenceAmount: String = "100",
    val referenceUnit: MeasureUnit = MeasureUnit.GRAM,
    val nutrients: Map<Nutrient, String> = emptyMap(),
) {
    fun toDraft(derivedFrom: Long?) = CustomFoodDraft(
        name = name, brand = brand, categoryId = categoryId, preparation = preparation,
        referenceAmount = Fmt.parse(referenceAmount) ?: 0.0, referenceUnit = referenceUnit,
        nutrients = Nutrient.entries.associateWith { Fmt.parse(nutrients[it].orEmpty()) }, derivedFromFoodId = derivedFrom,
    )
}

@HiltViewModel
class FoodEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    private val saveCustomFood: SaveCustomFood,
) : ViewModel() {
    private val foodId: Long? = savedStateHandle.toRoute<FoodEditorRoute>().foodId.takeIf { it > 0 }
    val isNew = foodId == null
    private var derivedFrom: Long? = null
    private val _form = MutableStateFlow(FoodForm())
    val form: StateFlow<FoodForm> = _form.asStateFlow()
    val categories: StateFlow<List<FoodCategory>> = foods.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    var showErrors by mutableStateOf(false)
        private set

    init {
        foodId?.let { id ->
            viewModelScope.launch {
                val f = foods.get(id) ?: return@launch
                derivedFrom = f.derivedFromFoodId
                _form.value = FoodForm(
                    name = f.name, brand = f.brand.orEmpty(), categoryId = f.categoryId, preparation = f.preparation,
                    referenceAmount = Fmt.input(f.profile.referenceAmount), referenceUnit = f.profile.referenceUnit,
                    nutrients = Nutrient.entries.associateWith { n -> (f.profile[n] as? NutrientValue.Measured)?.amount?.let(Fmt::input).orEmpty() },
                )
            }
        }
    }

    fun update(transform: (FoodForm) -> FoodForm) = _form.update(transform)

    fun save() = viewModelScope.launch {
        showErrors = true
        val draft = _form.value.toDraft(derivedFrom)
        if (!CustomFoodValidator.validate(draft).isValid) return@launch
        runCatching { saveCustomFood(foodId, draft) }
            .onSuccess { _events.send(UiEvent.OpenFood(it)) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível salvar")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorRoute(onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: FoodEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.OpenFood -> onSaved(e.foodId)
                is UiEvent.Message -> launch { snackbar.showSnackbar(e.text) }
                else -> Unit
            }
        }
    }
    val validation = CustomFoodValidator.validate(form.toDraft(null))
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Novo alimento" else "Editar alimento") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = { TextButton(onClick = viewModel::save) { Text("Salvar") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(form.name, { v -> viewModel.update { it.copy(name = v.take(CustomFoodValidator.MAX_NAME)) } }, label = { Text("Nome *") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), isError = viewModel.showErrors && form.name.isBlank(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(form.brand, { v -> viewModel.update { it.copy(brand = v.take(60)) } }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker("Categoria", categories.firstOrNull { it.id == form.categoryId }?.name ?: "Sem categoria",
                    listOf<Pair<String, () -> Unit>>("Sem categoria" to { viewModel.update { it.copy(categoryId = null) } }) +
                        categories.map { c -> c.name to { viewModel.update { it.copy(categoryId = c.id) } } }, Modifier.weight(1f))
                Picker("Preparo", form.preparation.labelPt, Preparation.entries.map { p -> p.labelPt to { viewModel.update { it.copy(preparation = p) } } }, Modifier.weight(1f))
            }

            SectionCard {
                Text("Valores informados por", style = MaterialTheme.typography.titleMedium)
                InfoNote("Use a mesma base do rótulo (ex.: “por porção de 30 g” ou “por 100 ml”).")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    NumberField(form.referenceAmount, { v -> viewModel.update { it.copy(referenceAmount = v) } }, "Quantidade *", Modifier.weight(1f),
                        isError = viewModel.showErrors && (Fmt.parse(form.referenceAmount) ?: 0.0) <= 0.0)
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f).padding(top = 8.dp)) {
                        MeasureUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = form.referenceUnit == u, onClick = { viewModel.update { it.copy(referenceUnit = u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, MeasureUnit.entries.size)) { Text(u.symbol) }
                        }
                    }
                }
            }

            SectionCard {
                Text("Nutrientes", style = MaterialTheme.typography.titleMedium)
                InfoNote("Campos com * são obrigatórios. Deixe em branco o que o rótulo não informa: o app mostra “sem dado”, nunca zero.")
                Nutrient.entries.forEach { n ->
                    val required = n in CustomFoodValidator.REQUIRED
                    NumberField(
                        value = form.nutrients[n].orEmpty(),
                        onValueChange = { v -> viewModel.update { it.copy(nutrients = it.nutrients + (n to v)) } },
                        label = n.labelPt + if (required) " *" else "",
                        suffix = n.unit,
                        isError = viewModel.showErrors && required && Fmt.parse(form.nutrients[n].orEmpty()) == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            if (viewModel.showErrors) validation.errors.forEach { InfoNote(it, color = MaterialTheme.colorScheme.error) }
            validation.warnings.forEach { InfoNote("Atenção: $it", color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
private fun Picker(label: String, value: String, options: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, maxLines = 1) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() }) }
        }
    }
}
