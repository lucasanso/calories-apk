package com.calories.data.repository

import com.calories.data.db.CaloriesDatabase
import com.calories.data.db.dao.FoodRow
import com.calories.data.db.entity.FoodEntity
import com.calories.data.db.entity.FoodUsageEntity
import com.calories.data.seed.LibrarySeeder
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.Food
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.FoodOrigin
import com.calories.domain.model.FoodSource
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(private val db: CaloriesDatabase) : FoodRepository {

    private val query = db.foodQueryDao()
    private val custom = db.customFoodDao()
    private val usage = db.foodUsageDao()
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }

    override fun search(query: String, filter: FoodFilter, categoryId: Long?, limit: Int): Flow<List<Food>> {
        val match = ftsMatch(query)
        if (match == null) {
            val source = when (filter) {
                FoodFilter.ALL -> this.query.all(categoryId)
                FoodFilter.FAVORITES -> this.query.favorites(categoryId)
                FoodFilter.RECENT -> this.query.recent(categoryId, 50)
                FoodFilter.CUSTOM -> this.query.custom(categoryId)
            }
            return source.map { rows ->
                val foods = rows.map { it.toDomain() }
                if (filter == FoodFilter.RECENT) foods else foods.sortedWith(compareBy(collator) { it.name })
            }
        }
        return this.query.search(match, categoryId, 400).map { rows ->
            rows.filter { row ->
                when (filter) {
                    FoodFilter.ALL -> true
                    FoodFilter.FAVORITES -> row.isFavorite == true
                    FoodFilter.RECENT -> row.lastUsedAt != null
                    FoodFilter.CUSTOM -> row.food.origin == FoodOrigin.CUSTOM.name
                }
            }.let { rank(query, it) }.take(limit).map { it.toDomain() }
        }
    }

    /** Ordena por relevância: nome que começa com o termo, sinônimo exato, favoritos e uso recente. */
    private fun rank(text: String, rows: List<FoodRow>): List<FoodRow> {
        val q = normalize(text).trim()
        val firstToken = q.split(' ').first()
        val recentLimit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        fun score(row: FoodRow): Int {
            val name = normalize(row.food.name)
            var s = 0
            if (name.startsWith(q)) s += 40
            else if (name.startsWith(firstToken)) s += 25
            else if (Regex("(^|[ ,(-])${Regex.escape(firstToken)}").containsMatchIn(name)) s += 10
            if (row.food.aliases?.split(ALIAS_SEPARATOR)?.any { normalize(it) == q } == true) s += 35
            if (row.isFavorite == true) s += 25
            if ((row.lastUsedAt ?: 0) > recentLimit) s += 15
            s += minOf(row.useCount ?: 0, 10)
            if (row.food.origin == FoodOrigin.CUSTOM.name) s += 3
            if (row.dataSourceCode == "TACO4") s += 2
            return s
        }
        return rows.sortedWith(compareByDescending<FoodRow> { score(it) }.thenBy(collator) { it.food.name })
    }

    override fun observe(id: Long): Flow<Food?> = query.observe(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Food? = query.get(id)?.toDomain()

    override fun observeCategories(): Flow<List<FoodCategory>> =
        query.categories().map { list -> list.map { FoodCategory(it.id, it.code, it.name, it.sortOrder, it.isSystem) } }

    override fun observeSources(): Flow<List<FoodSource>> =
        query.sources().map { list -> list.map { FoodSource(it.code, it.name, it.edition, it.license, it.citation, it.url) } }

    override suspend fun createCustom(draft: CustomFoodDraft, now: Instant): Long {
        val sourceId = query.sourceId(LibrarySeeder.USER_SOURCE) ?: error("Fonte USER ausente: biblioteca não inicializada")
        return custom.insert(
            FoodEntity(
                origin = FoodOrigin.CUSTOM.name, sourceKey = null, dataSourceId = sourceId, sourceCode = null, sourceDescription = null,
                sourceGroup = null, name = draft.name, baseName = draft.name, brand = draft.brand, aliases = null, categoryId = draft.categoryId,
                preparation = draft.preparation.name, referenceAmount = draft.referenceAmount, referenceUnit = draft.referenceUnit.name,
                nutrients = draft.values().toColumns(), notes = null, derivedFromFoodId = draft.derivedFromFoodId, libraryVersion = null,
                isDeprecated = false, isArchived = false, createdAt = now.toEpochMilli(), updatedAt = now.toEpochMilli(),
            ),
        )
    }

    override suspend fun updateCustom(id: Long, draft: CustomFoodDraft, now: Instant) {
        val n = draft.nutrients
        val changed = custom.update(
            id, draft.name, draft.brand, draft.categoryId, draft.preparation.name, draft.referenceAmount, draft.referenceUnit.name,
            n[Nutrient.ENERGY], n[Nutrient.PROTEIN], n[Nutrient.CARBOHYDRATE], n[Nutrient.FAT], n[Nutrient.SATURATED_FAT],
            n[Nutrient.FIBER], n[Nutrient.SODIUM], now.toEpochMilli(),
        )
        check(changed == 1) { "Somente alimentos personalizados podem ser editados" }
    }

    override suspend fun deleteCustom(id: Long): Boolean =
        if (custom.references(id) > 0) { custom.archive(id); false } else custom.delete(id) == 1

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        val current = usage.get(id) ?: FoodUsageEntity(id, false, 0, null, null)
        usage.upsert(current.copy(isFavorite = favorite))
    }

    override suspend fun recordUsage(id: Long, amount: Double, at: Instant) {
        val current = usage.get(id) ?: FoodUsageEntity(id, false, 0, null, null)
        usage.upsert(current.copy(useCount = current.useCount + 1, lastUsedAt = at.toEpochMilli(), lastAmount = amount))
    }

    override suspend fun lastAmount(id: Long): Double? = usage.get(id)?.lastAmount

    private fun CustomFoodDraft.values(): Map<Nutrient, NutrientValue> =
        Nutrient.entries.associateWith { n -> nutrients[n]?.let { NutrientValue.Measured(it) } ?: NutrientValue.NotAvailable }

    companion object {
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /** Converte o texto digitado numa consulta FTS por prefixo ("frang grel" → "frang* grel*"). */
        fun ftsMatch(text: String): String? {
            val tokens = normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.take(8)
            return if (tokens.isEmpty()) null else tokens.joinToString(" ") { "$it*" }
        }
    }
}
