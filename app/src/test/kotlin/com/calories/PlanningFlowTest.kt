package com.calories

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import com.calories.data.repository.ShoppingRepositoryImpl
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.ui.format.Fmt
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Planejamento de ponta a ponta no app real: criar plano → adicionar alimento → aplicar em 7 dias →
 * registrar o dia no diário → gerar lista de compras somada (7 × 150 g = 1.050 g) → histórico.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class PlanningFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Before fun setUp() = DialogLayoutIdling.install()
    @After fun tearDown() = DialogLayoutIdling.uninstall()

    @Test
    fun `plano aplicado no calendario vira diario e lista de compras`() {
        val today = LocalDate.now()
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForText("Consumido")
            rule.onNodeWithText("Planejar").performClick()
            rule.waitForText("Calendário")
            rule.onNodeWithText("Planos").performClick()
            rule.waitForText("Nenhum plano ainda")
            rule.onNodeWithContentDescription("Novo plano").performClick()
            rule.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Plano A")
            rule.onNodeWithText("Criar").performClick()

            // Editor do plano → seletor de alimentos com destino "plano".
            rule.waitForText("Nada planejado")
            rule.onNodeWithContentDescription("Adicionar alimento em Almoço").performClick()
            rule.waitForText("Adicionar em Almoço")
            rule.waitForText("Plano Plano A")
            rule.onNode(hasSetTextAction()).performTextInput("frango peito sem pele grelhado")
            rule.waitForText("Frango, peito, sem pele, grelhado")
            rule.onAllNodesWithText("Frango, peito, sem pele, grelhado").onFirst().performClick()
            rule.waitForText("Quantidade")
            val fields = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            rule.onAllNodes(hasSetTextAction())[fields.lastIndex].performTextReplacement("150")
            rule.waitForText("238,5")
            rule.onNodeWithText("Adicionar").performClick()
            rule.waitForText("adicionado", substring = true)
            rule.onNodeWithContentDescription("Voltar").performClick()
            rule.waitForText("150 g")
            rule.waitForText("239 kcal")

            // Aplicar de hoje até hoje + 6 (padrão do diálogo), todos os dias da semana.
            rule.onNodeWithContentDescription("Aplicar no calendário").performClick()
            rule.waitForText("Será aplicado em 7 dia(s).")
            rule.onNodeWithText("Aplicar").performClick()
            rule.waitForText("Plano aplicado em 7 dia(s) do calendário")
            rule.onNodeWithContentDescription("Voltar").performClick()

            // Calendário: hoje aparece com as calorias planejadas; abrir e registrar no diário.
            rule.onNodeWithText("Calendário").performClick()
            val todayCell = "${Fmt.fullDate(today)}, 239 kcal planejadas"
            rule.waitUntil(15_000) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(todayCell)).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription(todayCell).performClick()
            rule.waitForText("Planejamento")
            rule.waitForText("Frango, peito, sem pele, grelhado")
            rule.onNodeWithContentDescription("Mais opções do dia").performClick()
            rule.onNodeWithText("Registrar tudo no diário").performClick()
            rule.onNodeWithText("Registrar").performClick()
            rule.waitForText("1 alimento(s) registrados no diário")
            rule.onNodeWithContentDescription("Voltar").performClick()

            // Compras: a lista soma os 7 dias planejados.
            rule.onNodeWithText("Compras").performClick()
            rule.onNodeWithContentDescription("Gerar lista").performClick()
            rule.waitForText("Gerar lista de compras")
            rule.onNodeWithText("Gerar").performClick()
            rule.waitForText("0 de 1 comprados")
            rule.waitForText("1.050 g")
            rule.onNodeWithContentDescription("Voltar").performClick()

            // Histórico mostra o dia registrado.
            rule.onNodeWithText("Histórico").performClick()
            rule.waitForText("média kcal/dia")
            rule.waitForText("239") // média do período = o único dia registrado
        }
        withAppDatabase { db ->
            val entry = DiaryRepositoryImpl(db, Clock.systemDefaultZone()).observeMeals(today).first().flatMap { it.entries }.single()
            assertEquals(NutrientValue.Measured(238.5), entry.consumed[Nutrient.ENERGY])
            val list = ShoppingRepositoryImpl(db).observeLists().first().single()
            assertEquals(1050.0, list.items.single().quantity)
        }
    }
}
