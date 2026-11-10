package com.calories.domain.nutrition

import kotlin.math.abs

/**
 * Regras de plausibilidade de um perfil nutricional, usadas pelo importador da biblioteca
 * e (na Fase 3) pelo cadastro de alimentos personalizados.
 *
 * As regras apenas apontam problemas; nenhum valor é corrigido automaticamente.
 */
object NutritionRules {

    enum class Severity { ERROR, WARNING }

    data class Finding(val code: String, val severity: Severity, val message: String)

    /** Limites físicos por 100 g: nenhum macronutriente passa de 100 g; sal puro tem ~38.758 mg de sódio. */
    const val MAX_MACRO_PER_100 = 100.0
    const val MAX_SODIUM_MG_PER_100 = 40_000.0
    const val SATURATED_TOLERANCE_G = 0.1

    /** Divergência relativa tolerada entre a energia publicada e 4P + 4C + 9G. */
    const val ATWATER_WARNING_RATIO = 0.15

    fun check(profile: NutrientProfile): List<Finding> = buildList {
        for (nutrient in listOf(Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT, Nutrient.SATURATED_FAT, Nutrient.FIBER)) {
            val v = profile.per100(nutrient) ?: continue
            if (v > MAX_MACRO_PER_100 + 1e-9) {
                add(Finding("E4", Severity.ERROR, "${nutrient.labelPt} acima de 100 g por 100 g ($v)"))
            }
        }
        profile.per100(Nutrient.SODIUM)?.let {
            if (it > MAX_SODIUM_MG_PER_100) add(Finding("E4", Severity.ERROR, "Sódio acima de 40.000 mg por 100 g ($it)"))
        }

        val fat = profile[Nutrient.FAT].contribution
        val saturated = profile[Nutrient.SATURATED_FAT].contribution
        if (fat != null && saturated != null && saturated > fat + SATURATED_TOLERANCE_G) {
            add(Finding("E5", Severity.ERROR, "Gordura saturada ($saturated g) maior que gordura total ($fat g)"))
        }

        val fiber = profile[Nutrient.FIBER].contribution
        val carbs = profile[Nutrient.CARBOHYDRATE].contribution
        if (fiber != null && carbs != null && fiber > carbs + 1e-9) {
            add(Finding("W2", Severity.WARNING, "Fibra ($fiber g) maior que carboidrato total ($carbs g)"))
        }

        atwaterDeviation(profile)?.let { (published, estimated) ->
            add(
                Finding(
                    "W1", Severity.WARNING,
                    "Energia publicada ($published kcal) difere mais de 15% de 4P+4C+9G (${"%.1f".format(estimated)} kcal). " +
                        "Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.",
                ),
            )
        }
    }

    /** Retorna (publicada, estimada) quando a diferença relativa passa de [ATWATER_WARNING_RATIO]. */
    fun atwaterDeviation(profile: NutrientProfile): Pair<Double, Double>? {
        val energy = profile[Nutrient.ENERGY].contribution ?: return null
        val p = profile[Nutrient.PROTEIN].contribution ?: return null
        val c = profile[Nutrient.CARBOHYDRATE].contribution ?: return null
        val f = profile[Nutrient.FAT].contribution ?: return null
        val estimated = 4 * p + 4 * c + 9 * f
        val base = maxOf(energy, estimated)
        if (base < 20.0) return null // valores pequenos: diferença relativa não é informativa
        return if (abs(energy - estimated) / base > ATWATER_WARNING_RATIO) energy to estimated else null
    }
}
