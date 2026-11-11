package com.calories.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Esquema v2 (Fase 4): peso, planos, calendário e listas de compras. */

@Entity(tableName = "weight_record", indices = [Index(value = ["date"], unique = true)])
data class WeightRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val weightKg: Double,
    val note: String?,
    val createdAt: Long,
)

@Entity(tableName = "diet_plan")
data class DietPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "diet_plan_item",
    foreignKeys = [
        ForeignKey(entity = DietPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MealSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["planId"]), Index(value = ["slotId"]), Index(value = ["foodId"])],
)
data class DietPlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val slotId: Long,
    val foodId: Long,
    val amount: Double,
    val sortOrder: Int,
)

/** Item planejado para uma data (cópia de um plano ou adicionado à mão). */
@Entity(
    tableName = "planned_item",
    foreignKeys = [
        ForeignKey(entity = MealSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DietPlanEntity::class, parentColumns = ["id"], childColumns = ["sourcePlanId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["date"]), Index(value = ["slotId"]), Index(value = ["foodId"]), Index(value = ["sourcePlanId"])],
)
data class PlannedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val slotId: Long,
    val foodId: Long,
    val amount: Double,
    val sourcePlanId: Long?,
    val sortOrder: Int,
)

@Entity(tableName = "shopping_list")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fromDate: Long?,
    val toDate: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "shopping_list_item",
    foreignKeys = [
        ForeignKey(entity = ShoppingListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["listId"]), Index(value = ["foodId"])],
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val foodId: Long?,
    val name: String,
    val detail: String?,
    val quantity: Double?,
    val unit: String?,
    val isChecked: Boolean,
    val isManual: Boolean,
    val sortOrder: Int,
)
