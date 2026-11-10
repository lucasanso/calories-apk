package com.calories.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionRulesTest {

    private fun profile(
        energy: Double? = null, protein: Double? = null, carbs: Double? = null, fat: Double? = null,
        saturated: NutrientValue? = null, fiber: Double? = null, sodium: Double? = null, reference: Double = 100.0,
    ): NutrientProfile {
        val values = buildMap<Nutrient, NutrientValue> {
            energy?.let { put(Nutrient.ENERGY, NutrientValue.Measured(it)) }
            protein?.let { put(Nutrient.PROTEIN, NutrientValue.Measured(it)) }
            carbs?.let { put(Nutrient.CARBOHYDRATE, NutrientValue.Measured(it)) }
            fat?.let { put(Nutrient.FAT, NutrientValue.Measured(it)) }
            saturated?.let { put(Nutrient.SATURATED_FAT, it) }
            fiber?.let { put(Nutrient.FIBER, NutrientValue.Measured(it)) }
            sodium?.let { put(Nutrient.SODIUM, NutrientValue.Measured(it)) }
        }
        return NutrientProfile(reference, MeasureUnit.GRAM, values)
    }

    private fun codes(p: NutrientProfile) = NutritionRules.check(p).map { it.code }

    @Test
    fun `perfil coerente nao gera achados`() {
        // Frango, peito, sem pele, grelhado (TACO 4ª ed., item 410).
        val p = profile(energy = 159.0, protein = 32.0, carbs = 0.0, fat = 2.5, saturated = NutrientValue.Measured(0.9), sodium = 50.0)
        assertEquals(emptyList(), codes(p))
    }

    @Test
    fun `macronutriente acima de 100 g por 100 g e erro`() {
        assertEquals(listOf("E4"), codes(profile(protein = 100.5)))
    }

    @Test
    fun `limite de 100 g considera a quantidade de referencia`() {
        // 40 g de proteína em 30 g de produto = 133 g/100 g: impossível.
        assertEquals(listOf("E4"), codes(profile(protein = 40.0, reference = 30.0)))
        assertEquals(emptyList(), codes(profile(protein = 25.0, reference = 30.0)))
    }

    @Test
    fun `sodio acima do sal puro e erro`() {
        assertEquals(listOf("E4"), codes(profile(sodium = 40_001.0)))
    }

    @Test
    fun `saturada maior que gordura total e erro`() {
        // Caso real: TACO item 368 publica 9,7 g de saturada para 2,4 g de lipídios.
        assertEquals(listOf("E5"), codes(profile(fat = 2.4, saturated = NutrientValue.Measured(9.7))))
    }

    @Test
    fun `saturada igual a gordura dentro da tolerancia e aceita`() {
        assertEquals(emptyList(), codes(profile(fat = 2.4, saturated = NutrientValue.Measured(2.5))))
    }

    @Test
    fun `fibra maior que carboidrato e aviso`() {
        assertEquals(listOf("W2"), codes(profile(carbs = 2.0, fiber = 3.0)))
    }

    @Test
    fun `energia muito diferente de Atwater e aviso, nunca correcao`() {
        val p = profile(energy = 300.0, protein = 10.0, carbs = 10.0, fat = 1.0) // 4*10+4*10+9 = 89
        val findings = NutritionRules.check(p)
        assertEquals(listOf("W1"), findings.map { it.code })
        assertEquals(NutritionRules.Severity.WARNING, findings.single().severity)
    }

    @Test
    fun `Atwater ignora valores pequenos e dados ausentes`() {
        assertNull(NutritionRules.atwaterDeviation(profile(energy = 9.0, protein = 0.1, carbs = 0.1, fat = 0.1)))
        assertNull(NutritionRules.atwaterDeviation(profile(energy = 300.0, protein = 10.0)))
    }

    @Test
    fun `traco e nao aplicavel somam zero, ausente nao soma`() {
        assertEquals(0.0, NutrientValue.Trace.contribution)
        assertEquals(0.0, NutrientValue.NotApplicable.contribution)
        assertNull(NutrientValue.NotAvailable.contribution)
        assertEquals(1.5, NutrientValue.Measured(1.5).contribution)
    }

    @Test
    fun `valor medido negativo ou infinito e rejeitado`() {
        assertFailsWith<IllegalArgumentException> { NutrientValue.Measured(-0.1) }
        assertFailsWith<IllegalArgumentException> { NutrientValue.Measured(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { NutrientValue.Measured(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `quantidade de referencia precisa ser positiva`() {
        assertFailsWith<IllegalArgumentException> { NutrientProfile(0.0, MeasureUnit.GRAM, emptyMap()) }
    }

    @Test
    fun `nutriente nao informado e ausente`() {
        val p = profile(energy = 100.0)
        assertTrue(p[Nutrient.FIBER] == NutrientValue.NotAvailable)
        assertNull(p.per100(Nutrient.FIBER))
    }
}
