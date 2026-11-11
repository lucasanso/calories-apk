package com.calories.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vetores de teste de docs/04-calculo-nutricional.md. */
class CalculationTest {

    private fun profile(reference: Double = 100.0, vararg values: Pair<Nutrient, NutrientValue>) =
        NutrientProfile(reference, MeasureUnit.GRAM, values.toMap())

    private fun measured(n: Nutrient, v: Double) = n to NutrientValue.Measured(v)

    @Test
    fun `exemplo do requisito - 165 kcal por 100 g, 150 g = 247,5 kcal`() {
        val consumed = NutritionCalculator.consumed(profile(100.0, measured(Nutrient.ENERGY, 165.0)), 150.0)
        assertEquals(NutrientValue.Measured(247.5), consumed[Nutrient.ENERGY])
    }

    @Test
    fun `referencia diferente de 100 - rotulo 120 kcal por 30 g, 45 g = 180 kcal`() {
        val consumed = NutritionCalculator.consumed(profile(30.0, measured(Nutrient.ENERGY, 120.0)), 45.0)
        assertEquals(180.0, (consumed[Nutrient.ENERGY] as NutrientValue.Measured).amount, 1e-12)
    }

    @Test
    fun `quantidade fracionaria e igual a referencia`() {
        assertEquals(0.4, NutritionCalculator.scale(3.2, 12.5, 100.0), 1e-12)
        assertEquals(9.0, NutritionCalculator.scale(9.0, 100.0, 100.0), 0.0)
        assertEquals(76.5, NutritionCalculator.scale(51.0, 150.0, 100.0), 1e-12)
    }

    @Test
    fun `traco, nao aplicavel e sem dado mantem o status ao escalar`() {
        val p = profile(100.0, Nutrient.FIBER to NutrientValue.NotApplicable, Nutrient.SODIUM to NutrientValue.Trace)
        val c = NutritionCalculator.consumed(p, 250.0)
        assertEquals(NutrientValue.NotApplicable, c[Nutrient.FIBER])
        assertEquals(NutrientValue.Trace, c[Nutrient.SODIUM])
        assertEquals(NutrientValue.NotAvailable, c[Nutrient.SATURATED_FAT])
    }

    @Test
    fun `quantidade invalida e rejeitada`() {
        val p = profile(100.0, measured(Nutrient.ENERGY, 100.0))
        assertFailsWith<IllegalArgumentException> { NutritionCalculator.consumed(p, 0.0) }
        assertFailsWith<IllegalArgumentException> { NutritionCalculator.consumed(p, -10.0) }
        assertFailsWith<IllegalArgumentException> { NutritionCalculator.consumed(p, Double.NaN) }
        assertFailsWith<IllegalArgumentException> { NutritionCalculator.consumed(p, 10_000.1) }
    }

    @Test
    fun `total parcial quando algum item nao tem o dado`() {
        val items = listOf(
            mapOf(Nutrient.FIBER to NutrientValue.Measured(5.0)),
            mapOf(Nutrient.FIBER to NutrientValue.NotAvailable),
            mapOf(Nutrient.FIBER to NutrientValue.Measured(2.0)),
        )
        val total = NutrientAggregator.sum(items)[Nutrient.FIBER]
        assertEquals(7.0, total.sum)
        assertEquals(1, total.missingCount)
        assertFalse(total.isComplete)
        assertTrue(total.hasAnyData)
    }

    @Test
    fun `todos sem dado nao exibem zero`() {
        val total = NutrientAggregator.sum(listOf(emptyMap(), emptyMap()))[Nutrient.FIBER]
        assertFalse(total.hasAnyData)
    }

    @Test
    fun `traco e NA somam zero sem tornar o total parcial`() {
        val total = NutrientAggregator.sum(
            listOf(mapOf(Nutrient.FIBER to NutrientValue.NotApplicable), mapOf(Nutrient.FIBER to NutrientValue.Trace), mapOf(Nutrient.FIBER to NutrientValue.Measured(3.0))),
        )[Nutrient.FIBER]
        assertEquals(3.0, total.sum)
        assertTrue(total.isComplete)
    }

    @Test
    fun `total do dia e a soma das refeicoes sem arredondamento`() {
        val m1 = listOf(mapOf(Nutrient.ENERGY to NutrientValue.Measured(247.5)), mapOf(Nutrient.ENERGY to NutrientValue.Measured(0.4)))
        val m2 = listOf(mapOf(Nutrient.ENERGY to NutrientValue.Measured(100.35)))
        val day = NutrientAggregator.sum(m1 + m2)
        val sumOfMeals = NutrientAggregator.sum(m1) + NutrientAggregator.sum(m2)
        assertEquals(day[Nutrient.ENERGY].sum, sumOfMeals[Nutrient.ENERGY].sum, 1e-9)
        assertEquals(348.25, day.energyKcal, 1e-9)
    }

    @Test
    fun `progresso da meta abaixo, acima e sem meta`() {
        GoalProgress(1500.0, 2000.0).let { assertEquals(500.0, it.remaining); assertEquals(0.75, it.fraction); assertFalse(it.isOver) }
        GoalProgress(2142.0, 2000.0).let { assertEquals(-142.0, it.remaining); assertTrue(it.isOver); assertEquals(1.071, it.fraction!!, 1e-9) }
        GoalProgress(800.0, null).let { assertNull(it.remaining); assertNull(it.fraction) }
        assertNull(GoalProgress(10.0, 0.0).fraction)
    }

    @Test
    fun `Mifflin-St Jeor - vetores do docs 04`() {
        assertEquals(1780.0, EnergyEstimator.bmr(BiologicalSex.MALE, 30, 80.0, 180.0), 1e-9)
        assertEquals(1345.25, EnergyEstimator.bmr(BiologicalSex.FEMALE, 25, 60.0, 165.0), 1e-9)
        val e = EnergyEstimator.estimate(EnergyInput(BiologicalSex.MALE, 30, 80.0, 180.0, ActivityLevel.MODERATE, Objective.MAINTAIN))
        assertEquals(2759.0, e.totalExpenditureKcal, 1e-9)
        assertEquals(2759.0, e.targetKcal, 1e-9)
        val f = EnergyEstimator.estimate(EnergyInput(BiologicalSex.FEMALE, 25, 60.0, 165.0, ActivityLevel.MODERATE, Objective.MAINTAIN))
        assertEquals(2085.1375, f.totalExpenditureKcal, 1e-9)
    }

    @Test
    fun `objetivo aplica o ajuste e macros fecham a energia`() {
        val e = EnergyEstimator.estimate(
            EnergyInput(BiologicalSex.MALE, 30, 80.0, 180.0, ActivityLevel.MODERATE, Objective.LOSE, adjustmentPercent = 20.0, proteinPerKg = 2.0, fatPercent = 25.0),
        )
        assertEquals(2759.0 * 0.8, e.targetKcal, 1e-9)
        assertEquals(160.0, e.proteinG, 1e-9)
        assertEquals(e.targetKcal, e.proteinG * 4 + e.fatG * 9 + e.carbohydrateG * 4, 1e-6)
        assertEquals(e.targetKcal / 1000 * 14, e.fiberG, 1e-9)
    }

    @Test
    fun `estimativa avisa quando a meta fica abaixo da TMB`() {
        val e = EnergyEstimator.estimate(EnergyInput(BiologicalSex.FEMALE, 30, 60.0, 165.0, ActivityLevel.SEDENTARY, Objective.LOSE, adjustmentPercent = 30.0))
        assertTrue(e.warnings.any { "basal" in it })
    }

    @Test
    fun `entradas fora da faixa sao rejeitadas`() {
        val bad = EnergyInput(BiologicalSex.MALE, 12, 80.0, 180.0, ActivityLevel.MODERATE, Objective.MAINTAIN)
        assertTrue(EnergyEstimator.validate(bad).isNotEmpty())
        assertFailsWith<IllegalArgumentException> { EnergyEstimator.estimate(bad) }
    }

    @Test
    fun `conversao de macros entre gramas e percentual`() {
        assertEquals(150.0, MacroEnergy.gramsFromPercent(2000.0, 30.0, MacroEnergy.KCAL_PER_G_CARBOHYDRATE), 1e-9)
        assertEquals(30.0, MacroEnergy.percentFromGrams(2000.0, 150.0, MacroEnergy.KCAL_PER_G_CARBOHYDRATE), 1e-9)
    }
}
