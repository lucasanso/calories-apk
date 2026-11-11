package com.calories.domain.nutrition

/**
 * Valores nutricionais de um alimento por [referenceAmount] [referenceUnit]
 * (ex.: por 100 g). Nutrientes não informados são [NutrientValue.NotAvailable].
 */
data class NutrientProfile(
    val referenceAmount: Double,
    val referenceUnit: MeasureUnit,
    val values: Map<Nutrient, NutrientValue>,
) {
    init {
        require(referenceAmount.isFinite() && referenceAmount > 0.0) {
            "Quantidade de referência precisa ser positiva: $referenceAmount"
        }
    }

    operator fun get(nutrient: Nutrient): NutrientValue = values[nutrient] ?: NutrientValue.NotAvailable

    /** Valor por 100 unidades de referência, para comparar com limites expressos "por 100 g". */
    fun per100(nutrient: Nutrient): Double? = this[nutrient].contribution?.let { it * 100.0 / referenceAmount }
}
