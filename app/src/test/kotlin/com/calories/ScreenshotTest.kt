package com.calories

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.FoodRepositoryImpl
import com.calories.data.repository.GoalRepositoryImpl
import com.calories.data.repository.MealSlotRepositoryImpl
import com.calories.data.repository.MealTemplateRepositoryImpl
import com.calories.data.repository.PlanRepositoryImpl
import com.calories.data.repository.ShoppingRepositoryImpl
import com.calories.data.repository.WeightRepositoryImpl
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.WeightRecord
import com.calories.domain.usecase.AddFoodToPlan
import com.calories.domain.usecase.ApplyPlanToDates
import com.calories.domain.usecase.CreatePlan
import com.calories.domain.usecase.GenerateShoppingList
import com.calories.ui.format.Fmt
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.Preparation
import com.calories.domain.usecase.AddFoodToMeal
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.LocalDate

/**
 * Capturas de tela do app real com dados de exemplo (o app começa vazio).
 * ./gradlew :app:testDebugUnitTest --tests "*ScreenshotTest*" -Pscreenshots  → app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val out = File("build/screenshots")

    @Before fun enabled() { assumeTrue(System.getProperty("calories.screenshots") == "true"); DialogLayoutIdling.install() }
    @After fun tearDown() = DialogLayoutIdling.uninstall()

    @Test
    fun light() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido"); rule.waitForText("Pão, trigo, francês"); rule.waitForIdle()
            sc.saveScreenshot(out, "01-hoje")
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Resumo nutricional do dia"))
            rule.onNodeWithText("Resumo nutricional do dia").performClick(); rule.waitForIdle()
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sódio")); rule.waitForIdle()
            sc.saveScreenshot(out, "02-hoje-resumo")
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Almoço"))
            rule.onNodeWithContentDescription("Adicionar alimento em Jantar").performClick()
            rule.waitForText("Adicionar em Jantar")
            rule.onNode(hasSetTextAction()).performTextInput("frango")
            rule.waitForText("Frango, peito, sem pele, grelhado"); rule.waitForIdle()
            sc.saveScreenshot(out, "03-busca")
        }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido")
            rule.onNodeWithText("Alimentos").performClick(); rule.waitForText("alimento(s)", substring = true); rule.waitForIdle()
            sc.saveScreenshot(out, "04-alimentos")
            rule.onNode(hasSetTextAction()).performTextInput("frango peito sem pele grelhado")
            rule.waitForText("Frango, peito, sem pele, grelhado")
            rule.onAllNodesWithText("Frango, peito, sem pele, grelhado").onFirst().performClick()
            rule.waitForText("Calcular uma porção"); rule.waitForIdle()
            sc.saveScreenshot(out, "05-detalhe")
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithContentDescription("Novo alimento").performClick(); rule.waitForText("Valores informados por"); rule.waitForIdle()
            sc.saveScreenshot(out, "06-novo-alimento")
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithText("Ajustes").performClick(); rule.waitForText("Metas nutricionais"); rule.waitForIdle()
            sc.saveScreenshot(out, "07-ajustes")
            rule.onNodeWithText("Metas nutricionais").performClick(); rule.waitForText("Calculadora de estimativa", substring = true); rule.waitForIdle()
            sc.saveScreenshot(out, "08-metas")
            rule.onNodeWithText("Calculadora de estimativa", substring = true).performClick(); rule.waitForText("Aproximação, não prescrição")
            rule.onNode(hasSetTextAction() and hasText("Idade", substring = true)).performTextInput("34")
            rule.onNode(hasSetTextAction() and hasText("Peso", substring = true)).performTextInput("78")
            rule.onNode(hasSetTextAction() and hasText("Altura", substring = true)).performTextInput("176")
            rule.onNodeWithText("Masculino").performClick()
            rule.waitForText("Resultado estimado"); rule.waitForIdle()
            rule.onNodeWithText("Usar como meta (editável)").performScrollTo(); rule.waitForIdle()
            sc.saveScreenshot(out, "09-calculadora")
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithText("Histórico").performClick(); rule.waitForText("média kcal/dia"); rule.waitForIdle()
            sc.saveScreenshot(out, "10-historico")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-night-xhdpi")
    fun dark() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido"); rule.waitForText("Pão, trigo, francês"); rule.waitForIdle()
            sc.saveScreenshot(out, "11-hoje-escuro")
            rule.onNodeWithText("Alimentos").performClick(); rule.waitForText("alimento(s)", substring = true); rule.waitForIdle()
            sc.saveScreenshot(out, "12-alimentos-escuro")
        }
    }

    @Test
    fun planning() {
        seed()
        val today = LocalDate.now()
        seedPlanning(today)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido")
            rule.onNodeWithText("Histórico").performClick(); rule.waitForText("média kcal/dia")
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Proporção da energia", substring = true)); rule.waitForIdle()
            sc.saveScreenshot(out, "13-historico-macros")
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Semana"))
            rule.onNodeWithText("Mês").performClick(); rule.waitForText(Fmt.monthYear(java.time.YearMonth.from(today))); rule.waitForIdle()
            sc.saveScreenshot(out, "14-historico-mes")
            rule.onNodeWithText("Peso").performClick(); rule.waitForText("Peso atual"); rule.waitForText("Média de 7 registros"); rule.waitForIdle()
            sc.saveScreenshot(out, "15-peso")
            rule.onNodeWithText("Planejar").performClick(); rule.waitForText("dia(s) planejados", substring = true); rule.waitForIdle()
            sc.saveScreenshot(out, "16-calendario")
            rule.onNodeWithContentDescription(Fmt.fullDate(today.plusDays(1)), substring = true).performClick()
            rule.waitForText("planejadas", substring = true); rule.waitForText("Frango, peito, sem pele, grelhado"); rule.waitForIdle()
            sc.saveScreenshot(out, "17-dia-planejado")
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithText("Planos").performClick(); rule.waitForText("Plano B — descanso"); rule.waitForIdle()
            sc.saveScreenshot(out, "18-planos")
            rule.onNodeWithText("Plano A — treino").performClick(); rule.waitForText("no dia", substring = true); rule.waitForIdle()
            sc.saveScreenshot(out, "19-plano")
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithText("Compras").performClick(); rule.waitForText("comprados", substring = true)
            rule.onAllNodesWithText("Compras ", substring = true).onFirst().performClick()
            rule.waitForText("Feijão, carioca, cozido"); rule.waitForIdle()
            sc.saveScreenshot(out, "20-lista-de-compras")
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.onNodeWithText("Ajustes").performClick(); rule.onNodeWithText("Dados e backup").performClick(); rule.waitForText("Levar para outro aparelho"); rule.waitForIdle()
            sc.saveScreenshot(out, "21-dados-backup")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-night-xhdpi")
    fun planningDark() {
        seed()
        seedPlanning(LocalDate.now())
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido")
            rule.onNodeWithText("Planejar").performClick(); rule.waitForText("dia(s) planejados", substring = true); rule.waitForIdle()
            sc.saveScreenshot(out, "22-calendario-escuro")
            rule.onNodeWithText("Histórico").performClick(); rule.waitForText("média kcal/dia"); rule.waitForIdle()
            sc.saveScreenshot(out, "23-historico-escuro")
        }
    }

    /** Peso dos últimos 60 dias, dois planos aplicados na próxima semana e uma lista de compras. */
    private fun seedPlanning(today: LocalDate) = withAppDatabase { db ->
        val clock = Clock.systemDefaultZone()
        val foods = FoodRepositoryImpl(db)
        val plans = PlanRepositoryImpl(db, clock)
        suspend fun id(q: String) = foods.search(q, FoodFilter.ALL, null).first().first().id
        val weights = WeightRepositoryImpl(db, clock)
        val noise = listOf(0.3, -0.2, 0.1, 0.4, -0.3, 0.0, 0.2, -0.1, 0.3, -0.2, 0.1, 0.0, -0.3, 0.2, 0.1, -0.1, 0.0, 0.2, -0.2, 0.1)
        noise.forEachIndexed { i, n -> weights.save(WeightRecord(today.minusDays(57L - 3 * i), 80.4 - i * 0.14 + n, null)) }

        val create = CreatePlan(plans)
        val add = AddFoodToPlan(plans, foods)
        val a = create("Plano A — treino", "Mais carboidrato antes do treino")
        listOf(
            1L to listOf("pao trigo frances" to 50.0, "ovo galinha inteiro cozido" to 100.0, "banana prata crua" to 100.0),
            2L to listOf("arroz tipo 1 cozido" to 200.0, "feijao carioca cozido" to 120.0, "frango peito sem pele grelhado" to 150.0, "alface crespa crua" to 30.0),
            3L to listOf("iogurte natural" to 170.0, "aveia flocos crua" to 30.0),
            4L to listOf("batata doce cozida" to 200.0, "carne bovina patinho sem gordura grelhado" to 130.0, "brocolis cozido" to 80.0),
        ).forEach { (slot, items) -> items.forEach { (q, g) -> add(a, slot, id(q), g) } }
        val b = create("Plano B — descanso", null)
        listOf(
            1L to listOf("ovo galinha inteiro cozido" to 100.0, "mamao formosa cru" to 150.0),
            2L to listOf("arroz tipo 1 cozido" to 120.0, "feijao carioca cozido" to 100.0, "frango peito sem pele grelhado" to 150.0),
            4L to listOf("tilapia calor seco" to 150.0, "abobrinha italiana cozida" to 100.0),
        ).forEach { (slot, items) -> items.forEach { (q, g) -> add(b, slot, id(q), g) } }
        val apply = ApplyPlanToDates(plans)
        val week = (1L..10L).map { today.plusDays(it) }
        apply(a, week.filter { it.dayOfWeek.value in setOf(1, 3, 5) }, ApplyMode.REPLACE)
        apply(b, week.filter { it.dayOfWeek.value !in setOf(1, 3, 5) }, ApplyMode.REPLACE)
        GenerateShoppingList(plans, ShoppingRepositoryImpl(db), clock)(today.plusDays(1), today.plusDays(7))
        Unit
    }

    @Test
    fun icon() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = androidx.core.content.ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher)!!
        val bmp = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 432, 432)
        d.draw(android.graphics.Canvas(bmp))
        File(out.apply { mkdirs() }, "00-icone.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed() = withAppDatabase { db ->
        val clock = Clock.systemDefaultZone()
        val foods = FoodRepositoryImpl(db)
        val diary = DiaryRepositoryImpl(db, clock)
        val slots = MealSlotRepositoryImpl(db)
        val add = AddFoodToMeal(foods, diary, slots, clock)
        suspend fun id(q: String) = foods.search(q, FoodFilter.ALL, null).first().first().id
        val today = LocalDate.now()
        GoalRepositoryImpl(db, clock).setGoal(NutritionGoal(today.minusDays(30), 2100.0, 140.0, 230.0, 70.0, 30.0, GoalOrigin.MANUAL))

        val day = listOf(
            1L to listOf("pao trigo frances" to 50.0, "ovo galinha inteiro cozido" to 100.0, "cafe infusao" to 150.0, "leite integral fluido" to 200.0),
            2L to listOf("arroz tipo 1 cozido" to 150.0, "feijao carioca cozido" to 100.0, "frango peito sem pele grelhado" to 150.0, "alface crespa crua" to 30.0, "azeite oliva extra virgem" to 8.0),
            3L to listOf("banana prata crua" to 100.0, "iogurte natural" to 170.0),
        )
        for ((slot, items) in day) for ((q, g) in items) add(today, slot, id(q), g)
        for (d in 1L..12L) {
            add(today.minusDays(d), 1, id("pao trigo frances"), 50.0 * (1 + d % 3))
            add(today.minusDays(d), 2, id("arroz tipo 1 cozido"), 150.0 + 10 * (d % 4))
            add(today.minusDays(d), 2, id("feijao carioca cozido"), 100.0)
            add(today.minusDays(d), 2, id("carne bovina patinho sem gordura grelhado"), 120.0 + 20 * (d % 3))
            add(today.minusDays(d), 4, id("batata doce cozida"), 150.0 + 50 * (d % 2))
            if (d % 4 == 0L) add(today.minusDays(d), 3, id("chocolate ao leite"), 60.0)
        }
        foods.setFavorite(id("frango peito sem pele grelhado"), true)
        foods.setFavorite(id("arroz tipo 1 cozido"), true)
        val whey = foods.createCustom(
            CustomFoodDraft("Whey protein Marca X", "Marca X", null, Preparation.DRIED, 30.0, MeasureUnit.GRAM,
                mapOf(Nutrient.ENERGY to 120.0, Nutrient.PROTEIN to 24.0, Nutrient.CARBOHYDRATE to 3.0, Nutrient.FAT to 1.5, Nutrient.SODIUM to 60.0)),
            clock.instant(),
        )
        MealTemplateRepositoryImpl(db, clock).create("Pós-treino", listOf(whey to 30.0, id("banana prata crua") to 100.0))
        Unit
    }
}
