package com.calories

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import com.calories.data.db.CaloriesDatabase
import com.calories.data.seed.AndroidLibraryAssetSource
import com.calories.data.seed.LibrarySeeder
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Clock

/** Abre o banco real do app (mesmo arquivo que o Hilt usa), já com a biblioteca importada. */
fun <T> withAppDatabase(block: suspend (CaloriesDatabase) -> T): T = runBlocking {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.databaseBuilder(ctx, CaloriesDatabase::class.java, CaloriesDatabase.NAME).build()
    try {
        LibrarySeeder(db, AndroidLibraryAssetSource(ctx), Clock.systemDefaultZone()).ensureLibrary()
        block(db)
    } finally {
        db.close()
    }
}

fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 15_000) = waitUntil(timeoutMs) {
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
}

/** Desenha a janela da Activity num bitmap (gráficos nativos do Robolectric). */
fun ActivityScenario<MainActivity>.saveScreenshot(dir: File, name: String) {
    onActivity { a ->
        val v = a.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(dir.apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/**
 * No Robolectric, janelas de Dialog (AlertDialog, DatePickerDialog) não recebem passada de desenho.
 * O Compose mede o conteúdo de um diálogo no dispatchDraw, então a medição fica pendente para sempre
 * e o teste nunca fica ocioso. Este recurso de ociosidade faz a medição que o aparelho faria ao
 * desenhar o quadro. Registrar com [DialogLayoutIdling.install] num @Before.
 */
object DialogLayoutIdling : IdlingResource {
    override fun getName() = "compose-dialog-layout"

    override fun isIdleNow(): Boolean {
        ShadowDialog.getShownDialogs().filter { it.isShowing }.forEach { d -> d.window?.decorView?.let(::measurePending) }
        return true
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) = Unit

    private fun measurePending(v: View) {
        if (v is ViewRootForTest && v.hasPendingMeasureOrLayout) v.measureAndLayoutForTest()
        if (v is ViewGroup) for (i in 0 until v.childCount) measurePending(v.getChildAt(i))
    }

    fun install() { if (this !in IdlingRegistry.getInstance().resources) IdlingRegistry.getInstance().register(this) }
    fun uninstall() { IdlingRegistry.getInstance().unregister(this) }
}
