package com.calories.data.db.entity

import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Valores de nutrientes em colunas. `NULL` = sem dado; traço e "não aplicável" são gravados
 * como 0 com o bit correspondente em [traceMask]/[notApplicableMask] (bit = ordinal do Nutrient).
 */
data class NutrientColumns(
    val energyKcal: Double?,
    val proteinG: Double?,
    val carbohydrateG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val fiberG: Double?,
    val sodiumMg: Double?,
    val traceMask: Int,
    val notApplicableMask: Int,
)

@Entity(tableName = "data_source", indices = [Index(value = ["code"], unique = true)])
data class DataSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val edition: String?,
    val publisher: String?,
    val year: Int?,
    val url: String?,
    val license: String,
    val citation: String,
    val carbohydrateBasis: String,
    val accessedAt: String?,
)

@Entity(tableName = "food_category", indices = [Index(value = ["code"], unique = true)])
data class FoodCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val sortOrder: Int,
    val isSystem: Boolean,
)

@Entity(
    tableName = "food",
    foreignKeys = [
        ForeignKey(entity = DataSourceEntity::class, parentColumns = ["id"], childColumns = ["dataSourceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = FoodCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["derivedFromFoodId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["origin", "isArchived", "isDeprecated"]),
        Index(value = ["baseName"]),
        Index(value = ["categoryId"]),
        Index(value = ["dataSourceId"]),
        Index(value = ["derivedFromFoodId"]),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `LIBRARY` ou `CUSTOM`. */
    val origin: String,
    val sourceKey: String?,
    val dataSourceId: Long,
    val sourceCode: String?,
    val sourceDescription: String?,
    val sourceGroup: String?,
    val name: String,
    val baseName: String?,
    val brand: String?,
    /** Sinônimos separados por " | " (indexados na busca). */
    val aliases: String?,
    val categoryId: Long?,
    val preparation: String,
    val referenceAmount: Double,
    val referenceUnit: String,
    @Embedded val nutrients: NutrientColumns,
    /** Observações separadas por quebra de linha. */
    val notes: String?,
    val derivedFromFoodId: Long?,
    val libraryVersion: Int?,
    val isDeprecated: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Índice de busca textual (sem acentos, por prefixo) sincronizado com `food` por triggers do Room. */
@Fts4(contentEntity = FoodEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=1"])
@Entity(tableName = "food_fts")
data class FoodFtsEntity(
    val name: String,
    val brand: String?,
    val aliases: String?,
    val sourceDescription: String?,
)

/** Estado do usuário sobre um alimento; separado de `food` para a atualização da biblioteca nunca apagá-lo. */
@Entity(
    tableName = "food_usage",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["isFavorite"]), Index(value = ["lastUsedAt"])],
)
data class FoodUsageEntity(
    @PrimaryKey val foodId: Long,
    val isFavorite: Boolean,
    val useCount: Int,
    val lastUsedAt: Long?,
    val lastAmount: Double?,
)

@Entity(tableName = "meal_slot")
data class MealSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultTimeMinutes: Int?,
    val sortOrder: Int,
    val isActive: Boolean,
)

@Entity(
    tableName = "diary_meal",
    foreignKeys = [ForeignKey(entity = MealSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["date", "slotId"], unique = true), Index(value = ["slotId"])],
)
data class DiaryMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `LocalDate.toEpochDay()`. */
    val date: Long,
    val slotId: Long?,
    /** Nome do slot no dia (renomear o slot não altera o passado). */
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "diary_entry",
    foreignKeys = [
        ForeignKey(entity = DiaryMealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["mealId"]), Index(value = ["foodId"])],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodId: Long?,
    val foodSourceKey: String?,
    val foodName: String,
    val brand: String?,
    val preparation: String,
    val dataSourceCode: String,
    val amount: Double,
    val unit: String,
    /** Snapshot da base de referência e dos valores por referência. */
    val refAmount: Double,
    @Embedded(prefix = "ref_") val reference: NutrientColumns,
    /** Valores consumidos, calculados pelo NutritionCalculator na gravação. */
    @Embedded val consumed: NutrientColumns,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "nutrition_goal", indices = [Index(value = ["effectiveFrom"], unique = true)])
data class NutritionGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveFrom: Long,
    val energyKcal: Double,
    val proteinG: Double?,
    val carbohydrateG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val origin: String,
    val createdAt: Long,
)

@Entity(tableName = "meal_template")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "meal_template_item",
    foreignKeys = [
        ForeignKey(entity = MealTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["templateId"]), Index(value = ["foodId"])],
)
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val foodId: Long,
    val amount: Double,
    val sortOrder: Int,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(@PrimaryKey val key: String, val value: String)

/** Totais diários calculados direto dos lançamentos (sem cache que possa divergir). */
@DatabaseView(
    viewName = "daily_nutrition",
    value = """
SELECT m.date AS date,
       COUNT(e.id) AS entryCount,
       SUM(e.energyKcal) AS energyKcal,
       SUM(e.proteinG) AS proteinG,
       SUM(e.carbohydrateG) AS carbohydrateG,
       SUM(e.fatG) AS fatG,
       SUM(e.saturatedFatG) AS saturatedFatG,
       SUM(e.fiberG) AS fiberG,
       SUM(e.sodiumMg) AS sodiumMg,
       SUM(CASE WHEN e.energyKcal IS NULL THEN 1 ELSE 0 END) AS energyMissing,
       SUM(CASE WHEN e.proteinG IS NULL THEN 1 ELSE 0 END) AS proteinMissing,
       SUM(CASE WHEN e.carbohydrateG IS NULL THEN 1 ELSE 0 END) AS carbohydrateMissing,
       SUM(CASE WHEN e.fatG IS NULL THEN 1 ELSE 0 END) AS fatMissing,
       SUM(CASE WHEN e.saturatedFatG IS NULL THEN 1 ELSE 0 END) AS saturatedFatMissing,
       SUM(CASE WHEN e.fiberG IS NULL THEN 1 ELSE 0 END) AS fiberMissing,
       SUM(CASE WHEN e.sodiumMg IS NULL THEN 1 ELSE 0 END) AS sodiumMissing
FROM diary_meal m
JOIN diary_entry e ON e.mealId = m.id
GROUP BY m.date
""",
)
data class DailyNutritionView(
    val date: Long,
    val entryCount: Int,
    val energyKcal: Double?,
    val proteinG: Double?,
    val carbohydrateG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val fiberG: Double?,
    val sodiumMg: Double?,
    val energyMissing: Int,
    val proteinMissing: Int,
    val carbohydrateMissing: Int,
    val fatMissing: Int,
    val saturatedFatMissing: Int,
    val fiberMissing: Int,
    val sodiumMissing: Int,
)
