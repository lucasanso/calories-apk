package com.calories.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.WeightRecord
import com.calories.domain.nutrition.WeightRules
import com.calories.domain.repository.WeightRepository
import com.calories.domain.usecase.SaveWeight
import com.calories.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class WeightWindow(val label: String, val days: Long?) { D30("30 dias", 30), D90("90 dias", 90), Y1("1 ano", 365), ALL("Tudo", null) }

data class WeightState(
    val window: WeightWindow,
    /** Registros do período, do mais antigo para o mais recente. */
    val records: List<WeightRecord>,
    val movingAverage: List<Double>,
    val latest: WeightRecord?,
) {
    /** Variação entre o primeiro e o último registro do período. */
    val change: Double? get() = if (records.size >= 2) records.last().weightKg - records.first().weightKg else null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weights: WeightRepository,
    private val saveWeight: SaveWeight,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    private val window = MutableStateFlow(WeightWindow.D90)

    val state: StateFlow<WeightState?> = window.flatMapLatest { w ->
        val from = w.days?.let { today.minusDays(it) } ?: LocalDate.of(1900, 1, 1)
        combine(weights.observe(from, today), weights.observeLatest()) { records, latest ->
            val sorted = records.sortedBy { it.date }
            WeightState(w, sorted, WeightRules.movingAverage(sorted.map { it.weightKg }), latest)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setWindow(w: WeightWindow) { window.value = w }

    fun save(date: LocalDate, weightKg: Double?, note: String?) = viewModelScope.launch {
        runCatching { saveWeight(date, weightKg, note) }
            .onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível salvar")) }
    }

    fun delete(record: WeightRecord) = viewModelScope.launch {
        weights.delete(record.date)
        _events.send(UiEvent.Message("Registro removido", undo = { viewModelScope.launch { weights.save(record) } }))
    }
}
