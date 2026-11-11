package com.calories.domain.model

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.Preparation

enum class FoodOrigin { LIBRARY, CUSTOM }

/** Alimento da biblioteca (somente leitura) ou personalizado. */
data class Food(
    val id: Long,
    val origin: FoodOrigin,
    /** `TACO4:410`, `USDA_SR:175176`; `null` para personalizados. */
    val sourceKey: String?,
    /** `TACO4`, `USDA_SR` ou `USER`. */
    val dataSourceCode: String,
    val name: String,
    val baseName: String?,
    val brand: String?,
    val aliases: List<String>,
    val categoryId: Long?,
    val preparation: Preparation,
    val profile: NutrientProfile,
    val sourceDescription: String?,
    val sourceGroup: String?,
    val notes: List<String>,
    val derivedFromFoodId: Long?,
    val isFavorite: Boolean,
    val isArchived: Boolean = false,
    val isDeprecated: Boolean = false,
) {
    val isLibrary: Boolean get() = origin == FoodOrigin.LIBRARY
}

data class FoodCategory(val id: Long, val code: String, val name: String, val sortOrder: Int, val isSystem: Boolean)

/** Procedência de dados exibida em "Fontes e licenças" e no detalhe do alimento. */
data class FoodSource(
    val code: String,
    val name: String,
    val edition: String?,
    val license: String,
    val citation: String,
    val url: String?,
)

/** Filtros da lista e da busca de alimentos. */
enum class FoodFilter { ALL, FAVORITES, RECENT, CUSTOM }

/** Dados de um alimento personalizado (criação ou edição). */
data class CustomFoodDraft(
    val name: String,
    val brand: String?,
    val categoryId: Long?,
    val preparation: Preparation,
    val referenceAmount: Double,
    val referenceUnit: MeasureUnit,
    /** `null` = sem dado. Energia, proteína, carboidrato e gordura são obrigatórios. */
    val nutrients: Map<Nutrient, Double?>,
    val derivedFromFoodId: Long? = null,
)
