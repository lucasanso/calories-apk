package com.calories.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.UserProfile
import com.calories.domain.nutrition.ActivityLevel
import com.calories.domain.nutrition.BiologicalSex
import com.calories.domain.nutrition.EnergyEstimate
import com.calories.domain.nutrition.EnergyEstimator
import com.calories.domain.nutrition.EnergyInput
import com.calories.domain.nutrition.Objective
import com.calories.domain.repository.SettingsRepository
import com.calories.domain.usecase.SetGoal
import com.calories.ui.UiEvent
import com.calories.ui.components.InfoNote
import com.calories.ui.components.NumberField
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
import javax.inject.Inject

@HiltViewModel
class EnergyCalculatorViewModel @Inject constructor(private val settings: SettingsRepository, private val setGoal: SetGoal) : ViewModel() {
    val profile: StateFlow<UserProfile?> = settings.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun apply(input: EnergyInput, estimate: EnergyEstimate) = viewModelScope.launch {
        settings.saveProfile(UserProfile(input.sex, input.ageYears, input.weightKg, input.heightCm, input.activity, input.objective, input.adjustmentPercent, input.proteinPerKg, input.fatPercent))
        runCatching { setGoal(estimate.targetKcal, estimate.proteinG, estimate.carbohydrateG, estimate.fatG, estimate.fiberG, GoalOrigin.ESTIMATED) }
            .onSuccess { _events.send(UiEvent.Close) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível aplicar")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyCalculatorRoute(onBack: () -> Unit, viewModel: EnergyCalculatorViewModel = hiltViewModel()) {
    val saved by viewModel.profile.collectAsStateWithLifecycle()
    var sex by remember { mutableStateOf(BiologicalSex.FEMALE) }
    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(ActivityLevel.MODERATE) }
    var objective by remember { mutableStateOf(Objective.MAINTAIN) }
    var adjustment by remember { mutableStateOf("15") }
    var proteinPerKg by remember { mutableStateOf("1,6") }
    var fatPercent by remember { mutableStateOf("30") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(saved) {
        val p = saved
        if (p != null && !loaded) {
            loaded = true
            sex = p.sex; age = p.ageYears.toString(); weight = Fmt.input(p.weightKg); height = Fmt.input(p.heightCm)
            activity = p.activity; objective = p.objective; adjustment = Fmt.input(p.adjustmentPercent); proteinPerKg = Fmt.input(p.proteinPerKg); fatPercent = Fmt.input(p.fatPercent)
        }
    }
    LaunchedEffect(Unit) { viewModel.events.collect { if (it is UiEvent.Close) onBack() } }

    val input = run {
        val a = age.toIntOrNull(); val w = Fmt.parse(weight); val h = Fmt.parse(height)
        if (a == null || w == null || h == null) null
        else EnergyInput(sex, a, w, h, activity, objective, Fmt.parse(adjustment) ?: -1.0, Fmt.parse(proteinPerKg) ?: -1.0, Fmt.parse(fatPercent) ?: -1.0)
    }
    val errors = input?.let(EnergyEstimator::validate).orEmpty()
    val estimate = input?.takeIf { errors.isEmpty() }?.let(EnergyEstimator::estimate)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Estimativa de necessidades") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } })
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Text("Aproximação, não prescrição", style = MaterialTheme.typography.titleMedium)
                InfoNote(
                    "Usa a equação de Mifflin-St Jeor (1990) e fatores de atividade convencionais. O resultado é uma estimativa populacional " +
                        "e todos os valores podem ser editados depois. Em caso de condição de saúde, gestação, uso de medicamentos ou objetivos agressivos, procure um nutricionista.",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            SectionCard {
                Text("Sexo biológico (exigido pela equação)", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    listOf(BiologicalSex.FEMALE to "Feminino", BiologicalSex.MALE to "Masculino").forEachIndexed { i, (s, label) ->
                        SegmentedButton(selected = sex == s, onClick = { sex = s }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(age, { age = it.filter(Char::isDigit) }, "Idade", Modifier.weight(1f), suffix = "anos")
                    NumberField(weight, { weight = it }, "Peso", Modifier.weight(1f), suffix = "kg")
                    NumberField(height, { height = it }, "Altura", Modifier.weight(1f), suffix = "cm")
                }
            }
            SectionCard {
                Text("Nível de atividade", style = MaterialTheme.typography.labelLarge)
                ActivityLevel.entries.forEach { level ->
                    Row(Modifier.fillMaxWidth().selectable(activity == level, role = Role.RadioButton) { activity = level }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = activity == level, onClick = null)
                        Text("${level.labelPt} (×${Fmt.number(level.factor, 3, true)})", Modifier.padding(start = 12.dp))
                    }
                }
            }
            SectionCard {
                Text("Objetivo", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Objective.entries.forEachIndexed { i, o ->
                        SegmentedButton(selected = objective == o, onClick = { objective = o }, shape = SegmentedButtonDefaults.itemShape(i, Objective.entries.size)) {
                            Text(when (o) { Objective.MAINTAIN -> "Manter"; Objective.LOSE -> "Perder"; Objective.GAIN -> "Ganhar" })
                        }
                    }
                }
                if (objective != Objective.MAINTAIN) NumberField(adjustment, { adjustment = it }, if (objective == Objective.LOSE) "Redução sobre o gasto" else "Aumento sobre o gasto", Modifier.fillMaxWidth(), suffix = "%")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    NumberField(proteinPerKg, { proteinPerKg = it }, "Proteína", Modifier.weight(1f), suffix = "g/kg")
                    NumberField(fatPercent, { fatPercent = it }, "Gordura", Modifier.weight(1f), suffix = "% kcal")
                }
            }
            errors.forEach { InfoNote(it, color = MaterialTheme.colorScheme.error) }
            estimate?.let { est ->
                SectionCard {
                    Text("Resultado estimado", style = MaterialTheme.typography.titleMedium)
                    ResultLine("Taxa metabólica basal", "${Fmt.kcal(est.bmrKcal)} kcal")
                    ResultLine("Gasto total estimado", "${Fmt.kcal(est.totalExpenditureKcal)} kcal")
                    ResultLine("Meta sugerida", "${Fmt.kcal(est.targetKcal)} kcal")
                    ResultLine("Proteínas", "${Fmt.number(est.proteinG, 0)} g")
                    ResultLine("Gorduras", "${Fmt.number(est.fatG, 0)} g")
                    ResultLine("Carboidratos", "${Fmt.number(est.carbohydrateG, 0)} g")
                    ResultLine("Fibras (14 g/1.000 kcal, DRI/IOM 2005)", "${Fmt.number(est.fiberG, 0)} g")
                    est.warnings.forEach { InfoNote(it, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error) }
                }
                Button(onClick = { viewModel.apply(input!!, est) }, modifier = Modifier.fillMaxWidth()) { Text("Usar como meta (editável)") }
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
    }
}
