package com.calories.navigation

import kotlinx.serialization.Serializable

// Destinos da barra inferior
@Serializable data class TodayRoute(val epochDay: Long? = null)
@Serializable data object FoodsRoute
@Serializable data object PlanningRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

// Destinos internos
@Serializable data class FoodPickerRoute(val epochDay: Long, val slotId: Long)
@Serializable data class FoodDetailRoute(val foodId: Long)
/** [foodId] = -1 cria um alimento novo. */
@Serializable data class FoodEditorRoute(val foodId: Long = -1L)
@Serializable data object GoalsRoute
@Serializable data object EnergyCalculatorRoute
@Serializable data object MealSlotsRoute
@Serializable data object SourcesRoute
