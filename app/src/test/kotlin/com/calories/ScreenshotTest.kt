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
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.Preparation
import com.calories.domain.usecase.AddFoodToMeal
import kotlinx.coroutines.flow.first
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

    @Before fun enabled() = assumeTrue(System.getProperty("calories.screenshots") == "true")

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
            rule.onNodeWithText("Histórico").performClick(); rule.waitForText("Ontem"); rule.waitForIdle()
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
        for (d in 1L..3L) {
            add(today.minusDays(d), 1, id("pao trigo frances"), 50.0 * d)
            add(today.minusDays(d), 2, id("arroz tipo 1 cozido"), 150.0)
            add(today.minusDays(d), 2, id("carne bovina patinho sem gordura grelhado"), 120.0 + 20 * d)
            add(today.minusDays(d), 4, id("batata doce cozida"), 200.0)
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
