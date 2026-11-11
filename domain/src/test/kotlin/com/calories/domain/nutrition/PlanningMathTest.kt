package com.calories.domain.nutrition

import com.calories.domain.model.DailyTotals
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.PlannedItem
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanningMathTest {
    private val d = LocalDate.of(2026, 11, 11) // quarta-feira

    private fun item(foodId: Long, name: String, prep: Preparation, amount: Double, date: LocalDate = d, unit: MeasureUnit = MeasureUnit.GRAM) =
        PlannedItem(0, date, 1, foodId, name, prep, amount, unit, emptyMap(), null, 0)

    @Test
    fun `lista de compras soma 150 + 150 + 100 g de frango = 400 g (exemplo do requisito)`() {
        val list = ShoppingListConsolidator.consolidate(
            listOf(
                item(1, "Frango, peito, sem pele, grelhado", Preparation.GRILLED, 150.0),
                item(1, "Frango, peito, sem pele, grelhado", Preparation.GRILLED, 150.0),
                item(1, "Frango, peito, sem pele, grelhado", Preparation.GRILLED, 100.0, d.plusDays(1)),
            ),
        )
        assertEquals(1, list.size)
        assertEquals(400.0, list.single().quantity)
        assertEquals("g", list.single().unit)
        assertTrue(list.single().detail!!.contains("peso do alimento pronto"))
    }

    @Test
    fun `preparos e unidades diferentes nao se somam e a lista sai em ordem alfabetica`() {
        val list = ShoppingListConsolidator.consolidate(
            listOf(
                item(3, "Arroz, tipo 1, cozido", Preparation.COOKED, 150.0),
                item(4, "Arroz, tipo 1, cru", Preparation.RAW, 80.0),
                item(5, "Leite fluido", Preparation.READY_TO_EAT, 200.0, unit = MeasureUnit.MILLILITER),
                item(5, "Leite fluido", Preparation.READY_TO_EAT, 100.0, unit = MeasureUnit.GRAM),
                item(2, "Abacate, cru", Preparation.RAW, 50.0),
            ),
        )
        assertEquals(listOf("Abacate, cru", "Arroz, tipo 1, cozido", "Arroz, tipo 1, cru", "Leite fluido", "Leite fluido"), list.map { it.name })
        assertEquals(setOf("ml", "g"), list.filter { it.name == "Leite fluido" }.map { it.unit }.toSet())
        assertEquals("Cru", list.first { it.name == "Arroz, tipo 1, cru" }.detail)
    }

    private fun day(date: LocalDate, kcal: Double, goal: Double?, entries: Int = 3) = DailyTotals(
        date, entries,
        NutrientAggregator.sum(if (entries == 0) emptyList() else listOf(mapOf(Nutrient.ENERGY to NutrientValue.Measured(kcal), Nutrient.PROTEIN to NutrientValue.Measured(kcal / 20)))),
        goal?.let { NutritionGoal(d.minusDays(30), it, null, null, null, null, GoalOrigin.MANUAL) },
    )

    @Test
    fun `estatisticas do periodo usam so dias com registro e contam dias dentro da meta`() {
        val week = Periods.week(d)
        val stats = PeriodStats.of(
            week.start, week.endInclusive,
            listOf(day(d, 2000.0, 2100.0), day(d.minusDays(1), 2400.0, 2100.0), day(d.minusDays(2), 1800.0, null), day(d.plusDays(10), 9999.0, 2100.0)),
        )
        assertEquals(3, stats.daysWithEntries)
        assertEquals((2000.0 + 2400 + 1800) / 3, stats.averages.getValue(Nutrient.ENERGY), 1e-9)
        assertEquals(2, stats.daysWithGoal)
        assertEquals(1, stats.daysWithinGoal) // 2000 está a 4,8% de 2100; 2400 está a 14%
    }

    @Test
    fun `semana de segunda a domingo, mes e selecao por dia da semana`() {
        assertEquals(LocalDate.of(2026, 11, 9)..LocalDate.of(2026, 11, 15), Periods.week(d))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 30), Periods.month(d))
        val mwf = Periods.select(Periods.month(d), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(13, mwf.size)
        assertTrue(mwf.all { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) })
        assertEquals(30, Periods.select(Periods.month(d), emptySet()).size)
    }

    @Test
    fun `peso valida faixa e data e calcula media movel`() {
        assertTrue(WeightRules.validate(78.4, d, d).isEmpty())
        assertTrue(WeightRules.validate(15.0, d, d).isNotEmpty())
        assertTrue(WeightRules.validate(null, d, d).isNotEmpty())
        assertTrue(WeightRules.validate(80.0, d.plusDays(1), d).isNotEmpty())
        assertEquals(listOf(80.0, 79.0, 78.0), WeightRules.movingAverage(listOf(80.0, 78.0, 76.0), window = 7))
        assertEquals(listOf(80.0, 79.0, 77.0), WeightRules.movingAverage(listOf(80.0, 78.0, 76.0), window = 2))
    }
}
