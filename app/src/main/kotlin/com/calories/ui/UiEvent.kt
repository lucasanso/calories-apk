package com.calories.ui

import java.time.LocalDate

/** Evento de uma vez só para a tela (snackbar, navegação). */
sealed interface UiEvent {
    data class Message(val text: String, val undo: (() -> Unit)? = null) : UiEvent
    data class OpenFood(val foodId: Long) : UiEvent
    /** Abrir um item recém-criado (plano, lista de compras). */
    data class OpenItem(val id: Long) : UiEvent
    /** Alimentos registrados no diário de [date] (a tela oferece um atalho para o dia). */
    data class LoggedToDiary(val text: String, val date: LocalDate) : UiEvent
    data object Close : UiEvent
}
