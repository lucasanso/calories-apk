package com.calories.domain.nutrition

/**
 * Soma de um nutriente sobre vários itens. [missingCount] conta itens sem dado
 * (o total é "parcial"); traço e "não aplicável" contam como zero e não como ausência.
 */
data class NutrientTotal(val sum: Double, val missingCount: Int, val itemCount: Int) {
    val isComplete: Boolean get() = missingCount == 0
    /** Falso quando nenhum item tem o dado: a interface mostra "—", não "0". */
    val hasAnyData: Boolean get() = itemCount > 0 && missingCount < itemCount

    operator fun plus(other: NutrientTotal) =
        NutrientTotal(sum + other.sum, missingCount + other.missingCount, itemCount + other.itemCount)

    companion object {
        val ZERO = NutrientTotal(0.0, 0, 0)
    }
}

/** Totais de todos os nutrientes de um conjunto de itens (refeição, dia, período). */
data class NutrientTotals(private val totals: Map<Nutrient, NutrientTotal>) {
    operator fun get(nutrient: Nutrient): NutrientTotal = totals[nutrient] ?: NutrientTotal.ZERO

    val energyKcal: Double get() = this[Nutrient.ENERGY].sum
    val itemCount: Int get() = totals.values.maxOfOrNull { it.itemCount } ?: 0

    operator fun plus(other: NutrientTotals) = NutrientTotals(Nutrient.entries.associateWith { this[it] + other[it] })

    companion object {
        val EMPTY = NutrientTotals(emptyMap())
    }
}

object NutrientAggregator {
    /** Soma itens já escalados (valores consumidos). Usa os valores sem arredondamento. */
    fun sum(items: List<Map<Nutrient, NutrientValue>>): NutrientTotals {
        if (items.isEmpty()) return NutrientTotals.EMPTY
        return NutrientTotals(
            Nutrient.entries.associateWith { nutrient ->
                var sum = 0.0
                var missing = 0
                for (item in items) {
                    val c = (item[nutrient] ?: NutrientValue.NotAvailable).contribution
                    if (c == null) missing++ else sum += c
                }
                NutrientTotal(sum, missing, items.size)
            },
        )
    }
}
