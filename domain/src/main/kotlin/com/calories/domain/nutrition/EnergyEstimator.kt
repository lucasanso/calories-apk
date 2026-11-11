package com.calories.domain.nutrition

import kotlin.math.max

enum class BiologicalSex { MALE, FEMALE }

/** Multiplicadores convencionais de atividade física (aproximação, docs/04 §4.7). */
enum class ActivityLevel(val factor: Double, val labelPt: String) {
    SEDENTARY(1.2, "Sedentário"),
    LIGHT(1.375, "Levemente ativo"),
    MODERATE(1.55, "Moderadamente ativo"),
    VERY_ACTIVE(1.725, "Muito ativo"),
    EXTRA_ACTIVE(1.9, "Extremamente ativo"),
}

enum class Objective(val labelPt: String) {
    MAINTAIN("Manter o peso"),
    LOSE("Perder peso"),
    GAIN("Ganhar peso"),
}

data class EnergyInput(
    val sex: BiologicalSex,
    val ageYears: Int,
    val weightKg: Double,
    val heightCm: Double,
    val activity: ActivityLevel,
    val objective: Objective,
    /** Ajuste do objetivo em % do gasto total (ex.: 15 = −15% para perder). Ignorado em MAINTAIN. */
    val adjustmentPercent: Double = 15.0,
    /** Proteína sugerida em g por kg de peso. */
    val proteinPerKg: Double = 1.6,
    /** Gordura sugerida em % da energia. */
    val fatPercent: Double = 30.0,
)

data class EnergyEstimate(
    val bmrKcal: Double,
    val totalExpenditureKcal: Double,
    val targetKcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbohydrateG: Double,
    val fiberG: Double,
    val warnings: List<String>,
)

/**
 * Estimativa de necessidades energéticas com a equação de Mifflin-St Jeor
 * (Mifflin MD, St Jeor ST et al. Am J Clin Nutr. 1990;51(2):241–247).
 * É uma APROXIMAÇÃO, não uma prescrição: todos os valores são editáveis antes de salvar.
 */
object EnergyEstimator {

    /** Fibra: 14 g por 1.000 kcal (ingestão adequada das DRIs, Institute of Medicine, 2005). */
    const val FIBER_G_PER_1000_KCAL = 14.0

    val AGE_RANGE = 15..100
    val WEIGHT_RANGE = 20.0..400.0
    val HEIGHT_RANGE = 100.0..250.0
    val ADJUSTMENT_RANGE = 0.0..30.0

    fun bmr(sex: BiologicalSex, ageYears: Int, weightKg: Double, heightCm: Double): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            BiologicalSex.MALE -> base + 5.0
            BiologicalSex.FEMALE -> base - 161.0
        }
    }

    fun validate(input: EnergyInput): List<String> = buildList {
        if (input.ageYears !in AGE_RANGE) add("Idade deve estar entre ${AGE_RANGE.first} e ${AGE_RANGE.last} anos.")
        if (input.weightKg !in WEIGHT_RANGE) add("Peso deve estar entre 20 e 400 kg.")
        if (input.heightCm !in HEIGHT_RANGE) add("Altura deve estar entre 100 e 250 cm.")
        if (input.adjustmentPercent !in ADJUSTMENT_RANGE) add("Ajuste deve estar entre 0% e 30%.")
        if (input.proteinPerKg !in 0.0..4.0) add("Proteína deve estar entre 0 e 4 g/kg.")
        if (input.fatPercent !in 0.0..70.0) add("Gordura deve estar entre 0% e 70% da energia.")
    }

    fun estimate(input: EnergyInput): EnergyEstimate {
        val errors = validate(input)
        require(errors.isEmpty()) { errors.joinToString(" ") }

        val bmr = bmr(input.sex, input.ageYears, input.weightKg, input.heightCm)
        val total = bmr * input.activity.factor
        val target = when (input.objective) {
            Objective.MAINTAIN -> total
            Objective.LOSE -> total * (1.0 - input.adjustmentPercent / 100.0)
            Objective.GAIN -> total * (1.0 + input.adjustmentPercent / 100.0)
        }
        val protein = input.weightKg * input.proteinPerKg
        val fat = target * input.fatPercent / 100.0 / MacroEnergy.KCAL_PER_G_FAT
        val carbs = max(0.0, (target - protein * MacroEnergy.KCAL_PER_G_PROTEIN - fat * MacroEnergy.KCAL_PER_G_FAT) / MacroEnergy.KCAL_PER_G_CARBOHYDRATE)
        val warnings = buildList {
            if (target < bmr) add("A meta sugerida fica abaixo da taxa metabólica basal estimada. Considere orientação profissional.")
            if (protein * 4 + fat * 9 > target) add("Proteína e gordura sugeridas já passam da meta de energia; ajuste os percentuais.")
        }
        return EnergyEstimate(bmr, total, target, protein, fat, carbs, target / 1000.0 * FIBER_G_PER_1000_KCAL, warnings)
    }
}
