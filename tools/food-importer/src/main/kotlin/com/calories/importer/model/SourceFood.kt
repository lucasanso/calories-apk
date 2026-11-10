package com.calories.importer.model

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientValue
import java.math.BigDecimal

/** Alimento já normalizado, ainda sem decisões de curadoria (nome, categoria, preparo). */
data class SourceFood(
    val source: String,
    val code: String,
    /** Descrição publicada pela fonte, com espaços normalizados e correções documentadas aplicadas. */
    val description: String,
    val group: String?,
    val referenceAmount: Double,
    val referenceUnit: MeasureUnit,
    val nutrients: Map<Nutrient, NutrientValue>,
    val notes: List<String>,
    /** Valores brutos para as verificações de consistência (E8/E9); não vão para o app. */
    val consistency: ConsistencyInputs,
) {
    val key: String get() = "$source:$code"
    val profile: NutrientProfile get() = NutrientProfile(referenceAmount, referenceUnit, nutrients)
}

/** Componentes da composição centesimal (g/100 g) e energia, sem arredondamento. */
data class ConsistencyInputs(
    val energyKcal: BigDecimal? = null,
    val energyKj: BigDecimal? = null,
    val moisture: BigDecimal? = null,
    val protein: BigDecimal? = null,
    val fat: BigDecimal? = null,
    val carbohydrate: BigDecimal? = null,
    val ash: BigDecimal? = null,
    val alcohol: BigDecimal? = null,
)

fun normalizeSpaces(text: String): String = text.trim().replace(Regex("\\s+"), " ")
