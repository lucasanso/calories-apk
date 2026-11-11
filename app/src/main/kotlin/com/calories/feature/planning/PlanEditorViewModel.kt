package com.calories.feature.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.DietPlan
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.PlanFoodItem
import com.calories.domain.model.SlotGroup
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.nutrition.SlotGrouping
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.GoalRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.PlanRepository
import com.calories.domain.usecase.ApplyPlanToDates
import com.calories.domain.usecase.DomainException
import com.calories.domain.usecase.DuplicatePlan
import com.calories.navigation.PlanEditorRoute
import com.calories.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlanEditorState(val plan: DietPlan?, val groups: List<SlotGroup<PlanFoodItem>>, val goal: NutritionGoal?)

@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: PlanRepository,
    slots: MealSlotRepository,
    goals: GoalRepository,
    private val foods: FoodRepository,
    private val duplicatePlan: DuplicatePlan,
    private val applyPlanToDates: ApplyPlanToDates,
    clock: Clock,
) : ViewModel() {
    val planId: Long = savedStateHandle.toRoute<PlanEditorRoute>().planId
    val today: LocalDate = LocalDate.now(clock)

    /** `plan == null` depois de excluído. */
    val state: StateFlow<PlanEditorState?> = combine(plans.observePlan(planId), slots.observeSlots(includeInactive = true), goals.observeGoalFor(today)) { plan, allSlots, goal ->
        PlanEditorState(plan, plan?.let { p -> SlotGrouping.group(allSlots, p.items, { it.slotId }, { it.consumed }) }.orEmpty(), goal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    suspend fun profileOf(foodId: Long): NutrientProfile? = foods.get(foodId)?.profile

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível concluir")) }
    }

    fun rename(name: String, description: String) = launchSafely {
        if (name.isBlank()) throw DomainException("Dê um nome ao plano")
        plans.updatePlan(planId, name.trim().take(60), description.trim().ifEmpty { null })
    }

    fun delete() = launchSafely {
        plans.deletePlan(planId)
        _events.send(UiEvent.Close)
    }

    fun duplicate() = launchSafely { _events.send(UiEvent.OpenItem(duplicatePlan(planId))) }

    fun apply(dates: List<LocalDate>, mode: ApplyMode) = launchSafely {
        val n = applyPlanToDates(planId, dates, mode)
        _events.send(UiEvent.Message("Plano aplicado em $n dia(s) do calendário"))
    }

    fun updateAmount(itemId: Long, amount: Double) = launchSafely {
        NutritionCalculator.requireValidAmount(amount)
        plans.updatePlanItem(itemId, amount)
    }

    fun deleteItem(item: PlanFoodItem) = launchSafely {
        plans.deletePlanItem(item.id)
        _events.send(UiEvent.Message("${item.foodName} removido", undo = { viewModelScope.launch { plans.addPlanItem(planId, item.slotId, item.foodId, item.amount) } }))
    }
}
