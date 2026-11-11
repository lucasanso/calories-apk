package com.calories.domain.nutrition

import com.calories.domain.model.DailyTotals
import com.calories.domain.model.MealSlot
import com.calories.domain.model.NewShoppingItem
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.PlannedItem
import com.calories.domain.model.SlotGroup
import com.calories.domain.model.goalOn
import java.text.Collator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/**
 * Lista de compras consolidada (docs/04 §4.8): soma por alimento EXATO e unidade.
 * Preparos diferentes não se somam (arroz cru ≠ arroz cozido). Para alimentos preparados,
 * a quantidade é do alimento pronto: converter para peso cru exigiria fatores de cocção
 * que não estão nas fontes, e eles não são inventados.
 */
object ShoppingListConsolidator {
    private val PREPARED = setOf(
        Preparation.COOKED, Preparation.GRILLED, Preparation.ROASTED, Preparation.FRIED,
        Preparation.SAUTEED, Preparation.PREPARED_DISH,
    )

    fun consolidate(items: List<PlannedItem>): List<NewShoppingItem> {
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }
        return items.groupBy { it.foodId to it.unit }
            .map { (key, group) ->
                val first = group.first()
                NewShoppingItem(
                    foodId = key.first,
                    name = first.foodName,
                    detail = if (first.preparation in PREPARED) "${first.preparation.labelPt} · peso do alimento pronto" else first.preparation.labelPt,
                    quantity = group.sumOf { it.amount },
                    unit = key.second.symbol,
                )
            }
            .sortedWith(compareBy(collator) { it.name })
    }
}

/** Estatísticas de um período do histórico (semana, mês). Médias só sobre dias com registro. */
data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val daysWithEntries: Int,
    val averages: Map<Nutrient, Double>,
    /** Dias com meta definida em que o consumo ficou a até ±10% da meta de calorias. */
    val daysWithinGoal: Int,
    val daysWithGoal: Int,
) {
    companion object {
        const val GOAL_TOLERANCE = 0.10

        fun of(from: LocalDate, to: LocalDate, days: List<DailyTotals>): PeriodStats {
            val inRange = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) && it.entryCount > 0 }
            val averages = if (inRange.isEmpty()) emptyMap() else Nutrient.entries.associateWith { n -> inRange.sumOf { it.totals[n].sum } / inRange.size }
            val withGoal = inRange.filter { it.goal != null }
            val within = withGoal.count { d -> abs(d.totals.energyKcal - d.goal!!.energyKcal) <= d.goal.energyKcal * GOAL_TOLERANCE }
            return PeriodStats(from, to, inRange.size, averages, within, withGoal.size)
        }
    }
}

/** Um dia de um período do histórico, com ou sem registro, e a meta vigente nele. */
data class PeriodDay(val date: LocalDate, val totals: DailyTotals?, val goal: NutritionGoal?)

object PeriodSeries {
    /** Todos os dias do intervalo, em ordem (dias sem registro ficam com [PeriodDay.totals] nulo). */
    fun build(range: ClosedRange<LocalDate>, days: List<DailyTotals>, goals: List<NutritionGoal>): List<PeriodDay> {
        val byDate = days.filter { it.entryCount > 0 }.associateBy { it.date }
        return Periods.days(range).map { d -> PeriodDay(d, byDate[d], goals.goalOn(d)) }
    }

    /** Média de uma meta sobre os dias com registro (a meta pode mudar no meio do período). */
    fun averageGoal(days: List<PeriodDay>, select: (NutritionGoal) -> Double?): Double? =
        days.filter { it.totals != null }.mapNotNull { it.goal?.let(select) }.takeIf { it.isNotEmpty() }?.average()
}

/** Agrupa itens por refeição: refeições ativas (mesmo vazias) e inativas que tenham itens, na ordem do dia. */
object SlotGrouping {
    fun <T> group(slots: List<MealSlot>, items: List<T>, slotOf: (T) -> Long, consumedOf: (T) -> Map<Nutrient, NutrientValue>): List<SlotGroup<T>> {
        val bySlot = items.groupBy(slotOf)
        return slots.sortedBy { it.sortOrder }
            .filter { it.isActive || !bySlot[it.id].isNullOrEmpty() }
            .map { slot -> bySlot[slot.id].orEmpty().let { list -> SlotGroup(slot, list, NutrientAggregator.sum(list.map(consumedOf))) } }
    }
}

object Periods {
    /** Semana de segunda a domingo que contém [date]. */
    fun week(date: LocalDate): ClosedRange<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start..start.plusDays(6)
    }

    fun month(date: LocalDate): ClosedRange<LocalDate> {
        val start = date.withDayOfMonth(1)
        return start..start.plusMonths(1).minusDays(1)
    }

    fun days(range: ClosedRange<LocalDate>): List<LocalDate> =
        generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.endInclusive) }.toList()

    /** Datas do intervalo que caem nos dias da semana escolhidos (vazio = todos). */
    fun select(range: ClosedRange<LocalDate>, weekdays: Set<DayOfWeek>): List<LocalDate> =
        days(range).filter { weekdays.isEmpty() || it.dayOfWeek in weekdays }
}

object WeightRules {
    val RANGE = 20.0..400.0

    fun validate(weightKg: Double?, date: LocalDate, today: LocalDate): List<String> = buildList {
        if (weightKg == null || !weightKg.isFinite()) add("Informe o peso.")
        else if (weightKg !in RANGE) add("Peso deve estar entre 20 e 400 kg.")
        if (date.isAfter(today)) add("A data não pode estar no futuro.")
    }

    /** Média móvel simples dos últimos [window] registros (inclusive o atual). */
    fun movingAverage(values: List<Double>, window: Int = 7): List<Double> =
        values.indices.map { i -> values.subList(maxOf(0, i - window + 1), i + 1).average() }
}
