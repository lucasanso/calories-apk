package com.calories.feature.foods

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.FoodFilter

@Composable
fun FoodSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Buscar alimento") {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Limpar busca") } },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

val FILTER_LABELS = linkedMapOf(
    FoodFilter.ALL to "Todos", FoodFilter.RECENT to "Recentes", FoodFilter.FAVORITES to "Favoritos", FoodFilter.CUSTOM to "Meus",
)

@Composable
fun FoodFilterRow(
    filter: FoodFilter,
    onFilter: (FoodFilter) -> Unit,
    categories: List<FoodCategory>,
    categoryId: Long?,
    onCategory: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FILTER_LABELS.forEach { (f, label) -> FilterChip(selected = filter == f, onClick = { onFilter(f) }, label = { Text(label) }) }
        Box {
            FilterChip(
                selected = categoryId != null,
                onClick = { menu = true },
                label = { Text(categories.firstOrNull { it.id == categoryId }?.name ?: "Categoria") },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Todas as categorias") }, onClick = { menu = false; onCategory(null) })
                categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { menu = false; onCategory(c.id) }) }
            }
        }
        extra?.invoke()
    }
}
