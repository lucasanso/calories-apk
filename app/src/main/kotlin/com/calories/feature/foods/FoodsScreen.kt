package com.calories.feature.foods

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.Food
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.FoodFilter
import com.calories.domain.repository.FoodRepository
import com.calories.ui.components.EmptyState
import com.calories.ui.components.FoodListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FoodsViewModel @Inject constructor(foods: FoodRepository) : ViewModel() {
    val search = FoodSearchState(foods)
    val results: StateFlow<List<Food>?> = search.results.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<FoodCategory>> = foods.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsRoute(onOpenFood: (Long) -> Unit, onNewFood: () -> Unit, viewModel: FoodsViewModel = hiltViewModel()) {
    val query by viewModel.search.query.collectAsStateWithLifecycle()
    val filter by viewModel.search.filter.collectAsStateWithLifecycle()
    val categoryId by viewModel.search.categoryId.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    FoodsScreen(query, { viewModel.search.query.value = it }, filter, { viewModel.search.filter.value = it }, categories, categoryId,
        { viewModel.search.categoryId.value = it }, results, onOpenFood, onNewFood)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsScreen(
    query: String,
    onQuery: (String) -> Unit,
    filter: FoodFilter,
    onFilter: (FoodFilter) -> Unit,
    categories: List<FoodCategory>,
    categoryId: Long?,
    onCategory: (Long?) -> Unit,
    results: List<Food>?,
    onOpenFood: (Long) -> Unit,
    onNewFood: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Alimentos") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewFood, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Novo alimento") },
                modifier = Modifier.semantics { contentDescription = "Novo alimento" })
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FoodSearchField(query, onQuery, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            FoodFilterRow(filter, onFilter, categories, categoryId, onCategory)
            FoodResults(results, filter, query, onOpenFood, PaddingValues(bottom = 88.dp))
        }
    }
}

@Composable
fun FoodResults(results: List<Food>?, filter: FoodFilter, query: String, onOpen: (Long) -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    when {
        results == null -> Unit
        results.isEmpty() -> EmptyState(
            title = if (query.isNotBlank()) "Nada encontrado" else when (filter) {
                FoodFilter.FAVORITES -> "Nenhum favorito"
                FoodFilter.RECENT -> "Nenhum alimento usado ainda"
                FoodFilter.CUSTOM -> "Nenhum alimento seu"
                FoodFilter.ALL -> "Biblioteca vazia"
            },
            message = if (query.isNotBlank()) "Tente outro termo, sem acentos ou só o começo da palavra (ex.: “frang grel”)."
            else "Use a estrela no detalhe do alimento para favoritar, ou cadastre seus próprios alimentos.",
            icon = Icons.Outlined.SearchOff,
        )
        else -> LazyColumn(contentPadding = contentPadding) {
            item { Text("${results.size} alimento(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(results, key = { it.id }) { food -> FoodListItem(food, onClick = { onOpen(food.id) }) }
        }
    }
}
