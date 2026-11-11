package com.calories.data

import com.calories.data.backup.BackupRepositoryImpl
import com.calories.data.db.CaloriesDatabase
import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.FoodRepositoryImpl
import com.calories.data.repository.GoalRepositoryImpl
import com.calories.data.repository.MealSlotRepositoryImpl
import com.calories.data.repository.MealTemplateRepositoryImpl
import com.calories.data.repository.PlanRepositoryImpl
import com.calories.data.repository.ShoppingRepositoryImpl
import com.calories.data.repository.WeightRepositoryImpl
import com.calories.domain.model.AppSettings
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.ThemeMode
import com.calories.domain.model.UserProfile
import com.calories.domain.model.WeightRecord
import com.calories.domain.nutrition.ActivityLevel
import com.calories.domain.nutrition.BiologicalSex
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.Objective
import com.calories.domain.nutrition.Preparation
import com.calories.domain.repository.SettingsRepository
import com.calories.domain.usecase.AddFoodToMeal
import com.calories.domain.usecase.AddFoodToPlannedDay
import com.calories.domain.usecase.ApplyPlanToDates
import com.calories.domain.usecase.GenerateShoppingList
import com.calories.domain.usecase.LogPlannedDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupAndPlanningTest {
    private val day = LocalDate.of(2026, 11, 11)
    private val dbs = ArrayList<CaloriesDatabase>()

    @After fun close() = dbs.forEach { it.close() }

    class MemorySettings : SettingsRepository {
        private val s = MutableStateFlow(AppSettings())
        private val p = MutableStateFlow<UserProfile?>(null)
        override val settings: Flow<AppSettings> = s
        override suspend fun setThemeMode(mode: ThemeMode) { s.value = s.value.copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) { s.value = s.value.copy(dynamicColor = enabled) }
        override val profile: Flow<UserProfile?> = p
        override suspend fun saveProfile(profile: UserProfile) { p.value = profile }
    }

    private suspend fun freshDb(): CaloriesDatabase = TestDb.create().also { dbs += it; TestDb.realSeeder(it).ensureLibrary() }

    private suspend fun CaloriesDatabase.id(q: String) = FoodRepositoryImpl(this).search(q, FoodFilter.ALL, null).first().first().id

    /** Preenche todas as tabelas do usuário. */
    private suspend fun populate(db: CaloriesDatabase, settings: MemorySettings) {
        val clock = TestDb.clock
        val foods = FoodRepositoryImpl(db)
        val diary = DiaryRepositoryImpl(db, clock)
        val slots = MealSlotRepositoryImpl(db)
        val plans = PlanRepositoryImpl(db, clock)
        val frango = db.id("frango peito sem pele grelhado")
        val arroz = db.id("arroz tipo 1 cozido")
        val whey = foods.createCustom(CustomFoodDraft("Whey X", "Marca", null, Preparation.DRIED, 30.0, MeasureUnit.GRAM,
            mapOf(Nutrient.ENERGY to 120.0, Nutrient.PROTEIN to 24.0, Nutrient.CARBOHYDRATE to 3.0, Nutrient.FAT to 1.5)), clock.instant())
        foods.setFavorite(frango, true)
        AddFoodToMeal(foods, diary, slots, clock)(day, 2, frango, 150.0)
        AddFoodToMeal(foods, diary, slots, clock)(day, 2, arroz, 120.0)
        AddFoodToMeal(foods, diary, slots, clock)(day.minusDays(1), 3, whey, 30.0)
        GoalRepositoryImpl(db, clock).setGoal(NutritionGoal(day.minusDays(10), 2100.0, 140.0, 230.0, 70.0, 30.0, GoalOrigin.MANUAL))
        WeightRepositoryImpl(db, clock).save(WeightRecord(day, 78.4, "manhã"))
        MealTemplateRepositoryImpl(db, clock).create("Pós-treino", listOf(whey to 30.0))
        val plan = plans.createPlan("Plano A — Treino", "dias de treino")
        plans.addPlanItem(plan, 2, frango, 150.0); plans.addPlanItem(plan, 4, frango, 150.0); plans.addPlanItem(plan, 3, whey, 30.0)
        ApplyPlanToDates(plans)(plan, listOf(day.plusDays(1), day.plusDays(2)), ApplyMode.REPLACE)
        GenerateShoppingList(plans, ShoppingRepositoryImpl(db), clock)(day.plusDays(1), day.plusDays(2))
        settings.setThemeMode(ThemeMode.DARK)
        settings.saveProfile(UserProfile(BiologicalSex.MALE, 34, 78.0, 176.0, ActivityLevel.MODERATE, Objective.MAINTAIN, 15.0, 1.6, 30.0))
    }

    private fun withoutTimestamp(json: String) = json.replace(Regex("\"exportedAt\": \"[^\"]+\""), "")

    /** Ids de personalizados e planos são renumerados na restauração (podem colidir com a biblioteca de outro aparelho). */
    private fun canonical(text: String): com.calories.data.backup.BackupFile {
        val f = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(com.calories.data.backup.BackupFile.serializer(), text)
        val foods = f.customFoods.mapIndexed { i, it -> it.id to (i + 1).toLong() }.toMap()
        val plans = f.plans.mapIndexed { i, it -> it.id to (i + 1).toLong() }.toMap()
        fun r(ref: com.calories.data.backup.FoodRef) = ref.copy(customId = ref.customId?.let(foods::getValue))
        return f.copy(
            exportedAt = "",
            customFoods = f.customFoods.map { it.copy(id = foods.getValue(it.id)) },
            foodUsage = f.foodUsage.map { it.copy(food = r(it.food)) },
            diary = f.diary.map { m -> m.copy(entries = m.entries.map { e -> e.copy(food = e.food?.let(::r)) }) },
            templates = f.templates.map { t -> t.copy(items = t.items.map { it.copy(food = r(it.food)) }) },
            plans = f.plans.map { p -> p.copy(id = plans.getValue(p.id), items = p.items.map { it.copy(food = r(it.food)) }) },
            plannedItems = f.plannedItems.map { it.copy(food = r(it.food), planId = it.planId?.let(plans::getValue)) },
            shoppingLists = f.shoppingLists.map { l -> l.copy(items = l.items.map { it.copy(food = it.food?.let(::r)) }) },
        )
    }

    @Test
    fun `backup de ida e volta - restaurar em outro aparelho reproduz exatamente os dados`() = runBlocking<Unit> {
        val settingsA = MemorySettings()
        val a = freshDb(); populate(a, settingsA)
        val exported = BackupRepositoryImpl(a, settingsA).exportBackup(TestDb.clock.instant())

        val settingsB = MemorySettings()
        val b = freshDb()
        AddFoodToMeal(FoodRepositoryImpl(b), DiaryRepositoryImpl(b, TestDb.clock), MealSlotRepositoryImpl(b), TestDb.clock)(day, 1, b.id("banana prata crua"), 100.0) // dado antigo que será substituído
        val result = BackupRepositoryImpl(b, settingsB).restore(exported)
        assertEquals(emptyList(), result.errors)
        assertEquals(3, result.counts["Alimentos consumidos"])

        val reExported = BackupRepositoryImpl(b, settingsB).exportBackup(TestDb.clock.instant())
        assertEquals(canonical(exported), canonical(reExported))
        assertEquals(ThemeMode.DARK, settingsB.settings.first().themeMode)
        assertEquals(34, settingsB.profile.first()?.ageYears)
        // O lançamento antigo (banana) foi substituído; os do backup mantêm o snapshot.
        val meals = DiaryRepositoryImpl(b, TestDb.clock).observeMeals(day).first()
        assertEquals(listOf(238.5, 128.0 * 1.2), meals.flatMap { it.entries }.map { (it.consumed[Nutrient.ENERGY] as com.calories.domain.nutrition.NutrientValue.Measured).amount })
    }

    @Test
    fun `arquivo invalido, de versao futura ou com alimento inexistente nao toca no banco`() = runBlocking<Unit> {
        val settings = MemorySettings()
        val db = freshDb(); populate(db, settings)
        val backup = BackupRepositoryImpl(db, settings)
        val good = backup.exportBackup(TestDb.clock.instant())
        val before = withoutTimestamp(good)

        assertFalse(backup.restore("isto não é json").isValid)
        assertTrue(backup.restore(good.replace("\"formatVersion\": 1", "\"formatVersion\": 99")).errors.single().contains("versão mais nova"))
        assertTrue(backup.restore(good.replace("TACO4:410", "TACO4:99999")).errors.any { "TACO4:99999" in it })
        assertTrue(backup.restore(good.replace("\"weightKg\": 78.4", "\"weightKg\": 7.0")).errors.any { "Peso" in it })
        assertEquals(before, withoutTimestamp(backup.exportBackup(TestDb.clock.instant())))
    }

    @Test
    fun `exportar e importar planos casando refeicoes por nome e recriando personalizados`() = runBlocking<Unit> {
        val settings = MemorySettings()
        val a = freshDb(); populate(a, settings)
        val plansFile = BackupRepositoryImpl(a, settings).exportPlans(TestDb.clock.instant())
        assertFalse("\"diary\": [\n" in plansFile && "Frango" in plansFile.substringAfter("\"diary\"").substringBefore("]"))

        val b = freshDb()
        assertEquals(1, BackupRepositoryImpl(b, MemorySettings()).importPlans(plansFile))
        val plan = PlanRepositoryImpl(b, TestDb.clock).observePlans().first().single()
        assertEquals("Plano A — Treino", plan.name)
        assertEquals(3, plan.items.size)
        assertEquals(setOf(2L, 3L, 4L), plan.items.map { it.slotId }.toSet()) // mesmas refeições padrão, casadas por nome
        assertEquals(1, FoodRepositoryImpl(b).search("", FoodFilter.CUSTOM, null).first().size) // whey recriado
        assertEquals(30.0 * 4 / 30 * 30, plan.items.first { it.foodName == "Whey X" }.amount * 4) // quantidade preservada
    }

    @Test
    fun `CSV do diario padrao e Excel pt-BR`() = runBlocking<Unit> {
        val settings = MemorySettings()
        val db = freshDb(); populate(db, settings)
        val backup = BackupRepositoryImpl(db, settings)
        val csv = backup.exportDiaryCsv(null, null, excelPtBr = false)
        assertTrue(csv.startsWith("﻿data,refeicao,alimento"))
        assertTrue(csv.contains("2026-11-11,Almoço,\"Frango, peito, sem pele, grelhado\",,Grelhado,TACO4,150,g,238.5,48,0,3.75,1.35,0,75\r\n"))
        val excel = backup.exportDiaryCsv(day, day, excelPtBr = true)
        assertTrue(excel.contains("2026-11-11;Almoço;Frango, peito, sem pele, grelhado;;Grelhado;TACO4;150;g;238,5;48;0;3,75;1,35;0;75\r\n"))
        assertFalse(excel.contains("Whey X")) // filtro de datas: o whey foi no dia anterior
    }

    @Test
    fun `planejamento com repositorios reais - calendario, registrar no diario e lista de compras`() = runBlocking<Unit> {
        val db = freshDb()
        val clock = TestDb.clock
        val plans = PlanRepositoryImpl(db, clock)
        val foods = FoodRepositoryImpl(db)
        val frango = db.id("frango peito sem pele grelhado")
        AddFoodToPlannedDay(plans, foods)(day, 2, frango, 150.0)
        AddFoodToPlannedDay(plans, foods)(day, 4, frango, 150.0)
        AddFoodToPlannedDay(plans, foods)(day.plusDays(1), 2, frango, 100.0)
        val overview = plans.observeOverview(day, day.plusDays(1)).first()
        assertEquals(477.0, overview.first { it.date == day }.energyKcal, 1e-9) // 159 × 3
        assertEquals(2, LogPlannedDay(plans, foods, DiaryRepositoryImpl(db, clock), MealSlotRepositoryImpl(db), clock)(day))
        assertEquals(477.0, DiaryRepositoryImpl(db, clock).observeDailyTotals(day, day).first().single().totals.energyKcal, 1e-9)

        val listId = GenerateShoppingList(plans, ShoppingRepositoryImpl(db), clock)(day, day.plusDays(1))
        val list = ShoppingRepositoryImpl(db).observeList(listId).first()!!
        assertEquals(400.0, list.items.single().quantity)
        assertTrue(list.items.single().detail!!.contains("peso do alimento pronto"))

        // Alimento personalizado usado no calendário é arquivado, não apagado (FK RESTRICT protegida).
        val custom = foods.createCustom(CustomFoodDraft("Marmita", null, null, Preparation.PREPARED_DISH, 100.0, MeasureUnit.GRAM,
            mapOf(Nutrient.ENERGY to 150.0, Nutrient.PROTEIN to 10.0, Nutrient.CARBOHYDRATE to 15.0, Nutrient.FAT to 5.0)), clock.instant())
        AddFoodToPlannedDay(plans, foods)(day.plusDays(3), 4, custom, 300.0)
        assertFalse(foods.deleteCustom(custom))
        assertTrue(foods.get(custom)!!.isArchived)
    }
}
