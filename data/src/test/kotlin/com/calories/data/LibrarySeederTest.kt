package com.calories.data

import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.FoodRepositoryImpl
import com.calories.data.repository.MealSlotRepositoryImpl
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.FoodOrigin
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import com.calories.domain.usecase.AddFoodToMeal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrarySeederTest {
    private val db = TestDb.create()
    private val foods = FoodRepositoryImpl(db)

    @After fun close() = db.close()

    @Test
    fun `importa a biblioteca real com 614 alimentos, refeicoes iniciais e fonte do usuario`() = runBlocking<Unit> {
        val status = TestDb.realSeeder(db).ensureLibrary()
        assertEquals(2, status.installedVersion)
        assertEquals(614, status.foodCount)
        assertEquals(listOf("Café da manhã", "Almoço", "Lanche da tarde", "Jantar", "Ceia"), MealSlotRepositoryImpl(db).observeSlots().first().map { it.name })
        assertEquals(setOf("TACO4", "USDA_SR", "USER"), foods.observeSources().first().map { it.code }.toSet())
        assertEquals(5, foods.observeCategories().first().size)

        val frango = foods.search("frango peito sem pele grelhado", FoodFilter.ALL, null).first().first()
        assertEquals("TACO4:410", frango.sourceKey)
        assertEquals(NutrientValue.Measured(159.0), frango.profile[Nutrient.ENERGY])
        assertEquals(NutrientValue.NotApplicable, frango.profile[Nutrient.FIBER])
        assertEquals(Preparation.GRILLED, frango.preparation)
    }

    @Test
    fun `importar de novo nao duplica nem muda ids`() = runBlocking<Unit> {
        val seeder = TestDb.realSeeder(db)
        seeder.ensureLibrary()
        val before = foods.search("", FoodFilter.ALL, null).first().associate { it.sourceKey to it.id }
        val status = seeder.ensureLibrary()
        TestDb.realSeeder(db).ensureLibrary()
        assertEquals(614, status.foodCount)
        assertEquals(before, foods.search("", FoodFilter.ALL, null).first().associate { it.sourceKey to it.id })
        assertEquals(5, MealSlotRepositoryImpl(db).observeSlots().first().size)
    }

    @Test
    fun `nova versao atualiza valores, depreca removidos e preserva dados do usuario`() = runBlocking<Unit> {
        var asset = TestDb.asset(1, TestDb.food("TACO4:1", "Frango A", 100.0), TestDb.food("TACO4:2", "Frango B", 200.0))
        val seeder = TestDb.seeder(db) { asset }
        seeder.ensureLibrary()
        val idA = foods.search("frango a", FoodFilter.ALL, null).first().first().id
        val idB = foods.search("frango b", FoodFilter.ALL, null).first().first().id

        // Usuário: favorito, alimento personalizado e um lançamento no diário.
        foods.setFavorite(idA, true)
        val customId = foods.createCustom(
            CustomFoodDraft("Meu iogurte", "Marca", null, Preparation.READY_TO_EAT, 170.0, MeasureUnit.GRAM,
                mapOf(Nutrient.ENERGY to 100.0, Nutrient.PROTEIN to 15.0, Nutrient.CARBOHYDRATE to 6.0, Nutrient.FAT to 1.7)),
            TestDb.clock.instant(),
        )
        val diary = DiaryRepositoryImpl(db, TestDb.clock)
        val slots = MealSlotRepositoryImpl(db)
        val day = LocalDate.of(2026, 11, 11)
        AddFoodToMeal(foods, diary, slots, TestDb.clock)(day, 1, idA, 150.0)

        // Versão 2: valor de A muda, B sai da biblioteca, C entra.
        asset = TestDb.asset(2, TestDb.food("TACO4:1", "Frango A", 110.0), TestDb.food("TACO4:3", "Frango C", 300.0))
        assertEquals(2, seeder.ensureLibrary().installedVersion)

        val a = foods.get(idA)!!
        assertEquals(NutrientValue.Measured(110.0), a.profile[Nutrient.ENERGY]) // atualizado, mesmo id
        assertTrue(a.isFavorite) // favorito preservado
        assertTrue(foods.get(idB)!!.isDeprecated) // removido vira deprecated, não é apagado
        assertTrue(foods.search("frango", FoodFilter.ALL, null).first().none { it.id == idB }) // e some da busca
        assertEquals(FoodOrigin.CUSTOM, foods.get(customId)!!.origin) // personalizado intacto
        // Histórico preserva o valor do momento do consumo (100 kcal/100 g × 150 g).
        val entry = diary.observeMeals(day).first().single().entries.single()
        assertEquals(NutrientValue.Measured(150.0), entry.consumed[Nutrient.ENERGY])
        assertEquals(NutrientValue.Measured(100.0), entry.reference[Nutrient.ENERGY])
    }
}
