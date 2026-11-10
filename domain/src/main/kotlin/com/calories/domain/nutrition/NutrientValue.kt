package com.calories.domain.nutrition

/**
 * Valor de um nutriente como informado pela fonte. Ausência de dado nunca vira zero:
 * ela é [NotAvailable]. Ver docs/04-calculo-nutricional.md §4.2.
 */
sealed interface NutrientValue {
    /** Valor numérico publicado pela fonte, na base de referência do alimento. */
    data class Measured(val amount: Double) : NutrientValue {
        init {
            require(amount.isFinite()) { "Valor nutricional precisa ser finito: $amount" }
            require(amount >= 0.0) { "Valor nutricional não pode ser negativo: $amount" }
        }
    }

    /** "Tr": presente, mas abaixo do que a fonte considera quantificável. Soma como 0. */
    data object Trace : NutrientValue

    /** "NA": o nutriente não se aplica ao alimento (ex.: fibra em carne). Soma como 0. */
    data object NotApplicable : NutrientValue

    /** Sem dado na fonte (não analisado, em reavaliação, ausente). Nunca soma como 0. */
    data object NotAvailable : NutrientValue
}

/** Contribuição numérica para somas: `null` quando o dado não existe. */
val NutrientValue.contribution: Double?
    get() = when (this) {
        is NutrientValue.Measured -> amount
        NutrientValue.Trace, NutrientValue.NotApplicable -> 0.0
        NutrientValue.NotAvailable -> null
    }
