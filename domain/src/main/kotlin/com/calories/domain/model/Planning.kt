package com.calories.domain.model

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientAggregator
import com.calories.domain.nutrition.NutrientTotals
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import java.time.Instant
import java.time.LocalDate

data class WeightRecord(val date: LocalDate, val weightKg: Double, val note: String?)

/**
 * Alimento dentro de um plano ou de um dia planejado. Planejamento é "vivo": os valores
 * vêm do cadastro atual do alimento (o snapshot só é gravado quando vira consumo no diário).
 */
data class PlanFoodItem(
    val id: Long,
    val slotId: Long,
    val foodId: Long,
    val foodName: String,
    val preparation: Preparation,
    val amount: Double,
    val unit: MeasureUnit,
    val consumed: Map<Nutrient, NutrientValue>,
    val sortOrder: Int,
)

/** Modelo de dia reutilizável ("Plano A — Dias de treino"). */
data class DietPlan(val id: Long, val name: String, val description: String?, val items: List<PlanFoodItem>) {
    val totals: NutrientTotals = NutrientAggregator.sum(items.map { it.consumed })
}

/** Item planejado para uma data no calendário. */
data class PlannedItem(
    val id: Long,
    val date: LocalDate,
    val slotId: Long,
    val foodId: Long,
    val foodName: String,
    val preparation: Preparation,
    val amount: Double,
    val unit: MeasureUnit,
    val consumed: Map<Nutrient, NutrientValue>,
    val sourcePlanId: Long?,
    val sortOrder: Int,
)

/** Itens de uma refeição (slot), usados tanto no editor de plano quanto no dia planejado. */
data class SlotGroup<T>(val slot: MealSlot, val items: List<T>, val totals: NutrientTotals)

/** Resumo de um dia no calendário. */
data class PlannedDayOverview(val date: LocalDate, val itemCount: Int, val energyKcal: Double, val planNames: List<String>)

enum class ApplyMode { REPLACE, APPEND }

data class ShoppingList(
    val id: Long,
    val name: String,
    val fromDate: LocalDate?,
    val toDate: LocalDate?,
    val createdAt: Instant,
    val items: List<ShoppingItem>,
)

data class ShoppingItem(
    val id: Long,
    val foodId: Long?,
    val name: String,
    /** Ex.: "Grelhado · peso do alimento pronto". */
    val detail: String?,
    val quantity: Double?,
    val unit: String?,
    val isChecked: Boolean,
    val isManual: Boolean,
    val sortOrder: Int,
)

/** Item a criar numa lista de compras. */
data class NewShoppingItem(val foodId: Long?, val name: String, val detail: String?, val quantity: Double?, val unit: String?)
