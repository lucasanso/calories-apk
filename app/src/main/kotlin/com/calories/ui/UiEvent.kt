package com.calories.ui

/** Evento de uma vez só para a tela (snackbar, navegação). */
sealed interface UiEvent {
    data class Message(val text: String, val undo: (() -> Unit)? = null) : UiEvent
    data class OpenFood(val foodId: Long) : UiEvent
    data object Close : UiEvent
}
