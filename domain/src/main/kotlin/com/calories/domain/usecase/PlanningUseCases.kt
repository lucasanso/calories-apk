package com.calories.domain.usecase

import com.calories.domain.model.ApplyMode
import com.calories.domain.model.MealSlot
import com.calories.domain.model.WeightRecord
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.nutrition.ShoppingListConsolidator
import com.calories.domain.nutrition.WeightRules
import com.calories.domain.repository.DiaryRepository
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.PlanRepository
import com.calories.domain.repository.ShoppingRepository
import com.calories.domain.repository.WeightRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class CreatePlan @Inject constructor(private val plans: PlanRepository) {
    suspend operator fun invoke(name: String, description: String? = null): Long {
        if (name.isBlank()) throw DomainException("Dê um nome ao plano")
        return plans.createPlan(name.trim().take(60), description?.trim()?.ifEmpty { null })
    }
}

/** Cria um plano a partir do que foi registrado num dia do diário ("salvar o dia como plano"). */
class CreatePlanFromDiaryDay @Inject constructor(
    private val plans: PlanRepository,
    private val diary: DiaryRepository,
    private val slots: MealSlotRepository,
) {
    suspend operator fun invoke(date: LocalDate, name: String): Long {
        if (name.isBlank()) throw DomainException("Dê um nome ao plano")
        val active = slots.allSlots()
        val items = active.flatMap { slot -> diary.mealEntries(date, slot.id).mapNotNull { e -> e.foodId?.let { Triple(slot.id, it, e.amount) } } }
        if (items.isEmpty()) throw DomainException("Esse dia não tem alimentos que possam virar plano")
        return plans.transaction {
            val id = plans.createPlan(name.trim().take(60), "Criado a partir de $date")
            items.forEach { (slotId, foodId, amount) -> plans.addPlanItem(id, slotId, foodId, amount) }
            id
        }
    }
}

class DuplicatePlan @Inject constructor(private val plans: PlanRepository) {
    suspend operator fun invoke(planId: Long): Long {
        val plan = plans.getPlan(planId) ?: throw DomainException("Plano não encontrado")
        return plans.transaction {
            val id = plans.createPlan("${plan.name} (cópia)".take(60), plan.description)
            plan.items.forEach { plans.addPlanItem(id, it.slotId, it.foodId, it.amount) }
            id
        }
    }
}

class AddFoodToPlan @Inject constructor(private val plans: PlanRepository, private val foods: FoodRepository) {
    suspend operator fun invoke(planId: Long, slotId: Long, foodId: Long, amount: Double): Long {
        NutritionCalculator.requireValidAmount(amount)
        foods.get(foodId) ?: throw DomainException("Alimento não encontrado")
        return plans.addPlanItem(planId, slotId, foodId, amount)
    }
}

class AddFoodToPlannedDay @Inject constructor(private val plans: PlanRepository, private val foods: FoodRepository) {
    suspend operator fun invoke(date: LocalDate, slotId: Long, foodId: Long, amount: Double): Long {
        NutritionCalculator.requireValidAmount(amount)
        foods.get(foodId) ?: throw DomainException("Alimento não encontrado")
        return plans.addPlannedItem(date, slotId, foodId, amount, null)
    }
}

/**
 * Aplica um plano a várias datas. Copia os itens (editar o plano depois não muda os dias
 * já planejados). REPLACE apaga antes o planejamento de cada data; APPEND acrescenta.
 */
class ApplyPlanToDates @Inject constructor(private val plans: PlanRepository) {
    suspend operator fun invoke(planId: Long, dates: List<LocalDate>, mode: ApplyMode): Int {
        val plan = plans.getPlan(planId) ?: throw DomainException("Plano não encontrado")
        if (dates.isEmpty()) throw DomainException("Escolha ao menos uma data")
        if (dates.size > 366) throw DomainException("Escolha no máximo um ano de datas")
        if (plan.items.isEmpty()) throw DomainException("O plano está vazio")
        plans.transaction {
            for (date in dates.distinct()) {
                if (mode == ApplyMode.REPLACE) plans.clearPlannedDay(date)
                plan.items.forEach { plans.addPlannedItem(date, it.slotId, it.foodId, it.amount, plan.id) }
            }
        }
        return dates.distinct().size
    }
}

class CopyPlannedDay @Inject constructor(private val plans: PlanRepository) {
    suspend operator fun invoke(from: LocalDate, to: LocalDate, mode: ApplyMode): Int {
        val items = plans.plannedItems(from, from)
        if (items.isEmpty()) throw DomainException("Nada planejado nesse dia")
        plans.transaction {
            if (mode == ApplyMode.REPLACE) plans.clearPlannedDay(to)
            items.forEach { plans.addPlannedItem(to, it.slotId, it.foodId, it.amount, it.sourcePlanId) }
        }
        return items.size
    }
}

/** Registra no diário o que foi planejado (um slot ou o dia todo), com snapshot dos valores atuais. */
class LogPlannedDay @Inject constructor(
    private val plans: PlanRepository,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val slots: MealSlotRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate, slotId: Long? = null): Int {
        val items = plans.plannedItems(date, date).filter { slotId == null || it.slotId == slotId }
        var count = 0
        for (item in items) {
            val food = foods.get(item.foodId) ?: continue
            val slot: MealSlot = slots.get(item.slotId) ?: continue
            diary.addEntry(date, slot, snapshotOf(food, item.amount, clock))
            foods.recordUsage(food.id, item.amount, clock.instant())
            count++
        }
        if (count == 0) throw DomainException("Nada planejado para registrar")
        return count
    }
}

class GenerateShoppingList @Inject constructor(
    private val plans: PlanRepository,
    private val shopping: ShoppingRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(from: LocalDate, to: LocalDate, slotIds: Set<Long>? = null): Long {
        if (to.isBefore(from)) throw DomainException("A data final é anterior à inicial")
        val items = plans.plannedItems(from, to).filter { slotIds == null || it.slotId in slotIds }
        if (items.isEmpty()) throw DomainException("Nada planejado nesse período. Planeje refeições no calendário primeiro.")
        return shopping.create("Compras ${from.dayOfMonth}/${from.monthValue} a ${to.dayOfMonth}/${to.monthValue}", from, to,
            ShoppingListConsolidator.consolidate(items), clock.instant())
    }
}

class SaveWeight @Inject constructor(private val weights: WeightRepository, private val clock: Clock) {
    suspend operator fun invoke(date: LocalDate, weightKg: Double?, note: String?) {
        val errors = WeightRules.validate(weightKg, date, LocalDate.now(clock))
        if (errors.isNotEmpty()) throw DomainException(errors.joinToString("\n"))
        weights.save(WeightRecord(date, weightKg!!, note?.trim()?.ifEmpty { null }))
    }
}

private suspend fun MealSlotRepository.allSlots(): List<MealSlot> = observeSlots(includeInactive = true).first()
