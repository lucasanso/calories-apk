package com.calories.feature.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.DietPlan
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.PlannedItem
import com.calories.domain.model.SlotGroup
import com.calories.domain.nutrition.NutrientAggregator
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientTotals
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.nutrition.SlotGrouping
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.GoalRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.PlanRepository
import com.calories.domain.usecase.ApplyPlanToDates
import com.calories.domain.usecase.CopyPlannedDay
import com.calories.domain.usecase.LogPlannedDay
import com.calories.navigation.PlannedDayRoute
import com.calories.ui.UiEvent
import com.calories.ui.format.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlannedDayState(val date: LocalDate, val groups: List<SlotGroup<PlannedItem>>, val goal: NutritionGoal?) {
    val totals: NutrientTotals = NutrientAggregator.sum(groups.flatMap { g -> g.items.map { it.consumed } })
    val itemCount: Int get() = groups.sumOf { it.items.size }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannedDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: PlanRepository,
    slots: MealSlotRepository,
    goals: GoalRepository,
    private val foods: FoodRepository,
    private val applyPlanToDates: ApplyPlanToDates,
    private val copyPlannedDay: CopyPlannedDay,
    private val logPlannedDay: LogPlannedDay,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    private val _date = MutableStateFlow(LocalDate.ofEpochDay(savedStateHandle.toRoute<PlannedDayRoute>().epochDay))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val state: StateFlow<PlannedDayState?> = _date.flatMapLatest { d ->
        combine(plans.observePlannedDay(d), slots.observeSlots(includeInactive = true), goals.observeGoalFor(d)) { items, allSlots, goal ->
            PlannedDayState(d, SlotGrouping.group(allSlots, items, { it.slotId }, { it.consumed }), goal)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val planList: StateFlow<List<DietPlan>> = plans.observePlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun previousDay() = _date.update { it.minusDays(1) }
    fun nextDay() = _date.update { it.plusDays(1) }

    suspend fun profileOf(foodId: Long): NutrientProfile? = foods.get(foodId)?.profile

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível concluir")) }
    }

    fun updateAmount(itemId: Long, amount: Double) = launchSafely {
        NutritionCalculator.requireValidAmount(amount)
        plans.updatePlannedItem(itemId, amount)
    }

    fun delete(item: PlannedItem) = launchSafely {
        plans.deletePlannedItem(item.id)
        _events.send(UiEvent.Message("${item.foodName} removido", undo = {
            viewModelScope.launch { plans.addPlannedItem(item.date, item.slotId, item.foodId, item.amount, item.sourcePlanId) }
        }))
    }

    fun applyPlan(planId: Long, mode: ApplyMode) = launchSafely {
        applyPlanToDates(planId, listOf(_date.value), mode)
        _events.send(UiEvent.Message("Plano aplicado"))
    }

    fun copyTo(target: LocalDate, mode: ApplyMode) = launchSafely {
        val n = copyPlannedDay(_date.value, target, mode)
        _events.send(UiEvent.Message("$n alimento(s) copiados para ${Fmt.shortDate(target)}"))
    }

    /** Registra no diário (o dia todo ou só uma refeição). */
    fun logToDiary(slotId: Long?) = launchSafely {
        val n = logPlannedDay(_date.value, slotId)
        _events.send(UiEvent.LoggedToDiary("$n alimento(s) registrados no diário", _date.value))
    }

    fun clear() = launchSafely { plans.clearPlannedDay(_date.value) }
}
