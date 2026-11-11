package com.calories.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Calculate
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MacroEnergy
import com.calories.domain.repository.GoalRepository
import com.calories.domain.usecase.GoalValidator
import com.calories.domain.usecase.SetGoal
import com.calories.ui.UiEvent
import com.calories.ui.components.InfoNote
import com.calories.ui.components.NumberField
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
class GoalsViewModel @Inject constructor(goals: GoalRepository, private val setGoal: SetGoal, clock: Clock) : ViewModel() {
    val current: StateFlow<NutritionGoal?> = goals.observeGoalFor(LocalDate.now(clock)).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun save(energy: Double?, protein: Double?, carbs: Double?, fat: Double?, fiber: Double?) = viewModelScope.launch {
        runCatching { setGoal(energy, protein, carbs, fat, fiber, GoalOrigin.MANUAL) }
            .onSuccess { _events.send(UiEvent.Message("Meta salva. Vale a partir de hoje; dias anteriores mantêm a meta antiga.")) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível salvar")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsRoute(onBack: () -> Unit, onCalculator: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var energy by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    LaunchedEffect(current) {
        current?.let { g ->
            energy = Fmt.input(g.energyKcal); protein = Fmt.input(g.proteinG); carbs = Fmt.input(g.carbohydrateG); fat = Fmt.input(g.fatG); fiber = Fmt.input(g.fiberG)
        }
    }
    LaunchedEffect(Unit) { viewModel.events.collect { if (it is UiEvent.Message) launch { snackbar.showSnackbar(it.text) } } }

    val e = Fmt.parse(energy)
    val errors = GoalValidator.validate(e, Fmt.parse(protein), Fmt.parse(carbs), Fmt.parse(fat), Fmt.parse(fiber))
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metas nutricionais") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = { TextButton(onClick = { viewModel.save(e, Fmt.parse(protein), Fmt.parse(carbs), Fmt.parse(fat), Fmt.parse(fiber)) }, enabled = errors.isEmpty()) { Text("Salvar") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoNote(current?.let { "Meta atual vigente desde ${Fmt.fullDate(it.effectiveFrom)}${if (it.origin == GoalOrigin.ESTIMATED) " (estimada pela calculadora)" else ""}." }
                ?: "Nenhuma meta definida. Preencha os valores ou use a calculadora de estimativa.")
            SectionCard {
                NumberField(energy, { energy = it }, "Calorias *", Modifier.fillMaxWidth(), suffix = "kcal", isError = energy.isNotEmpty() && errors.any { "Calorias" in it })
                MacroField("Proteínas", protein, { protein = it }, e, MacroEnergy.KCAL_PER_G_PROTEIN)
                MacroField("Carboidratos", carbs, { carbs = it }, e, MacroEnergy.KCAL_PER_G_CARBOHYDRATE)
                MacroField("Gorduras", fat, { fat = it }, e, MacroEnergy.KCAL_PER_G_FAT)
                NumberField(fiber, { fiber = it }, "Fibras", Modifier.fillMaxWidth().padding(top = 4.dp), suffix = "g")
                val macroKcal = listOf(protein to 4.0, carbs to 4.0, fat to 9.0).sumOf { (t, f) -> (Fmt.parse(t) ?: 0.0) * f }
                if (e != null && macroKcal > 0) InfoNote("Macronutrientes somam ${Fmt.kcal(macroKcal)} kcal (convenção 4/4/9 kcal/g) de ${Fmt.kcal(e)} kcal.", Modifier.padding(top = 8.dp))
            }
            errors.filter { energy.isNotEmpty() }.forEach { InfoNote(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onCalculator, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Calculate, null); Text("  Calculadora de estimativa")
            }
            InfoNote("Metas são suas: a calculadora só sugere valores aproximados, que você pode editar.")
        }
    }
}

@Composable
private fun MacroField(label: String, value: String, onChange: (String) -> Unit, energy: Double?, kcalPerGram: Double) {
    val grams = Fmt.parse(value)
    val pct = if (grams != null && energy != null && energy > 0) " · ${Fmt.number(MacroEnergy.percentFromGrams(energy, grams, kcalPerGram), 0)}% das kcal" else ""
    NumberField(value, onChange, label, Modifier.fillMaxWidth().padding(top = 4.dp), suffix = "g", supporting = if (pct.isNotEmpty()) pct.removePrefix(" · ") else null)
}
