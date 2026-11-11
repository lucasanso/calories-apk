package com.calories

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.calories.data.db.CaloriesDatabase
import com.calories.data.seed.AndroidLibraryAssetSource
import com.calories.data.seed.LibrarySeeder
import kotlinx.coroutines.runBlocking
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
