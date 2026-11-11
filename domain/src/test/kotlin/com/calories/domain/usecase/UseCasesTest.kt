package com.calories.domain.usecase

import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.DailyTotals
import com.calories.domain.model.DiaryEntry
import com.calories.domain.model.DiaryMeal
import com.calories.domain.model.Food
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.FoodOrigin
import com.calories.domain.model.FoodSource
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.MealSlot
import com.calories.domain.model.MealTemplate
import com.calories.domain.model.MealTemplateItem
import com.calories.domain.model.NewDiaryEntry
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import com.calories.domain.repository.DiaryRepository
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.GoalRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.MealTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 11)

    private fun profile(kcal: Double, protein: Double) = NutrientProfile(
        100.0, MeasureUnit.GRAM,
        mapOf(Nutrient.ENERGY to NutrientValue.Measured(kcal), Nutrient.PROTEIN to NutrientValue.Measured(protein), Nutrient.FIBER to NutrientValue.NotApplicable),
    )

    private fun food(id: Long, kcal: Double, origin: FoodOrigin = FoodOrigin.LIBRARY) = Food(
        id, origin, if (origin == FoodOrigin.LIBRARY) "TACO4:$id" else null, if (origin == FoodOrigin.LIBRARY) "TACO4" else "USER",
        "Alimento $id", null, null, emptyList(), null, Preparation.GRILLED, profile(kcal, 32.0), null, null, emptyList(), null, false,
    )

    private class Fakes {
        val foods = FakeFoods()
        val diary = FakeDiary()
        val slots = FakeSlots()
        val goals = FakeGoals()
        val templates = FakeTemplates(foods)
    }

    @Test
    fun `adicionar alimento grava snapshot e calcula o consumo`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 165.0)
        AddFoodToMeal(f.foods, f.diary, f.slots, clock)(today, 1, 1, 150.0)
        val entry = f.diary.entries.single()
        assertEquals(NutrientValue.Measured(247.5), entry.consumed[Nutrient.ENERGY])
        assertEquals(165.0, (entry.reference[Nutrient.ENERGY] as NutrientValue.Measured).amount)
        assertEquals(listOf(1L to 150.0), f.foods.usage)
    }

    @Test
    fun `editar o alimento depois nao muda o historico, e editar a quantidade usa o snapshot`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 165.0)
        val id = AddFoodToMeal(f.foods, f.diary, f.slots, clock)(today, 1, 1, 150.0)
        f.foods.items[1] = food(1, 999.0) // cadastro alterado depois do consumo
        UpdateEntryAmount(f.diary)(id, 200.0)
        assertEquals(NutrientValue.Measured(330.0), f.diary.entries.single().consumed[Nutrient.ENERGY])
    }

    @Test
    fun `remover e desfazer`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 100.0)
        val id = AddFoodToMeal(f.foods, f.diary, f.slots, clock)(today, 1, 1, 50.0)
        val removed = RemoveEntry(f.diary)(id)!!
        assertTrue(f.diary.entries.isEmpty())
        RestoreEntry(f.diary)(removed)
        assertEquals(removed, f.diary.entries.single())
    }

    @Test
    fun `repetir refeicao usa os valores atuais do alimento ou o snapshot se ele foi apagado`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 100.0); f.foods.items[2] = food(2, 200.0)
        val add = AddFoodToMeal(f.foods, f.diary, f.slots, clock)
        val yesterday = today.minusDays(1)
        add(yesterday, 1, 1, 100.0); add(yesterday, 1, 2, 100.0)
        f.foods.items[1] = food(1, 120.0)
        f.foods.items.remove(2)
        assertEquals(2, CopyMeal(f.foods, f.diary, f.slots, clock)(yesterday, 1, today, 2))
        val copied = f.diary.entries.filter { it.mealId == f.diary.mealIdFor(today, 2) }.map { (it.consumed[Nutrient.ENERGY] as NutrientValue.Measured).amount }
        assertEquals(listOf(120.0, 200.0), copied)
    }

    @Test
    fun `resumo do dia mostra refeicoes ativas vazias e soma totais`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 165.0)
        AddFoodToMeal(f.foods, f.diary, f.slots, clock)(today, 2, 1, 150.0)
        f.goals.goal = NutritionGoal(today.minusDays(10), 2000.0, 140.0, null, null, null, GoalOrigin.MANUAL)
        val summary = ObserveDaySummary(f.diary, f.slots, f.goals)(today).first()
        assertEquals(listOf("Café da manhã", "Almoço"), summary.meals.map { it.name })
        assertEquals(0, summary.meals[0].entries.size)
        assertEquals(247.5, summary.totals.energyKcal)
        assertEquals(48.0, summary.totals[Nutrient.PROTEIN].sum, 1e-9)
        assertEquals(2000.0, summary.goal?.energyKcal)
    }

    @Test
    fun `quantidade invalida nao grava nada`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 100.0)
        assertFailsWith<IllegalArgumentException> { AddFoodToMeal(f.foods, f.diary, f.slots, clock)(today, 1, 1, 0.0) }
        assertTrue(f.diary.entries.isEmpty())
    }

    @Test
    fun `alimento da biblioteca nao pode ser editado nem excluido`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 100.0)
        val draft = CustomFoodDraft("X", null, null, Preparation.RAW, 100.0, MeasureUnit.GRAM, mapOf(Nutrient.ENERGY to 1.0, Nutrient.PROTEIN to 0.0, Nutrient.CARBOHYDRATE to 0.0, Nutrient.FAT to 0.0))
        assertFailsWith<DomainException> { SaveCustomFood(f.foods, clock)(1, draft) }
        assertFailsWith<DomainException> { DeleteCustomFood(f.foods)(1) }
    }

    @Test
    fun `validacao do alimento personalizado`() {
        val ok = CustomFoodDraft("Iogurte X", "Marca", null, Preparation.READY_TO_EAT, 170.0, MeasureUnit.GRAM,
            mapOf(Nutrient.ENERGY to 100.0, Nutrient.PROTEIN to 15.0, Nutrient.CARBOHYDRATE to 6.0, Nutrient.FAT to 1.7))
        assertTrue(CustomFoodValidator.validate(ok).isValid)
        assertTrue(CustomFoodValidator.validate(ok.copy(name = " ")).errors.isNotEmpty())
        assertTrue(CustomFoodValidator.validate(ok.copy(nutrients = ok.nutrients - Nutrient.FAT)).errors.any { "Gorduras" in it })
        assertTrue(CustomFoodValidator.validate(ok.copy(nutrients = ok.nutrients + (Nutrient.SATURATED_FAT to 5.0))).errors.isNotEmpty())
        assertTrue(CustomFoodValidator.validate(ok.copy(nutrients = ok.nutrients + (Nutrient.ENERGY to 500.0))).warnings.isNotEmpty())
    }

    @Test
    fun `duplicar como personalizado preserva valores e aponta a origem`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 159.0)
        val id = DuplicateAsCustomFood(f.foods, clock)(1)
        val copy = f.foods.items.getValue(id)
        assertEquals(FoodOrigin.CUSTOM, copy.origin)
        assertEquals(1L, copy.derivedFromFoodId)
        assertEquals(NutrientValue.Measured(159.0), copy.profile[Nutrient.ENERGY])
        assertEquals(NutrientValue.Measured(0.0), copy.profile[Nutrient.FIBER]) // NA → 0
        assertEquals(NutrientValue.NotAvailable, copy.profile[Nutrient.SODIUM]) // sem dado continua sem dado
        assertEquals(NutrientValue.Measured(159.0), f.foods.items.getValue(1).profile[Nutrient.ENERGY]) // original intacto
    }

    @Test
    fun `meta vigente a partir de hoje e validada`() = runBlocking<Unit> {
        val f = Fakes()
        SetGoal(f.goals, clock)(2100.0, 150.0, null, 70.0, 30.0, GoalOrigin.MANUAL)
        assertEquals(today, f.goals.goal?.effectiveFrom)
        assertFailsWith<DomainException> { SetGoal(f.goals, clock)(100.0, null, null, null, null, GoalOrigin.MANUAL) }
        assertFailsWith<DomainException> { SetGoal(f.goals, clock)(null, null, null, null, null, GoalOrigin.MANUAL) }
    }

    @Test
    fun `refeicao favorita salva e reaplicada`() = runBlocking<Unit> {
        val f = Fakes(); f.foods.items[1] = food(1, 100.0); f.foods.items[2] = food(2, 50.0)
        val add = AddFoodToMeal(f.foods, f.diary, f.slots, clock)
        add(today, 1, 1, 80.0); add(today, 1, 2, 120.0)
        val templateId = SaveMealAsTemplate(f.diary, f.templates)(today, 1, "Café padrão")
        assertEquals(2, ApplyMealTemplate(f.templates, f.foods, f.diary, f.slots, clock)(templateId, today.plusDays(1), 1))
        assertEquals(4, f.diary.entries.size)
        assertFailsWith<IllegalArgumentException> { SaveMealAsTemplate(f.diary, f.templates)(today, 1, " ") }
    }

    // --- Fakes ---

    private class FakeFoods : FoodRepository {
        val items = HashMap<Long, Food>()
        val usage = ArrayList<Pair<Long, Double>>()
        private var nextId = 1000L
        override fun search(query: String, filter: FoodFilter, categoryId: Long?, limit: Int): Flow<List<Food>> = flowOf(items.values.toList())
        override fun observe(id: Long): Flow<Food?> = flowOf(items[id])
        override suspend fun get(id: Long): Food? = items[id]
        override fun observeCategories(): Flow<List<FoodCategory>> = flowOf(emptyList())
        override fun observeSources(): Flow<List<FoodSource>> = flowOf(emptyList())
        override suspend fun createCustom(draft: CustomFoodDraft, now: Instant): Long {
            val id = nextId++
            items[id] = Food(id, FoodOrigin.CUSTOM, null, "USER", draft.name, null, draft.brand, emptyList(), draft.categoryId, draft.preparation,
                CustomFoodValidator.toProfile(draft), null, null, emptyList(), draft.derivedFromFoodId, false)
            return id
        }
        override suspend fun updateCustom(id: Long, draft: CustomFoodDraft, now: Instant) { createCustom(draft, now) }
        override suspend fun deleteCustom(id: Long): Boolean = items.remove(id) != null
        override suspend fun setFavorite(id: Long, favorite: Boolean) {}
        override suspend fun recordUsage(id: Long, amount: Double, at: Instant) { usage += id to amount }
        override suspend fun lastAmount(id: Long): Double? = null
    }

    private class FakeDiary : DiaryRepository {
        val entries = ArrayList<DiaryEntry>()
        private val meals = HashMap<Pair<LocalDate, Long>, Long>()
        private val mealDates = HashMap<Long, Pair<LocalDate, MealSlot>>()
        private var nextId = 1L
        private val state = MutableStateFlow(0)

        fun mealIdFor(date: LocalDate, slotId: Long) = meals[date to slotId]

        override fun observeMeals(date: LocalDate): Flow<List<DiaryMeal>> = state.map {
            mealDates.filterValues { it.first == date }.map { (id, v) -> DiaryMeal(id, date, v.second.id, v.second.name, 0, entries.filter { it.mealId == id }) }
        }
        override suspend fun mealEntries(date: LocalDate, slotId: Long) = entries.filter { it.mealId == meals[date to slotId] }
        override suspend fun addEntry(date: LocalDate, slot: MealSlot, entry: NewDiaryEntry): Long {
            val mealId = meals.getOrPut(date to slot.id) { nextId++.also { mealDates[it] = date to slot } }
            val id = nextId++
            entries += DiaryEntry(id, mealId, entry.foodId, entry.foodSourceKey, entry.foodName, entry.brand, entry.preparation, entry.dataSourceCode,
                entry.amount, entry.unit, entry.reference, entry.consumed, entries.size, entry.createdAt)
            state.value++
            return id
        }
        override suspend fun getEntry(id: Long) = entries.firstOrNull { it.id == id }
        override suspend fun updateEntryAmount(id: Long, amount: Double, consumed: Map<Nutrient, NutrientValue>) {
            val i = entries.indexOfFirst { it.id == id }; entries[i] = entries[i].copy(amount = amount, consumed = consumed)
        }
        override suspend fun deleteEntry(id: Long): DiaryEntry? = getEntry(id)?.also { entries.remove(it) }
        override suspend fun restoreEntry(entry: DiaryEntry) { entries += entry }
        override suspend fun moveEntry(id: Long, date: LocalDate, slot: MealSlot) {}
        override suspend fun clearMeal(date: LocalDate, slotId: Long) { entries.removeAll { it.mealId == meals[date to slotId] } }
        override fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotals>> = flowOf(emptyList())
    }

    private class FakeSlots : MealSlotRepository {
        val slots = listOf(MealSlot(1, "Café da manhã", 420, 0, true), MealSlot(2, "Almoço", 720, 1, true), MealSlot(3, "Ceia", 1320, 2, false))
        override fun observeSlots(includeInactive: Boolean) = flowOf(slots.filter { includeInactive || it.isActive })
        override suspend fun get(id: Long) = slots.firstOrNull { it.id == id }
        override suspend fun save(slot: MealSlot): Long = slot.id
        override suspend fun reorder(idsInOrder: List<Long>) {}
    }

    private class FakeGoals : GoalRepository {
        var goal: NutritionGoal? = null
        override fun observeGoalFor(date: LocalDate) = flowOf(goal?.takeIf { !it.effectiveFrom.isAfter(date) })
        override suspend fun goalFor(date: LocalDate) = goal
        override suspend fun setGoal(goal: NutritionGoal) { this.goal = goal }
        override suspend fun goalsBetween(from: LocalDate, to: LocalDate) = listOfNotNull(goal)
    }

    private class FakeTemplates(private val foods: FakeFoods) : MealTemplateRepository {
        val templates = HashMap<Long, MealTemplate>()
        override fun observeTemplates() = flowOf(templates.values.toList())
        override suspend fun get(id: Long) = templates[id]
        override suspend fun create(name: String, items: List<Pair<Long, Double>>): Long {
            val id = templates.size + 1L
            templates[id] = MealTemplate(id, name, items.map { (foodId, amount) -> MealTemplateItem(foodId, foods.items[foodId]?.name ?: "", amount, "g", null) })
            return id
        }
        override suspend fun delete(id: Long) { templates.remove(id) }
    }

    @Test
    fun `fake sanity`() { assertNull(FakeGoals().goal) }
}
