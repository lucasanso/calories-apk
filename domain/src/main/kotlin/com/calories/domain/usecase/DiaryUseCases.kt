package com.calories.domain.usecase

import com.calories.domain.model.DaySummary
import com.calories.domain.model.DiaryEntry
import com.calories.domain.model.Food
import com.calories.domain.model.MealSlot
import com.calories.domain.model.MealSummary
import com.calories.domain.model.NewDiaryEntry
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.repository.DiaryRepository
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.GoalRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.MealTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class DomainException(message: String) : Exception(message)

/** Resumo do dia: todas as refeições ativas (mesmo vazias), totais e meta vigente. */
class ObserveDaySummary @Inject constructor(
    private val diary: DiaryRepository,
    private val slots: MealSlotRepository,
    private val goals: GoalRepository,
) {
    operator fun invoke(date: LocalDate): Flow<DaySummary> =
        combine(diary.observeMeals(date), slots.observeSlots(includeInactive = true), goals.observeGoalFor(date)) { meals, allSlots, goal ->
            val bySlot = meals.groupBy { it.slotId }
            val active = allSlots.filter { it.isActive }.sortedBy { it.sortOrder }.map { slot ->
                val entries = bySlot[slot.id].orEmpty().flatMap { it.entries }.sortedBy { it.sortOrder }
                MealSummary(slot.id, bySlot[slot.id]?.firstOrNull()?.id, slot.name, slot.defaultTimeMinutes, entries)
            }
            val activeIds = active.mapNotNull { it.slotId }.toSet()
            // Refeições registradas em slots desativados ou removidos continuam visíveis no dia.
            val orphan = meals.filter { it.slotId == null || it.slotId !in activeIds }
                .filter { it.entries.isNotEmpty() }
                .map { MealSummary(it.slotId, it.id, it.name, null, it.entries.sortedBy { e -> e.sortOrder }) }
            DaySummary(date, active + orphan, goal)
        }
}

/** Monta o lançamento com snapshot dos valores atuais do alimento. */
internal fun snapshotOf(food: Food, amount: Double, clock: Clock): NewDiaryEntry {
    NutritionCalculator.requireValidAmount(amount)
    return NewDiaryEntry(
        foodId = food.id,
        foodSourceKey = food.sourceKey,
        foodName = food.name,
        brand = food.brand,
        preparation = food.preparation,
        dataSourceCode = food.dataSourceCode,
        amount = amount,
        unit = food.profile.referenceUnit,
        reference = food.profile,
        consumed = NutritionCalculator.consumed(food.profile, amount),
        createdAt = clock.instant(),
    )
}

class AddFoodToMeal @Inject constructor(
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val slots: MealSlotRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate, slotId: Long, foodId: Long, amount: Double): Long {
        val food = foods.get(foodId) ?: throw DomainException("Alimento não encontrado")
        val slot = slots.get(slotId) ?: throw DomainException("Refeição não encontrada")
        val id = diary.addEntry(date, slot, snapshotOf(food, amount, clock))
        foods.recordUsage(foodId, amount, clock.instant())
        return id
    }
}

/** Edita a quantidade recalculando a partir do snapshot do lançamento (nunca do alimento atual). */
class UpdateEntryAmount @Inject constructor(private val diary: DiaryRepository) {
    suspend operator fun invoke(entryId: Long, amount: Double) {
        val entry = diary.getEntry(entryId) ?: throw DomainException("Lançamento não encontrado")
        diary.updateEntryAmount(entryId, amount, NutritionCalculator.consumed(entry.reference, amount))
    }
}

class RemoveEntry @Inject constructor(private val diary: DiaryRepository) {
    suspend operator fun invoke(entryId: Long): DiaryEntry? = diary.deleteEntry(entryId)
}

class RestoreEntry @Inject constructor(private val diary: DiaryRepository) {
    suspend operator fun invoke(entry: DiaryEntry) = diary.restoreEntry(entry)
}

class MoveEntry @Inject constructor(private val diary: DiaryRepository, private val slots: MealSlotRepository) {
    suspend operator fun invoke(entryId: Long, date: LocalDate, slotId: Long) {
        val slot = slots.get(slotId) ?: throw DomainException("Refeição não encontrada")
        diary.moveEntry(entryId, date, slot)
    }
}

/**
 * Repete uma refeição de outro dia. É um consumo novo: usa os valores atuais do alimento;
 * se o alimento não existe mais, reaproveita o snapshot do lançamento original.
 */
class CopyMeal @Inject constructor(
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val slots: MealSlotRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(fromDate: LocalDate, fromSlotId: Long, toDate: LocalDate, toSlotId: Long): Int {
        val target = slots.get(toSlotId) ?: throw DomainException("Refeição não encontrada")
        val source = diary.mealEntries(fromDate, fromSlotId)
        for (entry in source) {
            val food = entry.foodId?.let { foods.get(it) }?.takeUnless { it.isDeprecated }
            val newEntry = if (food != null) snapshotOf(food, entry.amount, clock) else copySnapshot(entry)
            diary.addEntry(toDate, target, newEntry)
        }
        return source.size
    }

    private fun copySnapshot(entry: DiaryEntry) = NewDiaryEntry(
        foodId = entry.foodId, foodSourceKey = entry.foodSourceKey, foodName = entry.foodName, brand = entry.brand,
        preparation = entry.preparation, dataSourceCode = entry.dataSourceCode, amount = entry.amount, unit = entry.unit,
        reference = entry.reference, consumed = entry.consumed, createdAt = clock.instant(),
    )
}

class ClearMeal @Inject constructor(private val diary: DiaryRepository) {
    suspend operator fun invoke(date: LocalDate, slotId: Long) = diary.clearMeal(date, slotId)
}

class SaveMealAsTemplate @Inject constructor(
    private val diary: DiaryRepository,
    private val templates: MealTemplateRepository,
) {
    suspend operator fun invoke(date: LocalDate, slotId: Long, name: String): Long {
        require(name.isNotBlank()) { "Dê um nome à refeição favorita" }
        val items = diary.mealEntries(date, slotId).mapNotNull { e -> e.foodId?.let { it to e.amount } }
        if (items.isEmpty()) throw DomainException("A refeição não tem alimentos que possam ser reutilizados")
        return templates.create(name.trim(), items)
    }
}

class ApplyMealTemplate @Inject constructor(
    private val templates: MealTemplateRepository,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val slots: MealSlotRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(templateId: Long, date: LocalDate, slotId: Long): Int {
        val template = templates.get(templateId) ?: throw DomainException("Refeição favorita não encontrada")
        val slot: MealSlot = slots.get(slotId) ?: throw DomainException("Refeição não encontrada")
        var added = 0
        for (item in template.items) {
            val food = foods.get(item.foodId) ?: continue
            diary.addEntry(date, slot, snapshotOf(food, item.amount, clock))
            added++
        }
        return added
    }
}
