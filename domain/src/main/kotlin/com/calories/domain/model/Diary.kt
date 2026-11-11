package com.calories.domain.model

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientAggregator
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientTotals
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import java.time.Instant
import java.time.LocalDate

/** Refeição configurável do dia (Café da manhã, Almoço…). */
data class MealSlot(
    val id: Long,
    val name: String,
    /** Minutos desde 00:00. */
    val defaultTimeMinutes: Int?,
    val sortOrder: Int,
    val isActive: Boolean,
)

/**
 * Alimento consumido. Guarda uma cópia (snapshot) dos valores de referência usados no
 * momento do registro: editar ou apagar o alimento depois não altera o histórico.
 */
data class DiaryEntry(
    val id: Long,
    val mealId: Long,
    val foodId: Long?,
    val foodSourceKey: String?,
    val foodName: String,
    val brand: String?,
    val preparation: Preparation,
    val dataSourceCode: String,
    /** Quantidade na unidade de referência do alimento. */
    val amount: Double,
    val unit: MeasureUnit,
    val reference: NutrientProfile,
    val consumed: Map<Nutrient, NutrientValue>,
    val sortOrder: Int,
    val createdAt: Instant,
)

/** Dados para inserir um lançamento (o id e a refeição são definidos pelo repositório). */
data class NewDiaryEntry(
    val foodId: Long?,
    val foodSourceKey: String?,
    val foodName: String,
    val brand: String?,
    val preparation: Preparation,
    val dataSourceCode: String,
    val amount: Double,
    val unit: MeasureUnit,
    val reference: NutrientProfile,
    val consumed: Map<Nutrient, NutrientValue>,
    val createdAt: Instant,
)

data class DiaryMeal(
    val id: Long,
    val date: LocalDate,
    val slotId: Long?,
    val name: String,
    val sortOrder: Int,
    val entries: List<DiaryEntry>,
)

/** Refeição exibida no dia: um slot ativo (mesmo vazio) ou uma refeição registrada de slot removido. */
data class MealSummary(
    val slotId: Long?,
    val mealId: Long?,
    val name: String,
    val timeMinutes: Int?,
    val entries: List<DiaryEntry>,
) {
    val totals: NutrientTotals = NutrientAggregator.sum(entries.map { it.consumed })
}

data class DaySummary(
    val date: LocalDate,
    val meals: List<MealSummary>,
    val goal: NutritionGoal?,
) {
    /** Total do dia somado diretamente dos lançamentos (sem arredondamentos intermediários). */
    val totals: NutrientTotals = NutrientAggregator.sum(meals.flatMap { m -> m.entries.map { it.consumed } })
    val entryCount: Int get() = meals.sumOf { it.entries.size }
}

/** Totais de um dia para o histórico. */
data class DailyTotals(val date: LocalDate, val entryCount: Int, val totals: NutrientTotals, val goal: NutritionGoal?)
