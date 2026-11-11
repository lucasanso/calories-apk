package com.calories.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.calories.data.db.entity.AppMetaEntity
import com.calories.data.db.entity.DailyNutritionView
import com.calories.data.db.entity.DataSourceEntity
import com.calories.data.db.entity.DiaryEntryEntity
import com.calories.data.db.entity.DiaryMealEntity
import com.calories.data.db.entity.FoodCategoryEntity
import com.calories.data.db.entity.FoodEntity
import com.calories.data.db.entity.FoodUsageEntity
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.db.entity.MealTemplateEntity
import com.calories.data.db.entity.MealTemplateItemEntity
import com.calories.data.db.entity.NutritionGoalEntity
import kotlinx.coroutines.flow.Flow

/** Alimento com a fonte e o estado do usuário (favorito, uso). */
data class FoodRow(
    @Embedded val food: FoodEntity,
    val dataSourceCode: String,
    val isFavorite: Boolean?,
    val lastUsedAt: Long?,
    val useCount: Int?,
)

private const val FOOD_SELECT =
    "SELECT food.*, data_source.code AS dataSourceCode, food_usage.isFavorite AS isFavorite, " +
        "food_usage.lastUsedAt AS lastUsedAt, food_usage.useCount AS useCount " +
        "FROM food JOIN data_source ON data_source.id = food.dataSourceId " +
        "LEFT JOIN food_usage ON food_usage.foodId = food.id"

private const val VISIBLE = "food.isDeprecated = 0 AND food.isArchived = 0"

/** Consultas de alimentos usadas pela interface (somente leitura). */
@Dao
interface FoodQueryDao {
    @Query("$FOOD_SELECT JOIN food_fts ON food_fts.rowid = food.id WHERE food_fts MATCH :match AND $VISIBLE AND (:categoryId IS NULL OR food.categoryId = :categoryId) LIMIT :limit")
    fun search(match: String, categoryId: Long?, limit: Int): Flow<List<FoodRow>>

    @Query("$FOOD_SELECT WHERE $VISIBLE AND (:categoryId IS NULL OR food.categoryId = :categoryId)")
    fun all(categoryId: Long?): Flow<List<FoodRow>>

    @Query("$FOOD_SELECT WHERE $VISIBLE AND food_usage.isFavorite = 1 AND (:categoryId IS NULL OR food.categoryId = :categoryId)")
    fun favorites(categoryId: Long?): Flow<List<FoodRow>>

    @Query("$FOOD_SELECT WHERE $VISIBLE AND food_usage.lastUsedAt IS NOT NULL AND (:categoryId IS NULL OR food.categoryId = :categoryId) ORDER BY food_usage.lastUsedAt DESC LIMIT :limit")
    fun recent(categoryId: Long?, limit: Int): Flow<List<FoodRow>>

    @Query("$FOOD_SELECT WHERE $VISIBLE AND food.origin = 'CUSTOM' AND (:categoryId IS NULL OR food.categoryId = :categoryId)")
    fun custom(categoryId: Long?): Flow<List<FoodRow>>

    @Query("$FOOD_SELECT WHERE food.id = :id")
    fun observe(id: Long): Flow<FoodRow?>

    @Query("$FOOD_SELECT WHERE food.id = :id")
    suspend fun get(id: Long): FoodRow?

    @Query("SELECT * FROM food_category ORDER BY sortOrder, name")
    fun categories(): Flow<List<FoodCategoryEntity>>

    @Query("SELECT * FROM data_source ORDER BY id")
    fun sources(): Flow<List<DataSourceEntity>>

    @Query("SELECT id FROM data_source WHERE code = :code")
    suspend fun sourceId(code: String): Long?

    @Query("SELECT COUNT(*) FROM food WHERE origin = 'LIBRARY' AND isDeprecated = 0")
    suspend fun libraryCount(): Int
}

/**
 * Escrita de alimentos personalizados. Toda alteração filtra `origin = 'CUSTOM'` na própria
 * query: mesmo com um id errado, a biblioteca original não é modificada.
 */
@Dao
interface CustomFoodDao {
    @Insert
    suspend fun insert(food: FoodEntity): Long

    @Query(
        "UPDATE food SET name = :name, brand = :brand, categoryId = :categoryId, preparation = :preparation, " +
            "referenceAmount = :referenceAmount, referenceUnit = :referenceUnit, energyKcal = :energyKcal, proteinG = :proteinG, " +
            "carbohydrateG = :carbohydrateG, fatG = :fatG, saturatedFatG = :saturatedFatG, fiberG = :fiberG, sodiumMg = :sodiumMg, " +
            "traceMask = 0, notApplicableMask = 0, updatedAt = :now WHERE id = :id AND origin = 'CUSTOM'",
    )
    suspend fun update(
        id: Long, name: String, brand: String?, categoryId: Long?, preparation: String, referenceAmount: Double, referenceUnit: String,
        energyKcal: Double?, proteinG: Double?, carbohydrateG: Double?, fatG: Double?, saturatedFatG: Double?, fiberG: Double?, sodiumMg: Double?,
        now: Long,
    ): Int

    @Query("DELETE FROM food WHERE id = :id AND origin = 'CUSTOM'")
    suspend fun delete(id: Long): Int

    @Query("UPDATE food SET isArchived = 1 WHERE id = :id AND origin = 'CUSTOM'")
    suspend fun archive(id: Long): Int

    /** Referências que impedem a exclusão (refeições favoritas, planos e calendário). */
    @Query(
        "SELECT (SELECT COUNT(*) FROM meal_template_item WHERE foodId = :id) + (SELECT COUNT(*) FROM diet_plan_item WHERE foodId = :id) + " +
            "(SELECT COUNT(*) FROM planned_item WHERE foodId = :id)",
    )
    suspend fun references(id: Long): Int
}

/** Escrita da biblioteca original: usada SOMENTE pelo LibrarySeeder. */
@Dao
interface LibrarySeedDao {
    @Query("SELECT * FROM data_source")
    suspend fun sources(): List<DataSourceEntity>

    @Insert
    suspend fun insertSource(source: DataSourceEntity): Long

    @Update
    suspend fun updateSource(source: DataSourceEntity)

    @Query("SELECT * FROM food_category")
    suspend fun categories(): List<FoodCategoryEntity>

    @Insert
    suspend fun insertCategory(category: FoodCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: FoodCategoryEntity)

    @Query("SELECT * FROM food WHERE origin = 'LIBRARY'")
    suspend fun libraryFoods(): List<FoodEntity>

    @Insert
    suspend fun insertFoods(foods: List<FoodEntity>)

    @Update
    suspend fun updateFoods(foods: List<FoodEntity>)

    @Query("UPDATE food SET isDeprecated = 1, updatedAt = :now WHERE origin = 'LIBRARY' AND id IN (:ids)")
    suspend fun deprecate(ids: List<Long>, now: Long)
}

@Dao
interface FoodUsageDao {
    @Query("SELECT * FROM food_usage WHERE foodId = :foodId")
    suspend fun get(foodId: Long): FoodUsageEntity?

    @Upsert
    suspend fun upsert(usage: FoodUsageEntity)
}

data class MealWithEntries(
    @Embedded val meal: DiaryMealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId") val entries: List<DiaryEntryEntity>,
)

@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM diary_meal WHERE date = :date ORDER BY sortOrder, id")
    fun observeMeals(date: Long): Flow<List<MealWithEntries>>

    @Query("SELECT * FROM diary_meal WHERE date = :date AND slotId = :slotId")
    suspend fun findMeal(date: Long, slotId: Long): DiaryMealEntity?

    @Insert
    suspend fun insertMeal(meal: DiaryMealEntity): Long

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM diary_entry WHERE mealId = :mealId")
    suspend fun nextEntryOrder(mealId: Long): Int

    @Insert
    suspend fun insertEntry(entry: DiaryEntryEntity): Long

    @Query("SELECT * FROM diary_entry WHERE id = :id")
    suspend fun getEntry(id: Long): DiaryEntryEntity?

    @Update
    suspend fun updateEntry(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT e.* FROM diary_entry e JOIN diary_meal m ON m.id = e.mealId WHERE m.date = :date AND m.slotId = :slotId ORDER BY e.sortOrder, e.id")
    suspend fun mealEntries(date: Long, slotId: Long): List<DiaryEntryEntity>

    @Query("DELETE FROM diary_entry WHERE mealId IN (SELECT id FROM diary_meal WHERE date = :date AND slotId = :slotId)")
    suspend fun clearMeal(date: Long, slotId: Long)

    @Query("SELECT * FROM daily_nutrition WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeDaily(from: Long, to: Long): Flow<List<DailyNutritionView>>
}

@Dao
interface MealSlotDao {
    @Query("SELECT * FROM meal_slot ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slot WHERE id = :id")
    suspend fun get(id: Long): MealSlotEntity?

    @Query("SELECT COUNT(*) FROM meal_slot")
    suspend fun count(): Int

    @Insert
    suspend fun insert(slot: MealSlotEntity): Long

    @Update
    suspend fun update(slot: MealSlotEntity)

    @Query("UPDATE meal_slot SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setOrder(id: Long, sortOrder: Int)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM nutrition_goal WHERE effectiveFrom <= :date ORDER BY effectiveFrom DESC LIMIT 1")
    fun observeFor(date: Long): Flow<NutritionGoalEntity?>

    @Query("SELECT * FROM nutrition_goal WHERE effectiveFrom <= :date ORDER BY effectiveFrom DESC LIMIT 1")
    suspend fun goalFor(date: Long): NutritionGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: NutritionGoalEntity)

    @Query("SELECT * FROM nutrition_goal WHERE effectiveFrom <= :to ORDER BY effectiveFrom")
    suspend fun goalsUntil(to: Long): List<NutritionGoalEntity>

    @Query("SELECT * FROM nutrition_goal WHERE effectiveFrom <= :to ORDER BY effectiveFrom")
    fun observeGoalsUntil(to: Long): Flow<List<NutritionGoalEntity>>
}

data class TemplateItemRow(
    val templateId: Long,
    val foodId: Long,
    val amount: Double,
    val sortOrder: Int,
    val foodName: String,
    val referenceAmount: Double,
    val referenceUnit: String,
    val energyKcal: Double?,
)

@Dao
interface MealTemplateDao {
    @Query("SELECT * FROM meal_template ORDER BY name")
    fun observeTemplates(): Flow<List<MealTemplateEntity>>

    @Query(
        "SELECT i.templateId, i.foodId, i.amount, i.sortOrder, f.name AS foodName, f.referenceAmount, f.referenceUnit, f.energyKcal " +
            "FROM meal_template_item i JOIN food f ON f.id = i.foodId ORDER BY i.templateId, i.sortOrder",
    )
    fun observeItems(): Flow<List<TemplateItemRow>>

    @Query("SELECT * FROM meal_template WHERE id = :id")
    suspend fun get(id: Long): MealTemplateEntity?

    @Query(
        "SELECT i.templateId, i.foodId, i.amount, i.sortOrder, f.name AS foodName, f.referenceAmount, f.referenceUnit, f.energyKcal " +
            "FROM meal_template_item i JOIN food f ON f.id = i.foodId WHERE i.templateId = :id ORDER BY i.sortOrder",
    )
    suspend fun items(id: Long): List<TemplateItemRow>

    @Insert
    suspend fun insert(template: MealTemplateEntity): Long

    @Insert
    suspend fun insertItems(items: List<MealTemplateItemEntity>)

    @Query("DELETE FROM meal_template WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: AppMetaEntity)
}
