package com.calories.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calories.domain.nutrition.PeriodDay
import com.calories.domain.nutrition.PeriodSeries
import com.calories.domain.nutrition.PeriodStats
import com.calories.domain.nutrition.Periods
import com.calories.domain.repository.DiaryRepository
import com.calories.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class HistoryPeriod(val label: String) { WEEK("Semana"), MONTH("Mês") }

data class ConsumptionState(
    val period: HistoryPeriod,
    val range: ClosedRange<LocalDate>,
    /** Todos os dias do período, em ordem. */
    val days: List<PeriodDay>,
    val stats: PeriodStats,
    val canGoForward: Boolean,
) {
    val daysWithEntries: List<PeriodDay> get() = days.filter { it.totals != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(diary: DiaryRepository, goals: GoalRepository, clock: Clock) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    private val period = MutableStateFlow(HistoryPeriod.WEEK)
    private val anchor = MutableStateFlow(today)

    val state: StateFlow<ConsumptionState?> = combine(period, anchor) { p, a -> p to rangeOf(p, a) }
        .flatMapLatest { (p, range) ->
            combine(diary.observeDailyTotals(range.start, range.endInclusive), goals.observeGoals()) { days, goalList ->
                ConsumptionState(
                    period = p,
                    range = range,
                    days = PeriodSeries.build(range, days, goalList),
                    stats = PeriodStats.of(range.start, range.endInclusive, days),
                    canGoForward = range.endInclusive < today,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(p: HistoryPeriod) { period.value = p }
    fun previous() = anchor.update { if (period.value == HistoryPeriod.WEEK) it.minusWeeks(1) else it.minusMonths(1) }
    fun next() = anchor.update { if (period.value == HistoryPeriod.WEEK) it.plusWeeks(1) else it.plusMonths(1) }
    fun goToToday() { anchor.value = today }

    private fun rangeOf(p: HistoryPeriod, date: LocalDate) = if (p == HistoryPeriod.WEEK) Periods.week(date) else Periods.month(date)
}
