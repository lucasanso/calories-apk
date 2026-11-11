package com.calories.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.calories.data.db.CaloriesDatabase
import com.calories.data.db.entity.AppMetaEntity
import com.calories.data.db.entity.DataSourceEntity
import com.calories.data.db.entity.FoodCategoryEntity
import com.calories.data.db.entity.FoodEntity
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.repository.ALIAS_SEPARATOR
import com.calories.data.repository.NOTE_SEPARATOR
import com.calories.data.repository.toColumns
import com.calories.domain.model.LibraryStatus
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.repository.LibraryRepository
import com.calories.library.format.AssetNutrient
import com.calories.library.format.AssetNutrients
import com.calories.library.format.LibraryAsset
import com.calories.library.format.LibraryAssetCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Origem do asset `food_library.json` (o app lê de `assets/`; os testes injetam outro). */
fun interface LibraryAssetSource {
    fun open(): InputStream
}

class AndroidLibraryAssetSource @Inject constructor(@ApplicationContext private val context: Context) : LibraryAssetSource {
    override fun open(): InputStream = context.assets.open("library/food_library.json")
}

/**
 * Importa a biblioteca nutricional do asset para o Room (docs/03 §3.8):
 * - só quando a versão do asset é maior que a instalada (idempotente);
 * - upsert por `sourceKey` numa única transação (sem duplicar, preservando ids);
 * - itens que saíram da biblioteca viram `isDeprecated`;
 * - nunca toca em alimentos personalizados, favoritos, diário ou metas.
 */
@Singleton
class LibrarySeeder @Inject constructor(
    private val db: CaloriesDatabase,
    private val source: LibraryAssetSource,
    private val clock: Clock,
) : LibraryRepository {

    companion object {
        const val KEY_LIBRARY_VERSION = "library.version"
        const val KEY_DEFAULTS_VERSION = "defaults.version"
        const val USER_SOURCE = "USER"
        private const val DEFAULTS_VERSION = 1
        private val HEADER_VERSION = Regex("\"libraryVersion\"\\s*:\\s*(\\d+)")

        /** Refeições iniciais (editáveis): nome e horário em minutos. */
        val DEFAULT_SLOTS = listOf("Café da manhã" to 7 * 60, "Almoço" to 12 * 60, "Lanche da tarde" to 16 * 60, "Jantar" to 19 * 60 + 30, "Ceia" to 22 * 60)
    }

    private val mutex = Mutex()

    override suspend fun ensureLibrary(): LibraryStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureDefaults()
            val installed = db.metaDao().get(KEY_LIBRARY_VERSION)?.toIntOrNull() ?: 0
            if (assetVersion() > installed) {
                val asset = source.open().use { LibraryAssetCodec.decode(it) }
                import(asset)
            }
            LibraryStatus(db.metaDao().get(KEY_LIBRARY_VERSION)?.toIntOrNull() ?: 0, db.foodQueryDao().libraryCount())
        }
    }

    /** Lê só o cabeçalho do asset para decidir se precisa importar. */
    private fun assetVersion(): Int = source.open().use { input ->
        val head = ByteArray(512).let { buf -> String(buf, 0, input.read(buf).coerceAtLeast(0), Charsets.UTF_8) }
        HEADER_VERSION.find(head)?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE
    }

    private suspend fun ensureDefaults() {
        val meta = db.metaDao()
        if ((meta.get(KEY_DEFAULTS_VERSION)?.toIntOrNull() ?: 0) >= DEFAULTS_VERSION) return
        db.withTransaction {
            val seed = db.librarySeedDao()
            if (seed.sources().none { it.code == USER_SOURCE }) {
                seed.insertSource(
                    DataSourceEntity(
                        code = USER_SOURCE, name = "Cadastro do usuário", edition = null, publisher = null, year = null, url = null,
                        license = "Dados informados pelo usuário (ex.: rótulo do produto).", citation = "Cadastro do usuário",
                        carbohydrateBasis = "LABEL", accessedAt = null,
                    ),
                )
            }
            val slots = db.mealSlotDao()
            if (slots.count() == 0) {
                DEFAULT_SLOTS.forEachIndexed { i, (name, minutes) -> slots.insert(MealSlotEntity(name = name, defaultTimeMinutes = minutes, sortOrder = i, isActive = true)) }
            }
            meta.put(AppMetaEntity(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION.toString()))
        }
    }

    internal suspend fun import(asset: LibraryAsset) = db.withTransaction {
        val seed = db.librarySeedDao()
        val now = clock.millis()

        val sources = seed.sources().associateBy { it.code }
        val sourceIds = asset.sources.associate { s ->
            val entity = DataSourceEntity(
                id = sources[s.code]?.id ?: 0, code = s.code, name = s.name, edition = s.edition, publisher = s.publisher, year = s.year,
                url = s.url, license = s.license, citation = s.citation, carbohydrateBasis = s.carbohydrateBasis, accessedAt = s.accessedAt,
            )
            s.code to if (entity.id == 0L) seed.insertSource(entity) else entity.id.also { seed.updateSource(entity) }
        }

        val categories = seed.categories().associateBy { it.code }
        val categoryIds = asset.categories.associate { c ->
            val existing = categories[c.code]
            c.code to if (existing == null) {
                seed.insertCategory(FoodCategoryEntity(code = c.code, name = c.name, sortOrder = c.sortOrder, isSystem = true))
            } else {
                existing.id.also { seed.updateCategory(existing.copy(name = c.name, sortOrder = c.sortOrder, isSystem = true)) }
            }
        }

        val existing = seed.libraryFoods().associateBy { it.sourceKey }
        val inserts = ArrayList<FoodEntity>()
        val updates = ArrayList<FoodEntity>()
        for (f in asset.foods) {
            val previous = existing[f.key]
            val entity = FoodEntity(
                id = previous?.id ?: 0,
                origin = "LIBRARY",
                sourceKey = f.key,
                dataSourceId = sourceIds.getValue(f.source),
                sourceCode = f.sourceCode,
                sourceDescription = f.sourceDescription,
                sourceGroup = f.sourceGroup,
                name = f.name,
                baseName = f.baseName,
                brand = null,
                aliases = f.aliases.joinToString(ALIAS_SEPARATOR).ifEmpty { null },
                categoryId = categoryIds[f.category],
                preparation = f.preparation,
                referenceAmount = f.reference.amount,
                referenceUnit = f.reference.unit,
                nutrients = f.nutrients.toDomain().toColumns(),
                notes = f.notes.joinToString(NOTE_SEPARATOR).ifEmpty { null },
                derivedFromFoodId = null,
                libraryVersion = asset.libraryVersion,
                isDeprecated = false,
                isArchived = false,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
            )
            if (previous == null) inserts += entity else updates += entity
        }
        inserts.chunked(200).forEach { seed.insertFoods(it) }
        updates.chunked(200).forEach { seed.updateFoods(it) }

        val keys = asset.foods.map { it.key }.toSet()
        existing.values.filter { it.sourceKey !in keys && !it.isDeprecated }.map { it.id }.chunked(500).forEach { seed.deprecate(it, now) }

        db.metaDao().put(AppMetaEntity(KEY_LIBRARY_VERSION, asset.libraryVersion.toString()))
    }

    private fun AssetNutrients.toDomain(): Map<Nutrient, NutrientValue> = mapOf(
        Nutrient.ENERGY to energyKcal.toDomain(), Nutrient.PROTEIN to proteinG.toDomain(), Nutrient.CARBOHYDRATE to carbohydrateG.toDomain(),
        Nutrient.FAT to fatG.toDomain(), Nutrient.SATURATED_FAT to saturatedFatG.toDomain(), Nutrient.FIBER to fiberG.toDomain(),
        Nutrient.SODIUM to sodiumMg.toDomain(),
    )

    private fun AssetNutrient.toDomain(): NutrientValue = when (status) {
        AssetNutrient.MEASURED -> NutrientValue.Measured(value!!)
        AssetNutrient.TRACE -> NutrientValue.Trace
        AssetNutrient.NOT_APPLICABLE -> NutrientValue.NotApplicable
        else -> NutrientValue.NotAvailable
    }
}
