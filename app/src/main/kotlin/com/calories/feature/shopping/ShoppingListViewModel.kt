package com.calories.feature.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.calories.domain.model.ShoppingItem
import com.calories.domain.model.ShoppingList
import com.calories.domain.repository.ShoppingRepository
import com.calories.domain.usecase.DomainException
import com.calories.navigation.ShoppingListRoute
import com.calories.ui.UiEvent
import com.calories.ui.format.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `list == null` quando a lista foi excluída. */
data class ShoppingListState(val list: ShoppingList?)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shopping: ShoppingRepository,
) : ViewModel() {
    private val listId = savedStateHandle.toRoute<ShoppingListRoute>().listId
    val state: StateFlow<ShoppingListState?> = shopping.observeList(listId).map { ShoppingListState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _events.send(UiEvent.Message(it.message ?: "Não foi possível concluir")) }
    }

    fun toggle(item: ShoppingItem) = launchSafely { shopping.setChecked(item.id, !item.isChecked) }

    fun setQuantity(item: ShoppingItem, quantity: Double?) = launchSafely {
        if (quantity != null && (quantity <= 0 || quantity > 100_000)) throw DomainException("Quantidade inválida")
        shopping.updateQuantity(item.id, quantity)
    }

    fun addManual(name: String, quantity: Double?, unit: String?) = launchSafely {
        if (name.isBlank()) throw DomainException("Informe o nome do item")
        shopping.addManual(listId, name.trim().take(80), quantity, unit?.trim()?.ifEmpty { null }?.take(12))
    }

    fun delete(item: ShoppingItem) = launchSafely { shopping.deleteItem(item.id) }
    fun clearChecked() = launchSafely { shopping.clearChecked(listId) }
    fun deleteList() = launchSafely { shopping.deleteList(listId); _events.send(UiEvent.Close) }

    /** Texto para compartilhar (WhatsApp, notas…): só os itens que faltam comprar. */
    fun shareText(list: ShoppingList): String = buildString {
        appendLine(list.name)
        list.items.filter { !it.isChecked }.forEach { i ->
            append("• ").append(i.name)
            Fmt.quantity(i.quantity, i.unit).takeIf { it.isNotEmpty() }?.let { append(" — ").append(it) }
            appendLine()
        }
    }.trimEnd()
}
