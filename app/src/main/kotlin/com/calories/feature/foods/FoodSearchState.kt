package com.calories.feature.foods

import com.calories.domain.model.Food
import com.calories.domain.model.FoodFilter
import com.calories.domain.repository.FoodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest

/** Estado de busca compartilhado entre a aba Alimentos e a seleção de alimento para uma refeição. */
class FoodSearchState(private val foods: FoodRepository, initialFilter: FoodFilter = FoodFilter.ALL) {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(initialFilter)
    val categoryId = MutableStateFlow<Long?>(null)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: Flow<List<Food>> = combine(query.debounce(150), filter, categoryId) { q, f, c -> Triple(q, f, c) }
        .flatMapLatest { (q, f, c) -> foods.search(q, f, c) }
}
