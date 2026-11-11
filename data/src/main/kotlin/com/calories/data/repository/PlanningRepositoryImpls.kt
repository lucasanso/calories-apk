package com.calories.data.repository

import androidx.room.withTransaction
import com.calories.data.db.CaloriesDatabase
import com.calories.data.db.dao.PlanFoodRow
import com.calories.data.db.entity.DietPlanEntity
import com.calories.data.db.entity.DietPlanItemEntity
import com.calories.data.db.entity.PlannedItemEntity
import com.calories.data.db.entity.ShoppingListEntity
import com.calories.data.db.entity.ShoppingListItemEntity
import com.calories.data.db.entity.WeightRecordEntity
import com.calories.domain.model.DietPlan
import com.calories.domain.model.NewShoppingItem
import com.calories.domain.model.PlanFoodItem
import com.calories.domain.model.PlannedDayOverview
import com.calories.domain.model.PlannedItem
import com.calories.domain.model.ShoppingItem
import com.calories.domain.model.ShoppingList
import com.calories.domain.model.WeightRecord
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.nutrition.Preparation
import com.calories.domain.repository.PlanRepository
import com.calories.domain.repository.ShoppingRepository
import com.calories.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class WeightRepositoryImpl @Inject constructor(private val db: CaloriesDatabase, private val clock: Clock) : WeightRepository {
    private val dao = db.weightDao()

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<WeightRecord>> =
        dao.observe(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { WeightRecord(LocalDate.ofEpochDay(it.date), it.weightKg, it.note) } }

    override fun observeLatest(): Flow<WeightRecord?> = dao.observeLatest().map { it?.let { r -> WeightRecord(LocalDate.ofEpochDay(r.date), r.weightKg, r.note) } }

    override suspend fun save(record: WeightRecord) =
        dao.upsert(WeightRecordEntity(date = record.date.toEpochDay(), weightKg = record.weightKg, note = record.note, createdAt = clock.millis()))

    override suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())
}

/** Valores calculados com o cadastro atual do alimento (planejamento não é snapshot). */
private fun PlanFoodRow.profile() = NutrientProfile(referenceAmount, MeasureUnit.valueOf(referenceUnit), nutrients.toValues())

private fun PlanFoodRow.toPlanItem() = PlanFoodItem(
    id, slotId, foodId, foodName, Preparation.valueOf(preparation), amount, MeasureUnit.valueOf(referenceUnit),
    NutritionCalculator.consumed(profile(), amount), sortOrder,
)

private fun PlanFoodRow.toPlanned() = PlannedItem(
    id, LocalDate.ofEpochDay(date!!), slotId, foodId, foodName, Preparation.valueOf(preparation), amount, MeasureUnit.valueOf(referenceUnit),
    NutritionCalculator.consumed(profile(), amount), sourcePlanId, sortOrder,
)

class PlanRepositoryImpl @Inject constructor(private val db: CaloriesDatabase, private val clock: Clock) : PlanRepository {
    private val dao = db.planDao()

    override fun observePlans(): Flow<List<DietPlan>> = combine(dao.observePlans(), dao.observeAllPlanItems()) { plans, items ->
        val byPlan = items.groupBy { it.ownerId }
        plans.map { p -> DietPlan(p.id, p.name, p.description, byPlan[p.id].orEmpty().map { it.toPlanItem() }) }
    }

    override fun observePlan(id: Long): Flow<DietPlan?> = combine(dao.observePlan(id), dao.observePlanItems(id)) { p, items ->
        p?.let { DietPlan(it.id, it.name, it.description, items.map { row -> row.toPlanItem() }) }
    }

    override suspend fun getPlan(id: Long): DietPlan? = dao.getPlan(id)?.let { DietPlan(it.id, it.name, it.description, dao.planItems(id).map { r -> r.toPlanItem() }) }

    override suspend fun createPlan(name: String, description: String?): Long =
        dao.insertPlan(DietPlanEntity(name = name, description = description, createdAt = clock.millis(), updatedAt = clock.millis()))

    override suspend fun updatePlan(id: Long, name: String, description: String?) {
        val p = dao.getPlan(id) ?: return
        dao.updatePlan(p.copy(name = name, description = description, updatedAt = clock.millis()))
    }

    override suspend fun deletePlan(id: Long) = dao.deletePlan(id)

    override suspend fun addPlanItem(planId: Long, slotId: Long, foodId: Long, amount: Double): Long =
        dao.insertPlanItem(DietPlanItemEntity(planId = planId, slotId = slotId, foodId = foodId, amount = amount, sortOrder = dao.nextPlanOrder(planId)))

    override suspend fun updatePlanItem(itemId: Long, amount: Double) { NutritionCalculator.requireValidAmount(amount); dao.updatePlanItem(itemId, amount) }

    override suspend fun deletePlanItem(itemId: Long) = dao.deletePlanItem(itemId)

    override fun observePlannedDay(date: LocalDate): Flow<List<PlannedItem>> =
        dao.observePlanned(date.toEpochDay(), date.toEpochDay()).map { rows -> rows.map { it.toPlanned() } }

    override fun observeOverview(from: LocalDate, to: LocalDate): Flow<List<PlannedDayOverview>> =
        combine(dao.observePlanned(from.toEpochDay(), to.toEpochDay()), dao.observePlans()) { rows, plans ->
            val names = plans.associate { it.id to it.name }
            rows.groupBy { it.date!! }.map { (day, items) ->
                PlannedDayOverview(
                    LocalDate.ofEpochDay(day), items.size,
                    items.sumOf { r -> r.nutrients.energyKcal?.let { NutritionCalculator.scale(it, r.amount, r.referenceAmount) } ?: 0.0 },
                    items.mapNotNull { it.sourcePlanId?.let(names::get) }.distinct(),
                )
            }
        }

    override suspend fun plannedItems(from: LocalDate, to: LocalDate): List<PlannedItem> =
        dao.planned(from.toEpochDay(), to.toEpochDay()).map { it.toPlanned() }

    override suspend fun addPlannedItem(date: LocalDate, slotId: Long, foodId: Long, amount: Double, sourcePlanId: Long?): Long =
        dao.insertPlanned(PlannedItemEntity(date = date.toEpochDay(), slotId = slotId, foodId = foodId, amount = amount, sourcePlanId = sourcePlanId, sortOrder = dao.nextPlannedOrder(date.toEpochDay())))

    override suspend fun updatePlannedItem(itemId: Long, amount: Double) { NutritionCalculator.requireValidAmount(amount); dao.updatePlanned(itemId, amount) }

    override suspend fun deletePlannedItem(itemId: Long) = dao.deletePlanned(itemId)

    override suspend fun clearPlannedDay(date: LocalDate) = dao.clearDay(date.toEpochDay())

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
}

class ShoppingRepositoryImpl @Inject constructor(private val db: CaloriesDatabase) : ShoppingRepository {
    private val dao = db.shoppingDao()

    override fun observeLists(): Flow<List<ShoppingList>> = combine(dao.observeLists(), dao.observeAllItems()) { lists, items ->
        val byList = items.groupBy { it.listId }
        lists.map { l ->
            ShoppingList(
                l.id, l.name, l.fromDate?.let(LocalDate::ofEpochDay), l.toDate?.let(LocalDate::ofEpochDay), Instant.ofEpochMilli(l.createdAt),
                byList[l.id].orEmpty().map { ShoppingItem(it.id, it.foodId, it.name, it.detail, it.quantity, it.unit, it.isChecked, it.isManual, it.sortOrder) },
            )
        }
    }

    override fun observeList(id: Long): Flow<ShoppingList?> = observeLists().map { lists -> lists.firstOrNull { it.id == id } }

    override suspend fun create(name: String, from: LocalDate?, to: LocalDate?, items: List<NewShoppingItem>, now: Instant): Long = db.withTransaction {
        val id = dao.insertList(ShoppingListEntity(name = name, fromDate = from?.toEpochDay(), toDate = to?.toEpochDay(), createdAt = now.toEpochMilli()))
        dao.insertItems(items.mapIndexed { i, it -> ShoppingListItemEntity(listId = id, foodId = it.foodId, name = it.name, detail = it.detail, quantity = it.quantity, unit = it.unit, isChecked = false, isManual = false, sortOrder = i) })
        id
    }

    override suspend fun setChecked(itemId: Long, checked: Boolean) = dao.setChecked(itemId, checked)

    override suspend fun updateQuantity(itemId: Long, quantity: Double?) = dao.updateQuantity(itemId, quantity)

    override suspend fun addManual(listId: Long, name: String, quantity: Double?, unit: String?) {
        require(name.isNotBlank()) { "Informe o item" }
        dao.insertItems(listOf(ShoppingListItemEntity(listId = listId, foodId = null, name = name.trim(), detail = null, quantity = quantity, unit = unit?.trim()?.ifEmpty { null }, isChecked = false, isManual = true, sortOrder = dao.nextOrder(listId))))
    }

    override suspend fun deleteItem(itemId: Long) = dao.deleteItem(itemId)

    override suspend fun clearChecked(listId: Long) = dao.clearChecked(listId)

    override suspend fun deleteList(id: Long) = dao.deleteList(id)
}
