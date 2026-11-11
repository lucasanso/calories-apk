package com.calories

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.FoodRepositoryImpl
import com.calories.data.repository.MealSlotRepositoryImpl
import com.calories.data.repository.WeightRepositoryImpl
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.WeightRecord
import com.calories.domain.usecase.AddFoodToMeal
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tela "Dados e backup" de ponta a ponta: o seletor de arquivos do sistema é simulado
 * (Robolectric devolve o resultado da Activity e fornece os fluxos do ContentResolver).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class BackupFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Before fun setUp() = DialogLayoutIdling.install()
    @After fun tearDown() = DialogLayoutIdling.uninstall()

    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    /** Responde ao seletor de arquivos aberto pela tela com [uri]. */
    private fun ActivityScenario<MainActivity>.pickFile(uri: Uri, expectedAction: String) = onActivity { a ->
        val started = shadowOf(a).nextStartedActivityForResult
        assertEquals(expectedAction, started.intent.action)
        shadowOf(a).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))
    }

    @Test
    fun `backup completo grava o arquivo e restaurar devolve os dados apagados`() {
        val today = LocalDate.now()
        withAppDatabase { db ->
            val clock = Clock.systemDefaultZone()
            val foods = FoodRepositoryImpl(db)
            val add = AddFoodToMeal(foods, DiaryRepositoryImpl(db, clock), MealSlotRepositoryImpl(db), clock)
            add(today, 2, foods.search("arroz tipo 1 cozido", FoodFilter.ALL, null).first().first().id, 150.0)
            WeightRepositoryImpl(db, clock).save(WeightRecord(today, 78.4, null))
        }
        val backupUri = Uri.parse("content://teste/calories-backup.json")
        val written = ByteArrayOutputStream()
        shadowOf(resolver).registerOutputStream(backupUri, written)

        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido")
            rule.onNodeWithText("Ajustes").performClick()
            rule.onNodeWithText("Dados e backup").performClick()
            rule.waitForText("Fazer backup completo")
            rule.onNodeWithText("Fazer backup completo").performClick()
            sc.pickFile(backupUri, Intent.ACTION_CREATE_DOCUMENT)
            rule.waitForText("Backup salvo", substring = true)
        }
        val json = written.toString(Charsets.UTF_8.name())
        assertTrue(json.contains("\"format\": \"calories-backup\""), json.take(200))
        assertTrue(json.contains("78.4"))

        // Apaga os dados e restaura pelo app.
        withAppDatabase { db -> db.backupDao().let { it.deleteDiary(); it.deleteWeights() } }
        val restoreUri = Uri.parse("content://teste/escolhido.json")
        shadowOf(resolver).registerInputStream(restoreUri, ByteArrayInputStream(json.toByteArray()))
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido")
            rule.onNodeWithText("Ajustes").performClick()
            rule.onNodeWithText("Dados e backup").performClick()
            rule.waitForText("Restaurar backup")
            rule.onNodeWithText("Restaurar backup").performClick()
            sc.pickFile(restoreUri, Intent.ACTION_OPEN_DOCUMENT)
            rule.waitForText("Restaurar este backup?")
            rule.waitForText("Registros de peso")
            rule.onNodeWithText("Restaurar").performClick()
            rule.waitForText("Backup restaurado")
        }
        withAppDatabase { db ->
            val clock = Clock.systemDefaultZone()
            val entries = DiaryRepositoryImpl(db, clock).observeMeals(today).first().flatMap { it.entries }
            assertEquals(listOf(150.0), entries.map { it.amount })
            assertEquals(78.4, WeightRepositoryImpl(db, clock).observeLatest().first()?.weightKg)
        }
    }

    @Test
    fun `arquivo que nao e backup e recusado sem tocar nos dados`() {
        val uri = Uri.parse("content://teste/foto.json")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream("{\"isto\": \"não é backup\"}".toByteArray()))
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.waitForText("Consumido")
            rule.onNodeWithText("Ajustes").performClick()
            rule.onNodeWithText("Dados e backup").performClick()
            rule.waitForText("Restaurar backup")
            rule.onNodeWithText("Restaurar backup").performClick()
            sc.pickFile(uri, Intent.ACTION_OPEN_DOCUMENT)
            rule.waitForText("Backup recusado", substring = true)
        }
    }
}
