package com.calories.domain.nutrition

import com.calories.domain.model.DailyTotals
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.MealSlot
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

    @Test
    fun `serie do periodo tem todos os dias e a meta vigente em cada um`() {
        val week = Periods.week(d) // 9 a 15/11
        val goals = listOf(
            NutritionGoal(LocalDate.of(2026, 1, 1), 2000.0, 120.0, null, null, null, GoalOrigin.MANUAL),
            NutritionGoal(LocalDate.of(2026, 11, 12), 1800.0, 140.0, null, null, null, GoalOrigin.MANUAL),
        )
        fun day(date: LocalDate, kcal: Double, count: Int = 1) =
            DailyTotals(date, count, NutrientAggregator.sum(List(count) { mapOf(Nutrient.ENERGY to NutrientValue.Measured(kcal / count)) }), null)
        val series = PeriodSeries.build(week, listOf(day(d, 1900.0), day(d.plusDays(2), 1700.0, 2), day(d.plusDays(3), 0.0, 0)), goals)
        assertEquals(7, series.size)
        assertEquals(LocalDate.of(2026, 11, 9), series.first().date)
        assertEquals(listOf(null, null, 1900.0, null, 1700.0, null, null), series.map { it.totals?.totals?.energyKcal })
        assertEquals(listOf(2000.0, 2000.0, 2000.0, 1800.0, 1800.0, 1800.0, 1800.0), series.map { it.goal?.energyKcal })
        // Média das metas só nos dias com registro: (2000 + 1800) / 2.
        assertEquals(1900.0, PeriodSeries.averageGoal(series) { it.energyKcal })
        assertEquals(130.0, PeriodSeries.averageGoal(series) { it.proteinG })
        assertEquals(null, PeriodSeries.averageGoal(series) { it.fatG })
    }

    @Test
    fun `agrupamento por refeicao mostra ativas vazias e inativas so com itens`() {
        val slots = listOf(
            MealSlot(2, "Almoço", 720, 1, true), MealSlot(1, "Café da manhã", 420, 0, true),
            MealSlot(3, "Ceia", 1320, 2, false), MealSlot(4, "Lanche", 960, 3, false),
        )
        val items = listOf(
            item(10, "Arroz", Preparation.COOKED, 150.0).copy(slotId = 2, consumed = mapOf(Nutrient.ENERGY to NutrientValue.Measured(192.0))),
            item(11, "Feijão", Preparation.COOKED, 100.0).copy(slotId = 2, consumed = mapOf(Nutrient.ENERGY to NutrientValue.Measured(76.0))),
            item(12, "Iogurte", Preparation.RAW, 170.0).copy(slotId = 3, consumed = mapOf(Nutrient.ENERGY to NutrientValue.NotAvailable)),
        )
        val groups = SlotGrouping.group(slots, items, { it.slotId }, { it.consumed })
        assertEquals(listOf("Café da manhã", "Almoço", "Ceia"), groups.map { it.slot.name })
        assertEquals(0, groups[0].items.size)
        assertEquals(268.0, groups[1].totals.energyKcal)
        assertTrue(!groups[2].totals[Nutrient.ENERGY].hasAnyData)
    }
}
