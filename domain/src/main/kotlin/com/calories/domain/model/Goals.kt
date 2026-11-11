package com.calories.domain.model

import com.calories.domain.nutrition.ActivityLevel
import com.calories.domain.nutrition.BiologicalSex
import com.calories.domain.nutrition.Objective
import java.time.LocalDate

enum class GoalOrigin { MANUAL, ESTIMATED }

/** Meta vigente em [date] numa lista de metas (a de maior `effectiveFrom` ≤ data). */
fun List<NutritionGoal>.goalOn(date: LocalDate): NutritionGoal? =
    filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }

/** Meta diária vigente a partir de [effectiveFrom] (inclusive) até a próxima meta. */
data class NutritionGoal(
    val effectiveFrom: LocalDate,
    val energyKcal: Double,
    val proteinG: Double?,
    val carbohydrateG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val origin: GoalOrigin,
)

/** Dados da calculadora de estimativa, guardados só no aparelho. */
data class UserProfile(
    val sex: BiologicalSex,
    val ageYears: Int,
    val weightKg: Double,
    val heightCm: Double,
    val activity: ActivityLevel,
    val objective: Objective,
    val adjustmentPercent: Double,
    val proteinPerKg: Double,
    val fatPercent: Double,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = false)

/** Refeição favorita reutilizável. */
data class MealTemplate(val id: Long, val name: String, val items: List<MealTemplateItem>)

data class MealTemplateItem(val foodId: Long, val foodName: String, val amount: Double, val unitSymbol: String, val energyKcal: Double?)

data class LibraryStatus(val installedVersion: Int, val foodCount: Int)
