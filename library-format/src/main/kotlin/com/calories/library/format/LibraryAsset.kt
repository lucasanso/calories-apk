package com.calories.library.format

import kotlinx.serialization.Serializable

/**
 * Formato do asset `food_library.json`, escrito pelo importador (`:tools:food-importer`)
 * e lido pelo app na importação inicial (seed). Ver docs/03-biblioteca-nutricional.md §3.4.
 *
 * Mudanças incompatíveis exigem incrementar [LibraryAssetCodec.FORMAT_VERSION].
 */
@Serializable
data class LibraryAsset(
    val formatVersion: Int,
    val libraryVersion: Int,
    val sources: List<AssetSource>,
    val categories: List<AssetCategory>,
    val foods: List<AssetFood>,
)

@Serializable
data class AssetSource(
    /** Ex.: `TACO4`, `USDA_SR`. */
    val code: String,
    val name: String,
    val edition: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val url: String? = null,
    val license: String,
    val citation: String,
    /** `TOTAL_BY_DIFFERENCE` para TACO e USDA (carboidrato inclui fibra). */
    val carbohydrateBasis: String,
    val accessedAt: String? = null,
)

@Serializable
data class AssetCategory(
    /** Ex.: `PROTEINS`. */
    val code: String,
    val name: String,
    val sortOrder: Int,
)

@Serializable
data class AssetFood(
    /** Chave estável usada no upsert do app: `TACO4:409`, `USDA_SR:175176`. */
    val key: String,
    val source: String,
    val sourceCode: String,
    /** Descrição como publicada pela fonte (espaços normalizados). */
    val sourceDescription: String,
    val sourceGroup: String? = null,
    val name: String,
    val baseName: String? = null,
    val aliases: List<String> = emptyList(),
    val category: String,
    /** Nome de `com.calories.domain.nutrition.Preparation`. */
    val preparation: String,
    val reference: AssetReference,
    val nutrients: AssetNutrients,
    /** Observações exibidas no detalhe do alimento (ex.: valor inferido, ressalvas da fonte). */
    val notes: List<String> = emptyList(),
)

@Serializable
data class AssetReference(
    val amount: Double,
    /** `GRAM` ou `MILLILITER`. */
    val unit: String,
)

@Serializable
data class AssetNutrients(
    val energyKcal: AssetNutrient,
    val proteinG: AssetNutrient,
    val carbohydrateG: AssetNutrient,
    val fatG: AssetNutrient,
    val saturatedFatG: AssetNutrient,
    val fiberG: AssetNutrient,
    val sodiumMg: AssetNutrient,
)

/**
 * `status` é `MEASURED` (padrão, com [value]), `TRACE`, `NOT_APPLICABLE` ou `NOT_AVAILABLE`.
 */
@Serializable
data class AssetNutrient(
    val status: String = MEASURED,
    val value: Double? = null,
) {
    init {
        require(status in STATUSES) { "Status de nutriente inválido: $status" }
        require((status == MEASURED) == (value != null)) { "Somente MEASURED tem valor (status=$status, value=$value)" }
    }

    companion object {
        const val MEASURED = "MEASURED"
        const val TRACE = "TRACE"
        const val NOT_APPLICABLE = "NOT_APPLICABLE"
        const val NOT_AVAILABLE = "NOT_AVAILABLE"
        val STATUSES = setOf(MEASURED, TRACE, NOT_APPLICABLE, NOT_AVAILABLE)

        fun measured(value: Double) = AssetNutrient(MEASURED, value)
        val trace = AssetNutrient(TRACE)
        val notApplicable = AssetNutrient(NOT_APPLICABLE)
        val notAvailable = AssetNutrient(NOT_AVAILABLE)
    }
}
