package com.calories.domain.usecase

import com.calories.domain.model.ApplyMode
import com.calories.domain.model.DietPlan
import com.calories.domain.model.NewShoppingItem
import com.calories.domain.model.PlanFoodItem
import com.calories.domain.model.PlannedDayOverview
import com.calories.domain.model.PlannedItem
import com.calories.domain.model.ShoppingList
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Preparation
import com.calories.domain.repository.PlanRepository
import com.calories.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlanningUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
    private val d = LocalDate.of(2026, 11, 11)

    class FakePlans : PlanRepository {
        val plans = HashMap<Long, DietPlan>()
        val planned = ArrayList<PlannedItem>()
        private var nextId = 1L
        override fun observePlans(): Flow<List<DietPlan>> = flowOf(plans.values.toList())
        override fun observePlan(id: Long): Flow<DietPlan?> = flowOf(plans[id])
        override suspend fun getPlan(id: Long) = plans[id]
        override suspend fun createPlan(name: String, description: String?): Long = nextId++.also { plans[it] = DietPlan(it, name, description, emptyList()) }
        override suspend fun updatePlan(id: Long, name: String, description: String?) {}
        override suspend fun deletePlan(id: Long) { plans.remove(id) }
        override suspend fun addPlanItem(planId: Long, slotId: Long, foodId: Long, amount: Double): Long {
            val id = nextId++
            val p = plans.getValue(planId)
            plans[planId] = p.copy(items = p.items + PlanFoodItem(id, slotId, foodId, "Alimento $foodId", Preparation.COOKED, amount, MeasureUnit.GRAM, emptyMap(), p.items.size))
            return id
        }
        override suspend fun updatePlanItem(itemId: Long, amount: Double) {}
        override suspend fun deletePlanItem(itemId: Long) {}
        override fun observePlannedDay(date: LocalDate): Flow<List<PlannedItem>> = flowOf(planned.filter { it.date == date })
        override fun observeOverview(from: LocalDate, to: LocalDate): Flow<List<PlannedDayOverview>> = flowOf(emptyList())
        override suspend fun plannedItems(from: LocalDate, to: LocalDate) = planned.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        override suspend fun addPlannedItem(date: LocalDate, slotId: Long, foodId: Long, amount: Double, sourcePlanId: Long?): Long {
            val id = nextId++
            planned += PlannedItem(id, date, slotId, foodId, "Alimento $foodId", Preparation.COOKED, amount, MeasureUnit.GRAM, emptyMap(), sourcePlanId, planned.size)
            return id
        }
        override suspend fun updatePlannedItem(itemId: Long, amount: Double) {}
        override suspend fun deletePlannedItem(itemId: Long) {}
        override suspend fun clearPlannedDay(date: LocalDate) { planned.removeAll { it.date == date } }
        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }

    class FakeShopping : ShoppingRepository {
        var created: List<NewShoppingItem> = emptyList()
        override fun observeLists(): Flow<List<ShoppingList>> = flowOf(emptyList())
        override fun observeList(id: Long): Flow<ShoppingList?> = flowOf(null)
        override suspend fun create(name: String, from: LocalDate?, to: LocalDate?, items: List<NewShoppingItem>, now: Instant): Long { created = items; return 1 }
        override suspend fun setChecked(itemId: Long, checked: Boolean) {}
        override suspend fun updateQuantity(itemId: Long, quantity: Double?) {}
        override suspend fun addManual(listId: Long, name: String, quantity: Double?, unit: String?) {}
        override suspend fun deleteItem(itemId: Long) {}
        override suspend fun clearChecked(listId: Long) {}
        override suspend fun deleteList(id: Long) {}
    }

    @Test
    fun `aplicar plano copia os itens e REPLACE substitui o dia`() = runBlocking<Unit> {
        val plans = FakePlans()
        val id = plans.createPlan("Plano A — Treino", null)
        plans.addPlanItem(id, 2, 10, 150.0); plans.addPlanItem(id, 4, 10, 150.0)
        plans.addPlannedItem(d, 1, 99, 50.0, null)
        assertEquals(2, ApplyPlanToDates(plans)(id, listOf(d, d.plusDays(1), d), ApplyMode.REPLACE))
        assertEquals(4, plans.planned.size) // o item 99 foi substituído; 2 datas × 2 itens
        assertEquals(setOf(id), plans.planned.map { it.sourcePlanId }.toSet())
        ApplyPlanToDates(plans)(id, listOf(d), ApplyMode.APPEND)
        assertEquals(4, plans.planned.count { it.date == d })
    }

    @Test
    fun `plano vazio, sem datas ou inexistente e rejeitado`() = runBlocking<Unit> {
        val plans = FakePlans()
        val id = plans.createPlan("Vazio", null)
        assertFailsWith<DomainException> { ApplyPlanToDates(plans)(id, listOf(d), ApplyMode.REPLACE) }
        assertFailsWith<DomainException> { ApplyPlanToDates(plans)(id, emptyList(), ApplyMode.REPLACE) }
        assertFailsWith<DomainException> { ApplyPlanToDates(plans)(999, listOf(d), ApplyMode.REPLACE) }
        assertFailsWith<DomainException> { CreatePlan(plans)(" ") }
    }

    @Test
    fun `copiar dia planejado e duplicar plano`() = runBlocking<Unit> {
        val plans = FakePlans()
        plans.addPlannedItem(d, 1, 5, 100.0, null)
        plans.addPlannedItem(d, 2, 6, 200.0, null)
        assertEquals(2, CopyPlannedDay(plans)(d, d.plusDays(7), ApplyMode.REPLACE))
        assertEquals(listOf(5L, 6L), plans.planned.filter { it.date == d.plusDays(7) }.map { it.foodId })
        assertFailsWith<DomainException> { CopyPlannedDay(plans)(d.plusDays(30), d, ApplyMode.REPLACE) }

        val id = plans.createPlan("B", null); plans.addPlanItem(id, 1, 7, 30.0)
        val copy = DuplicatePlan(plans)(id)
        assertEquals("B (cópia)", plans.plans.getValue(copy).name)
        assertEquals(1, plans.plans.getValue(copy).items.size)
    }

    @Test
    fun `gerar lista de compras do periodo filtrando refeicoes`() = runBlocking<Unit> {
        val plans = FakePlans()
        plans.addPlannedItem(d, 2, 1, 150.0, null)
        plans.addPlannedItem(d, 4, 1, 150.0, null)
        plans.addPlannedItem(d.plusDays(1), 2, 1, 100.0, null)
        plans.addPlannedItem(d.plusDays(1), 1, 2, 50.0, null)
        val shopping = FakeShopping()
        GenerateShoppingList(plans, shopping, clock)(d, d.plusDays(1), slotIds = setOf(2L, 4L))
        assertEquals(listOf(400.0), shopping.created.map { it.quantity })
        assertFailsWith<DomainException> { GenerateShoppingList(plans, shopping, clock)(d.plusDays(5), d.plusDays(6)) }
        assertFailsWith<DomainException> { GenerateShoppingList(plans, shopping, clock)(d.plusDays(1), d) }
    }
}
