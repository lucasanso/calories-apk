package com.calories.data

import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.FoodRepositoryImpl
import com.calories.data.repository.GoalRepositoryImpl
import com.calories.data.repository.MealSlotRepositoryImpl
import com.calories.data.repository.MealTemplateRepositoryImpl
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientAggregator
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import com.calories.domain.usecase.AddFoodToMeal
import com.calories.domain.usecase.CopyMeal
import com.calories.domain.usecase.UpdateEntryAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoriesTest {
    private val db = TestDb.create()
    private val foods = FoodRepositoryImpl(db)
    private val diary = DiaryRepositoryImpl(db, TestDb.clock)
    private val slots = MealSlotRepositoryImpl(db)
    private val goals = GoalRepositoryImpl(db, TestDb.clock)
    private val day = LocalDate.of(2026, 11, 11)

    @Before fun seed() = runBlocking<Unit> { TestDb.realSeeder(db).ensureLibrary() }
    @After fun close() = db.close()

    private suspend fun first(query: String) = foods.search(query, FoodFilter.ALL, null).first().first()

    @Test
    fun `busca ignora acentos, aceita prefixo e sinonimos`() = runBlocking<Unit> {
        assertTrue(first("feijao carioca cozido").name.startsWith("Feijão, carioca"))
        assertEquals("TACO4:410", first("frang grel sem pele").sourceKey)
        assertTrue(first("aipim").name.startsWith("Mandioca"))
        assertEquals("TACO4:589", first("castanha do para").sourceKey)
        assertEquals("TACO4:589", first("Castanha-do-Pará").sourceKey)
        assertTrue(foods.search("carne magra", FoodFilter.ALL, null).first().any { it.sourceKey == "TACO4:377" })
        assertTrue(foods.search("xyzw", FoodFilter.ALL, null).first().isEmpty())
    }

    @Test
    fun `favoritos e recentes aparecem nos filtros e sobem na busca`() = runBlocking<Unit> {
        val arroz = first("arroz tipo 1 cozido")
        foods.setFavorite(arroz.id, true)
        assertEquals(listOf(arroz.id), foods.search("", FoodFilter.FAVORITES, null).first().map { it.id })
        foods.recordUsage(arroz.id, 120.0, TestDb.clock.instant())
        assertEquals(arroz.id, foods.search("", FoodFilter.RECENT, null).first().first().id)
        assertEquals(120.0, foods.lastAmount(arroz.id))
        assertEquals(arroz.id, first("arroz").id)
    }

    @Test
    fun `alimento da biblioteca nao pode ser alterado nem apagado pelo DAO de personalizados`() = runBlocking<Unit> {
        val frango = first("frango peito sem pele grelhado")
        val dao = db.customFoodDao()
        assertEquals(0, dao.update(frango.id, "X", null, null, "RAW", 100.0, "GRAM", 1.0, 1.0, 1.0, 1.0, null, null, null, 0))
        assertEquals(0, dao.delete(frango.id))
        assertEquals(NutrientValue.Measured(159.0), foods.get(frango.id)!!.profile[Nutrient.ENERGY])
    }

    @Test
    fun `personalizado - criar, buscar, editar e excluir ou arquivar`() = runBlocking<Unit> {
        val draft = CustomFoodDraft("Whey Marca X", "Marca X", null, Preparation.DRIED, 30.0, MeasureUnit.GRAM,
            mapOf(Nutrient.ENERGY to 120.0, Nutrient.PROTEIN to 24.0, Nutrient.CARBOHYDRATE to 3.0, Nutrient.FAT to 1.5, Nutrient.SODIUM to 50.0))
        val id = foods.createCustom(draft, TestDb.clock.instant())
        assertEquals(id, foods.search("whey marca", FoodFilter.CUSTOM, null).first().single().id)
        foods.updateCustom(id, draft.copy(name = "Whey Marca Y"), TestDb.clock.instant())
        assertEquals("Whey Marca Y", foods.get(id)!!.name)
        assertEquals(NutrientValue.NotAvailable, foods.get(id)!!.profile[Nutrient.FIBER])

        MealTemplateRepositoryImpl(db, TestDb.clock).create("Pós-treino", listOf(id to 30.0))
        assertFalse(foods.deleteCustom(id)) // usado em refeição favorita → arquivado
        assertTrue(foods.get(id)!!.isArchived)

        val other = foods.createCustom(draft.copy(name = "Outro"), TestDb.clock.instant())
        assertTrue(foods.deleteCustom(other))
        assertNull(foods.get(other))
    }

    @Test
    fun `diario - totais da view batem com o agregador e totais parciais sao contados`() = runBlocking<Unit> {
        val add = AddFoodToMeal(foods, diary, slots, TestDb.clock)
        val frango = first("frango peito sem pele grelhado") // fibra NA
        val azeite = first("azeite de oliva extra virgem") // sódio sem dado
        val arroz = first("arroz tipo 1 cozido")
        add(day, 2, frango.id, 150.0)
        add(day, 2, azeite.id, 10.0)
        add(day, 1, arroz.id, 120.0)

        val entries = diary.observeMeals(day).first().flatMap { it.entries }
        val expected = NutrientAggregator.sum(entries.map { it.consumed })
        val daily = diary.observeDailyTotals(day, day).first().single()
        assertEquals(3, daily.entryCount)
        for (n in Nutrient.entries) {
            assertEquals(expected[n].sum, daily.totals[n].sum, 1e-9, n.name)
            assertEquals(expected[n].missingCount, daily.totals[n].missingCount, n.name)
        }
        assertEquals(1, daily.totals[Nutrient.SODIUM].missingCount) // azeite sem dado de sódio
        assertEquals(159.0 * 1.5, (entries.first { it.foodId == frango.id }.consumed[Nutrient.ENERGY] as NutrientValue.Measured).amount, 1e-9)
    }

    @Test
    fun `editar quantidade, mover, apagar e desfazer, limpar e repetir refeicao`() = runBlocking<Unit> {
        val add = AddFoodToMeal(foods, diary, slots, TestDb.clock)
        val ovo = first("ovo galinha inteiro cozido")
        val id = add(day, 1, ovo.id, 50.0)
        UpdateEntryAmount(diary)(id, 100.0)
        assertEquals(ovo.profile[Nutrient.ENERGY], diary.getEntry(id)!!.consumed[Nutrient.ENERGY])

        diary.moveEntry(id, day, slots.get(4)!!)
        assertEquals("Jantar", diary.observeMeals(day).first().single { it.entries.isNotEmpty() }.name)

        val removed = diary.deleteEntry(id)!!
        assertTrue(diary.observeMeals(day).first().all { it.entries.isEmpty() })
        diary.restoreEntry(removed)
        assertEquals(removed, diary.getEntry(id))

        assertEquals(1, CopyMeal(foods, diary, slots, TestDb.clock)(day, 4, day.plusDays(1), 1))
        diary.clearMeal(day, 4)
        assertTrue(diary.observeMeals(day).first().all { it.entries.isEmpty() })
        assertEquals(1, diary.observeMeals(day.plusDays(1)).first().single().entries.size)
    }

    @Test
    fun `meta vigente por data`() = runBlocking<Unit> {
        goals.setGoal(NutritionGoal(day.minusDays(10), 2000.0, 140.0, null, null, null, GoalOrigin.MANUAL))
        goals.setGoal(NutritionGoal(day, 2200.0, 160.0, null, null, null, GoalOrigin.MANUAL))
        assertNull(goals.goalFor(day.minusDays(11)))
        assertEquals(2000.0, goals.goalFor(day.minusDays(1))?.energyKcal)
        assertEquals(2200.0, goals.observeGoalFor(day.plusDays(5)).first()?.energyKcal)
        goals.setGoal(NutritionGoal(day, 2300.0, null, null, null, null, GoalOrigin.MANUAL)) // substitui a do mesmo dia
        assertEquals(2300.0, goals.goalFor(day)?.energyKcal)
    }
}
