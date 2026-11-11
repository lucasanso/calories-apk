package com.calories.domain.nutrition

/** Progresso de um nutriente em relação à meta (docs/04 §4.6). */
data class GoalProgress(val consumed: Double, val goal: Double?) {
    /** Negativo quando o consumo passou da meta. `null` sem meta. */
    val remaining: Double? get() = goal?.let { it - consumed }

    /** Fração da meta (pode passar de 1). `null` sem meta ou com meta zero. */
    val fraction: Double? get() = goal?.takeIf { it > 0.0 }?.let { consumed / it }

    val isOver: Boolean get() = (remaining ?: 0.0) < 0.0
}

/**
 * Conversão de metas de macronutrientes entre gramas e percentual da energia, usando
 * os fatores gerais 4/4/9 kcal/g como convenção de planejamento (identificada como tal na interface).
 */
object MacroEnergy {
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBOHYDRATE = 4.0
    const val KCAL_PER_G_FAT = 9.0

    fun gramsFromPercent(totalKcal: Double, percent: Double, kcalPerGram: Double): Double = totalKcal * percent / 100.0 / kcalPerGram
    fun percentFromGrams(totalKcal: Double, grams: Double, kcalPerGram: Double): Double =
        if (totalKcal <= 0.0) 0.0 else grams * kcalPerGram / totalKcal * 100.0
}
