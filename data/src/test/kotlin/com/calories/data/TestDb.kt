package com.calories.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calories.data.db.CaloriesDatabase
import com.calories.data.seed.LibraryAssetSource
import com.calories.data.seed.LibrarySeeder
import com.calories.library.format.AssetCategory
import com.calories.library.format.AssetFood
import com.calories.library.format.AssetNutrient
import com.calories.library.format.AssetNutrients
import com.calories.library.format.AssetReference
import com.calories.library.format.AssetSource
import com.calories.library.format.LibraryAsset
import com.calories.library.format.LibraryAssetCodec
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

object TestDb {
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)

    fun create(): CaloriesDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), CaloriesDatabase::class.java,
    ).allowMainThreadQueries().build()

    /** Asset real gerado pelo importador (app/src/main/assets). */
    val realAsset: File = File("../app/src/main/assets/library/food_library.json").also {
        check(it.isFile) { "Asset não encontrado em ${it.absolutePath}" }
    }

    fun seeder(db: CaloriesDatabase, asset: () -> String): LibrarySeeder =
        LibrarySeeder(db, LibraryAssetSource { asset().byteInputStream() }, clock)

    fun realSeeder(db: CaloriesDatabase) = seeder(db) { realAsset.readText() }

    fun food(key: String, name: String, kcal: Double, baseName: String = name) = AssetFood(
        key = key, source = "TACO4", sourceCode = key.substringAfter(':'), sourceDescription = name, name = name, baseName = baseName,
        category = "PROTEINS", preparation = "GRILLED", reference = AssetReference(100.0, "GRAM"),
        nutrients = AssetNutrients(
            AssetNutrient.measured(kcal), AssetNutrient.measured(10.0), AssetNutrient.measured(0.0), AssetNutrient.measured(1.0),
            AssetNutrient.notAvailable, AssetNutrient.notApplicable, AssetNutrient.trace,
        ),
    )

    fun asset(version: Int, vararg foods: AssetFood): String = LibraryAssetCodec.encode(
        LibraryAsset(
            formatVersion = LibraryAssetCodec.FORMAT_VERSION, libraryVersion = version,
            sources = listOf(AssetSource(code = "TACO4", name = "TACO", license = "L", citation = "C", carbohydrateBasis = "TOTAL_BY_DIFFERENCE")),
            categories = listOf(AssetCategory("PROTEINS", "Proteínas", 1)),
            foods = foods.toList(),
        ),
    )
}
