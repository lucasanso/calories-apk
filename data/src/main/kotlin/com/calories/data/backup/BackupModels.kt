package com.calories.data.backup

import kotlinx.serialization.Serializable

/**
 * Arquivo de backup/exportação do Calories (JSON). A biblioteca nutricional não entra:
 * alimentos da biblioteca são referenciados pela chave estável (`TACO4:410`) e os
 * personalizados por um id local do arquivo.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    /** FULL (backup completo) ou PLANS (só planos). */
    val kind: String,
    val schemaVersion: Int,
    val libraryVersion: Int,
    val exportedAt: String,
    val mealSlots: List<BSlot> = emptyList(),
    val customFoods: List<BFood> = emptyList(),
    val foodUsage: List<BUsage> = emptyList(),
    val diary: List<BDiaryMeal> = emptyList(),
    val goals: List<BGoal> = emptyList(),
    val weights: List<BWeight> = emptyList(),
    val templates: List<BTemplate> = emptyList(),
    val plans: List<BPlan> = emptyList(),
    val plannedItems: List<BPlannedItem> = emptyList(),
    val shoppingLists: List<BShoppingList> = emptyList(),
    val settings: BSettings? = null,
    val profile: BProfile? = null,
) {
    companion object {
        const val FORMAT = "calories-backup"
        const val FORMAT_VERSION = 1
        const val KIND_FULL = "FULL"
        const val KIND_PLANS = "PLANS"
    }
}

@Serializable data class FoodRef(val libraryKey: String? = null, val customId: Long? = null)

@Serializable data class BSlot(val id: Long, val name: String, val defaultTimeMinutes: Int? = null, val sortOrder: Int, val isActive: Boolean)

@Serializable
data class BNutrients(
    val energyKcal: Double? = null,
    val proteinG: Double? = null,
    val carbohydrateG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
    val traceMask: Int = 0,
    val notApplicableMask: Int = 0,
)

@Serializable
data class BFood(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val categoryCode: String? = null,
    val preparation: String,
    val referenceAmount: Double,
    val referenceUnit: String,
    val nutrients: BNutrients,
    val derivedFromKey: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
)

@Serializable data class BUsage(val food: FoodRef, val isFavorite: Boolean, val useCount: Int, val lastUsedAt: Long? = null, val lastAmount: Double? = null)

@Serializable data class BDiaryMeal(val date: String, val slotId: Long? = null, val name: String, val sortOrder: Int, val entries: List<BEntry>)

@Serializable
data class BEntry(
    val food: FoodRef? = null,
    val foodName: String,
    val brand: String? = null,
    val preparation: String,
    val dataSourceCode: String,
    val amount: Double,
    val unit: String,
    val refAmount: Double,
    val reference: BNutrients,
    val consumed: BNutrients,
    val sortOrder: Int,
    val createdAt: Long,
)

@Serializable
data class BGoal(
    val effectiveFrom: String,
    val energyKcal: Double,
    val proteinG: Double? = null,
    val carbohydrateG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val origin: String,
)

@Serializable data class BWeight(val date: String, val weightKg: Double, val note: String? = null)

@Serializable data class BTemplate(val name: String, val items: List<BAmount>)

@Serializable data class BAmount(val food: FoodRef, val amount: Double, val slotId: Long? = null)

@Serializable data class BPlan(val id: Long, val name: String, val description: String? = null, val items: List<BAmount>)

@Serializable data class BPlannedItem(val date: String, val slotId: Long, val food: FoodRef, val amount: Double, val planId: Long? = null)

@Serializable data class BShoppingList(val name: String, val fromDate: String? = null, val toDate: String? = null, val createdAt: Long, val items: List<BShoppingItem>)

@Serializable
data class BShoppingItem(
    val food: FoodRef? = null,
    val name: String,
    val detail: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val isChecked: Boolean,
    val isManual: Boolean,
)

@Serializable data class BSettings(val themeMode: String, val dynamicColor: Boolean)

@Serializable
data class BProfile(
    val sex: String,
    val ageYears: Int,
    val weightKg: Double,
    val heightCm: Double,
    val activity: String,
    val objective: String,
    val adjustmentPercent: Double,
    val proteinPerKg: Double,
    val fatPercent: Double,
)
