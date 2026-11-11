package com.calories.domain.nutrition

/**
 * Cálculo proporcional (docs/04-calculo-nutricional.md §4.1):
 * valor consumido = valor de referência × quantidade ÷ quantidade de referência.
 * Nunca arredonda: o arredondamento é responsabilidade da apresentação.
 */
object NutritionCalculator {

    /** Quantidade máxima aceita por lançamento (g ou ml). */
    const val MAX_AMOUNT = 10_000.0

    /** Acima disto a interface pede confirmação (provável erro de digitação). */
    const val CONFIRM_AMOUNT = 2_000.0

    fun scale(referenceValue: Double, amount: Double, referenceAmount: Double): Double =
        referenceValue * amount / referenceAmount

    /**
     * Valores consumidos de cada nutriente para [amount] na unidade de referência do perfil.
     * `Tr`, `NA` e "sem dado" mantêm o status (não viram números).
     */
    fun consumed(reference: NutrientProfile, amount: Double): Map<Nutrient, NutrientValue> {
        requireValidAmount(amount)
        return Nutrient.entries.associateWith { nutrient ->
            when (val v = reference[nutrient]) {
                is NutrientValue.Measured -> NutrientValue.Measured(scale(v.amount, amount, reference.referenceAmount))
                else -> v
            }
        }
    }

    fun requireValidAmount(amount: Double) {
        require(amount.isFinite() && amount > 0.0) { "Quantidade precisa ser maior que zero" }
        require(amount <= MAX_AMOUNT) { "Quantidade acima do limite de ${MAX_AMOUNT.toInt()}" }
    }
}
