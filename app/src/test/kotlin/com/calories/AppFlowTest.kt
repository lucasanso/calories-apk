package com.calories

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals

/** Fluxo principal de ponta a ponta no app real (Hilt, Room, Compose). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class AppFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun `adicionar 150 g de frango grelhado ao almoco atualiza o dashboard`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForText("Hoje")
            rule.waitForText("Almoço")
            rule.onNodeWithContentDescription("Adicionar alimento em Almoço").performClick()
            rule.waitForText("Adicionar em Almoço")
            rule.onNode(hasSetTextAction()).performTextInput("frang grel sem pele")
            rule.waitForText("Frango, peito, sem pele, grelhado")
            rule.onAllNodesWithText("Frango, peito, sem pele, grelhado").onFirst().performClick()
            rule.waitForText("Quantidade")
            rule.onAllNodes(hasSetTextAction()).onFirst().let { } // campo de busca continua na tela
            rule.onNodeWithText("Adicionar").assertExists()
            // Campo de quantidade é o último campo editável (dentro da folha).
            val fields = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            rule.onAllNodes(hasSetTextAction())[fields.lastIndex].performTextReplacement("150")
            rule.waitForText("238,5") // prévia: 159 kcal/100 g × 150 g
            rule.onNodeWithText("Adicionar").performClick()
            rule.waitForText("adicionado", substring = true)
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.waitForText("Consumido")
            rule.waitForText("239 kcal") // resumo arredonda 238,5 → 239 (HALF_UP)
        }
        // O lançamento ficou gravado com snapshot e valor exato (sem arredondar).
        val entry = withAppDatabase { db -> DiaryRepositoryImpl(db, Clock.systemDefaultZone()).observeMeals(LocalDate.now()).first().flatMap { it.entries }.single() }
        assertEquals(NutrientValue.Measured(238.5), entry.consumed[Nutrient.ENERGY])
        assertEquals(150.0, entry.amount)
    }
}
