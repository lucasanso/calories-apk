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

    @Test
    fun `periodos, meses, dias da semana e quantidades de compras`() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals("9 a 15 de nov.", Fmt.period(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), today))
        assertEquals("26 de out. a 1 de nov.", Fmt.period(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1), today))
        assertEquals("29 de dez. a 4 de jan. de 2027", Fmt.period(LocalDate.of(2026, 12, 29), LocalDate.of(2027, 1, 4), today))
        assertEquals("Novembro de 2026", Fmt.monthYear(java.time.YearMonth.of(2026, 11)))
        assertEquals("seg", Fmt.weekdayShort(java.time.DayOfWeek.MONDAY))
        assertEquals("sáb", Fmt.weekdayShort(java.time.DayOfWeek.SATURDAY))
        assertEquals("11/11/2026", Fmt.shortDate(today))
        assertEquals("05/01", Fmt.dayMonth(LocalDate.of(2026, 1, 5)))
        assertEquals("1.050 g", Fmt.quantity(1050.0, "g"))
        assertEquals("2,5", Fmt.quantity(2.5, null))
        assertEquals("", Fmt.quantity(null, "g"))
    }
}
