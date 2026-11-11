package com.calories.data.repository

import androidx.room.withTransaction
import com.calories.data.db.CaloriesDatabase
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.db.entity.MealTemplateEntity
import com.calories.data.db.entity.MealTemplateItemEntity
import com.calories.data.db.entity.NutritionGoalEntity
import com.calories.domain.model.MealSlot
import com.calories.domain.model.MealTemplate
import com.calories.domain.model.MealTemplateItem
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.repository.GoalRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.MealTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class MealSlotRepositoryImpl @Inject constructor(private val db: CaloriesDatabase) : MealSlotRepository {
    private val dao = db.mealSlotDao()

    override fun observeSlots(includeInactive: Boolean): Flow<List<MealSlot>> =
        dao.observeAll().map { list -> list.map { it.toDomain() }.filter { includeInactive || it.isActive } }

    override suspend fun get(id: Long): MealSlot? = dao.get(id)?.toDomain()

    override suspend fun save(slot: MealSlot): Long {
        require(slot.name.isNotBlank()) { "Dê um nome à refeição" }
        val entity = MealSlotEntity(slot.id, slot.name.trim(), slot.defaultTimeMinutes, slot.sortOrder, slot.isActive)
        return if (slot.id == 0L) dao.insert(entity) else slot.id.also { dao.update(entity) }
    }

    override suspend fun reorder(idsInOrder: List<Long>) = db.withTransaction {
        idsInOrder.forEachIndexed { i, id -> dao.setOrder(id, i) }
    }
}

class GoalRepositoryImpl @Inject constructor(private val db: CaloriesDatabase, private val clock: Clock) : GoalRepository {
    private val dao = db.goalDao()

    override fun observeGoalFor(date: LocalDate): Flow<NutritionGoal?> = dao.observeFor(date.toEpochDay()).map { it?.toDomain() }

    override suspend fun goalFor(date: LocalDate): NutritionGoal? = dao.goalFor(date.toEpochDay())?.toDomain()

    override suspend fun setGoal(goal: NutritionGoal) = dao.upsert(
        NutritionGoalEntity(
            effectiveFrom = goal.effectiveFrom.toEpochDay(), energyKcal = goal.energyKcal, proteinG = goal.proteinG,
            carbohydrateG = goal.carbohydrateG, fatG = goal.fatG, fiberG = goal.fiberG, origin = goal.origin.name, createdAt = clock.millis(),
        ),
    )

    override suspend fun goalsBetween(from: LocalDate, to: LocalDate): List<NutritionGoal> = dao.goalsUntil(to.toEpochDay()).map { it.toDomain() }
}

class MealTemplateRepositoryImpl @Inject constructor(private val db: CaloriesDatabase, private val clock: Clock) : MealTemplateRepository {
    private val dao = db.mealTemplateDao()

    override fun observeTemplates(): Flow<List<MealTemplate>> = combine(dao.observeTemplates(), dao.observeItems()) { templates, items ->
        val byTemplate = items.groupBy { it.templateId }
        templates.map { t -> MealTemplate(t.id, t.name, byTemplate[t.id].orEmpty().map { it.toItem() }) }
    }

    override suspend fun get(id: Long): MealTemplate? = dao.get(id)?.let { t -> MealTemplate(t.id, t.name, dao.items(id).map { it.toItem() }) }

    override suspend fun create(name: String, items: List<Pair<Long, Double>>): Long = db.withTransaction {
        val id = dao.insert(MealTemplateEntity(name = name, createdAt = clock.millis()))
        dao.insertItems(items.mapIndexed { i, (foodId, amount) -> MealTemplateItemEntity(templateId = id, foodId = foodId, amount = amount, sortOrder = i) })
        id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun com.calories.data.db.dao.TemplateItemRow.toItem() = MealTemplateItem(
        foodId, foodName, amount, MeasureUnit.valueOf(referenceUnit).symbol,
        energyKcal?.let { NutritionCalculator.scale(it, amount, referenceAmount) },
    )
}
