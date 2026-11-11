package com.calories.data.repository

import com.calories.data.db.dao.FoodRow
import com.calories.data.db.entity.DiaryEntryEntity
import com.calories.data.db.entity.DiaryMealEntity
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.db.entity.NutrientColumns
import com.calories.data.db.entity.NutritionGoalEntity
import com.calories.domain.model.DiaryEntry
import com.calories.domain.model.DiaryMeal
import com.calories.domain.model.Food
import com.calories.domain.model.FoodOrigin
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.MealSlot
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import java.time.Instant
import java.time.LocalDate

internal const val ALIAS_SEPARATOR = " | "
internal const val NOTE_SEPARATOR = "\n"

private fun bit(n: Nutrient) = 1 shl n.ordinal

internal fun NutrientColumns.value(n: Nutrient): Double? = when (n) {
    Nutrient.ENERGY -> energyKcal
    Nutrient.PROTEIN -> proteinG
    Nutrient.CARBOHYDRATE -> carbohydrateG
    Nutrient.FAT -> fatG
    Nutrient.SATURATED_FAT -> saturatedFatG
    Nutrient.FIBER -> fiberG
    Nutrient.SODIUM -> sodiumMg
}

internal fun NutrientColumns.toValues(): Map<Nutrient, NutrientValue> = Nutrient.entries.associateWith { n ->
    when {
        traceMask and bit(n) != 0 -> NutrientValue.Trace
        notApplicableMask and bit(n) != 0 -> NutrientValue.NotApplicable
        else -> value(n)?.let { NutrientValue.Measured(it) } ?: NutrientValue.NotAvailable
    }
}

internal fun Map<Nutrient, NutrientValue>.toColumns(): NutrientColumns {
    fun v(n: Nutrient): Double? = when (val x = this[n] ?: NutrientValue.NotAvailable) {
        is NutrientValue.Measured -> x.amount
        NutrientValue.Trace, NutrientValue.NotApplicable -> 0.0
        NutrientValue.NotAvailable -> null
    }
    var trace = 0
    var na = 0
    for ((n, x) in this) {
        if (x == NutrientValue.Trace) trace = trace or bit(n)
        if (x == NutrientValue.NotApplicable) na = na or bit(n)
    }
    return NutrientColumns(
        v(Nutrient.ENERGY), v(Nutrient.PROTEIN), v(Nutrient.CARBOHYDRATE), v(Nutrient.FAT),
        v(Nutrient.SATURATED_FAT), v(Nutrient.FIBER), v(Nutrient.SODIUM), trace, na,
    )
}

internal fun FoodRow.toDomain(): Food = Food(
    id = food.id,
    origin = FoodOrigin.valueOf(food.origin),
    sourceKey = food.sourceKey,
    dataSourceCode = dataSourceCode,
    name = food.name,
    baseName = food.baseName,
    brand = food.brand,
    aliases = food.aliases?.split(ALIAS_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
    categoryId = food.categoryId,
    preparation = Preparation.valueOf(food.preparation),
    profile = NutrientProfile(food.referenceAmount, MeasureUnit.valueOf(food.referenceUnit), food.nutrients.toValues()),
    sourceDescription = food.sourceDescription,
    sourceGroup = food.sourceGroup,
    notes = food.notes?.split(NOTE_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
    derivedFromFoodId = food.derivedFromFoodId,
    isFavorite = isFavorite == true,
    isArchived = food.isArchived,
    isDeprecated = food.isDeprecated,
)

internal fun DiaryEntryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    mealId = mealId,
    foodId = foodId,
    foodSourceKey = foodSourceKey,
    foodName = foodName,
    brand = brand,
    preparation = Preparation.valueOf(preparation),
    dataSourceCode = dataSourceCode,
    amount = amount,
    unit = MeasureUnit.valueOf(unit),
    reference = NutrientProfile(refAmount, MeasureUnit.valueOf(unit), reference.toValues()),
    consumed = consumed.toValues(),
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun DiaryMealEntity.toDomain(entries: List<DiaryEntryEntity>) =
    DiaryMeal(id, LocalDate.ofEpochDay(date), slotId, name, sortOrder, entries.map { it.toDomain() }.sortedWith(compareBy({ it.sortOrder }, { it.id })))

internal fun MealSlotEntity.toDomain() = MealSlot(id, name, defaultTimeMinutes, sortOrder, isActive)

internal fun NutritionGoalEntity.toDomain() = NutritionGoal(
    LocalDate.ofEpochDay(effectiveFrom), energyKcal, proteinG, carbohydrateG, fatG, fiberG, GoalOrigin.valueOf(origin),
)
