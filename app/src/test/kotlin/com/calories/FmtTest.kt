package com.calories

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientTotal
import com.calories.domain.nutrition.NutrientValue
import com.calories.ui.format.Fmt
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Arredondamento só na apresentação (docs/04 §4.5), em pt-BR. */
class FmtTest {
    @Test
    fun `energia - resumo inteiro e detalhe com uma casa`() {
        assertEquals("248 kcal", Fmt.nutrient(Nutrient.ENERGY, 247.5))
        assertEquals("247,5 kcal", Fmt.nutrient(Nutrient.ENERGY, 247.5, detail = true))
        assertEquals("159 kcal", Fmt.nutrient(Nutrient.ENERGY, 159.0, detail = true))
        assertEquals("1.075 kcal", Fmt.nutrient(Nutrient.ENERGY, 1074.6))
    }

    @Test
    fun `HALF_UP sobre a representacao decimal curta`() {
        assertEquals("0,2", Fmt.number(0.15, 1)) // 0,15 em binário é 0,1499…; o app arredonda o decimal 0,15
        assertEquals("0,1", Fmt.number(0.05, 1))
        assertEquals("3,8", Fmt.number(2.5 * 1.5, 1))
        assertEquals("77 mg", Fmt.nutrient(Nutrient.SODIUM, 76.5))
    }

    @Test
    fun `status especiais e totais parciais`() {
        assertEquals("Tr", Fmt.value(Nutrient.SODIUM, NutrientValue.Trace))
        assertEquals("NA", Fmt.value(Nutrient.FIBER, NutrientValue.NotApplicable))
        assertEquals("—", Fmt.value(Nutrient.FIBER, NutrientValue.NotAvailable))
        assertEquals("7,0 g*", Fmt.total(Nutrient.FIBER, NutrientTotal(7.0, 1, 3)))
        assertEquals("—", Fmt.total(Nutrient.FIBER, NutrientTotal(0.0, 2, 2)))
        assertEquals("0,0 g", Fmt.total(Nutrient.FIBER, NutrientTotal(0.0, 0, 0)))
    }

    @Test
    fun `entrada aceita virgula, ponto e milhar`() {
        assertEquals(150.0, Fmt.parse("150"))
        assertEquals(150.5, Fmt.parse("150,5"))
        assertEquals(150.5, Fmt.parse("150.5"))
        assertEquals(1250.5, Fmt.parse("1.250,5"))
        assertNull(Fmt.parse(""))
        assertNull(Fmt.parse("abc"))
        assertEquals("1250,5", Fmt.input(1250.5))
        assertEquals("100", Fmt.input(100.0))
    }

    @Test
    fun `quantidades, percentuais e datas`() {
        assertEquals("150 g", Fmt.amount(150.0, MeasureUnit.GRAM))
        assertEquals("12,5 ml", Fmt.amount(12.5, MeasureUnit.MILLILITER))
        assertEquals("112%", Fmt.percent(1.12))
        val today = LocalDate.of(2026, 11, 11)
        assertEquals("Hoje", Fmt.relativeDate(today, today))
        assertEquals("Ontem", Fmt.relativeDate(today.minusDays(1), today))
        assertEquals("07:00", Fmt.time(420))
    }
}
