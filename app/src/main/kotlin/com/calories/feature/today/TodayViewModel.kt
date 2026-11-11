package com.calories.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.DaySummary
import com.calories.domain.model.MealSlot
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.usecase.ClearMeal
import com.calories.domain.usecase.CopyMeal
import com.calories.domain.usecase.MoveEntry
import com.calories.domain.usecase.ObserveDaySummary
import com.calories.domain.usecase.RemoveEntry
import com.calories.domain.usecase.RestoreEntry
import com.calories.domain.usecase.SaveMealAsTemplate
import com.calories.domain.usecase.UpdateEntryAmount
import com.calories.navigation.TodayRoute
import com.calories.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDaySummary: ObserveDaySummary,
    slots: MealSlotRepository,
    private val updateEntryAmount: UpdateEntryAmount,
    private val removeEntry: RemoveEntry,
    private val restoreEntry: RestoreEntry,
    private val moveEntry: MoveEntry,
    private val copyMeal: CopyMeal,
    private val clearMeal: ClearMeal,
    private val saveMealAsTemplate: SaveMealAsTemplate,
    private val clock: Clock,
) : ViewModel() {

    val today: LocalDate get() = LocalDate.now(clock)

    private val _date = MutableStateFlow(savedStateHandle.toRoute<TodayRoute>().epochDay?.let(LocalDate::ofEpochDay) ?: today)
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val summary: StateFlow<DaySummary?> = _date.flatMapLatest { observeDaySummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val slots: StateFlow<List<MealSlot>> = slots.observeSlots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setDate(date: LocalDate) { _date.value = date }
    fun previousDay() { _date.value = _date.value.minusDays(1) }
    fun nextDay() { _date.value = _date.value.plusDays(1) }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível concluir")) }
    }

    fun updateAmount(entryId: Long, amount: Double) = launchSafely { updateEntryAmount(entryId, amount) }

    fun delete(entryId: Long) = launchSafely {
        val removed = removeEntry(entryId) ?: return@launchSafely
        _events.send(UiEvent.Message("${removed.foodName} removido", undo = { viewModelScope.launch { restoreEntry(removed) } }))
    }

    fun move(entryId: Long, slotId: Long) = launchSafely { moveEntry(entryId, _date.value, slotId) }

    fun repeatPreviousDay(slotId: Long) = launchSafely {
        val count = copyMeal(_date.value.minusDays(1), slotId, _date.value, slotId)
        _events.send(UiEvent.Message(if (count == 0) "Nada registrado nessa refeição no dia anterior" else "$count alimento(s) copiados do dia anterior"))
    }

    fun clear(slotId: Long) = launchSafely { clearMeal(_date.value, slotId) }

    fun saveAsTemplate(slotId: Long, name: String) = launchSafely {
        saveMealAsTemplate(_date.value, slotId, name)
        _events.send(UiEvent.Message("Refeição favorita “${name.trim()}” salva"))
    }
}
