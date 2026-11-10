package com.calories.domain.nutrition

/**
 * Estado de preparo do alimento. Cada preparo é um registro distinto: valores de
 * preparos diferentes nunca são misturados. Ver docs/03-biblioteca-nutricional.md §3.5.
 */
enum class Preparation(val labelPt: String) {
    RAW("Cru"),
    COOKED("Cozido"),
    GRILLED("Grelhado"),
    ROASTED("Assado"),
    FRIED("Frito"),
    SAUTEED("Refogado"),
    TOASTED("Torrado"),
    CANNED("Em conserva"),
    DRIED("Seco ou em pó"),
    READY_TO_EAT("Pronto para consumo"),
    PREPARED_DISH("Preparação culinária"),
}
