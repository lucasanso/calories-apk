package com.calories.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

// Destinos da barra inferior
@Serializable data class TodayRoute(val epochDay: Long? = null)
@Serializable data object FoodsRoute
@Serializable data object PlanningRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

/** Onde o alimento escolhido no seletor vai parar. */
@Keep
enum class PickTarget { DIARY, PLANNED_DAY, PLAN }

// Destinos internos
/** [epochDay] vale para diário e dia planejado; [planId] só para [PickTarget.PLAN]. */
@Serializable data class FoodPickerRoute(
    val epochDay: Long,
    val slotId: Long,
    val target: PickTarget = PickTarget.DIARY,
    val planId: Long = -1L,
)
@Serializable data class FoodDetailRoute(val foodId: Long)
/** [foodId] = -1 cria um alimento novo. */
@Serializable data class FoodEditorRoute(val foodId: Long = -1L)
@Serializable data object GoalsRoute
@Serializable data object EnergyCalculatorRoute
@Serializable data object MealSlotsRoute
@Serializable data object SourcesRoute
@Serializable data object DataRoute
@Serializable data class PlannedDayRoute(val epochDay: Long)
@Serializable data class PlanEditorRoute(val planId: Long)
@Serializable data class ShoppingListRoute(val listId: Long)
