package com.calories.domain.usecase

import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.repository.GoalRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

object GoalValidator {
    val ENERGY_RANGE = 500.0..10_000.0
    val MACRO_RANGE = 0.0..2_000.0

    fun validate(energy: Double?, protein: Double?, carbs: Double?, fat: Double?, fiber: Double?): List<String> = buildList {
        if (energy == null) add("Informe a meta de calorias.")
        else if (energy !in ENERGY_RANGE) add("Calorias devem estar entre 500 e 10.000 kcal.")
        listOf("Proteínas" to protein, "Carboidratos" to carbs, "Gorduras" to fat, "Fibras" to fiber).forEach { (label, v) ->
            if (v != null && v !in MACRO_RANGE) add("$label: valor inválido.")
        }
    }
}

/** Define a meta a partir de hoje; os dias anteriores continuam com a meta que valia neles. */
class SetGoal @Inject constructor(private val goals: GoalRepository, private val clock: Clock) {
    suspend operator fun invoke(
        energyKcal: Double?, proteinG: Double?, carbohydrateG: Double?, fatG: Double?, fiberG: Double?,
        origin: GoalOrigin, effectiveFrom: LocalDate = LocalDate.now(clock),
    ) {
        val errors = GoalValidator.validate(energyKcal, proteinG, carbohydrateG, fatG, fiberG)
        if (errors.isNotEmpty()) throw DomainException(errors.joinToString("\n"))
        goals.setGoal(NutritionGoal(effectiveFrom, energyKcal!!, proteinG, carbohydrateG, fatG, fiberG, origin))
    }
}
