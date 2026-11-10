package com.calories.domain.nutrition

/**
 * Nutrientes acompanhados pelo app. [key] é o nome estável usado no asset da biblioteca,
 * no banco e nos backups; nunca deve ser renomeado sem migração.
 */
enum class Nutrient(val key: String, val unit: String, val labelPt: String) {
    ENERGY("energyKcal", "kcal", "Energia"),
    PROTEIN("proteinG", "g", "Proteínas"),
    CARBOHYDRATE("carbohydrateG", "g", "Carboidratos"),
    FAT("fatG", "g", "Gorduras totais"),
    SATURATED_FAT("saturatedFatG", "g", "Gorduras saturadas"),
    FIBER("fiberG", "g", "Fibras"),
    SODIUM("sodiumMg", "mg", "Sódio");

    companion object {
        fun fromKey(key: String): Nutrient =
            entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Nutriente desconhecido: $key")
    }
}
