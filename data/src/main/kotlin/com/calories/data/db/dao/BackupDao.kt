package com.calories.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.calories.data.db.entity.DiaryEntryEntity
import com.calories.data.db.entity.DiaryMealEntity
import com.calories.data.db.entity.DietPlanEntity
import com.calories.data.db.entity.DietPlanItemEntity
import com.calories.data.db.entity.FoodCategoryEntity
import com.calories.data.db.entity.FoodEntity
import com.calories.data.db.entity.FoodUsageEntity
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.db.entity.MealTemplateEntity
import com.calories.data.db.entity.MealTemplateItemEntity
import com.calories.data.db.entity.NutritionGoalEntity
import com.calories.data.db.entity.PlannedItemEntity
import com.calories.data.db.entity.ShoppingListEntity
import com.calories.data.db.entity.ShoppingListItemEntity
import com.calories.data.db.entity.WeightRecordEntity

data class KeyId(val id: Long, val sourceKey: String)

data class DiaryCsvRow(val mealDate: Long, val mealName: String, @Embedded val entry: DiaryEntryEntity)

/** Leitura e escrita em massa dos dados do usuário (backup, restauração, exportação). */
@Dao
interface BackupDao {
    @Query("SELECT id, sourceKey FROM food WHERE origin = 'LIBRARY' AND sourceKey IS NOT NULL")
    suspend fun libraryKeys(): List<KeyId>

    @Query("SELECT * FROM food_category") suspend fun categories(): List<FoodCategoryEntity>
    @Query("SELECT * FROM meal_slot ORDER BY sortOrder, id") suspend fun slots(): List<MealSlotEntity>
    @Query("SELECT * FROM food WHERE origin = 'CUSTOM' ORDER BY id") suspend fun customFoods(): List<FoodEntity>
    @Query("SELECT * FROM food_usage") suspend fun usage(): List<FoodUsageEntity>
    @Query("SELECT * FROM diary_meal ORDER BY date, sortOrder, id") suspend fun diaryMeals(): List<DiaryMealEntity>
    @Query("SELECT * FROM diary_entry ORDER BY mealId, sortOrder, id") suspend fun diaryEntries(): List<DiaryEntryEntity>
    @Query("SELECT * FROM nutrition_goal ORDER BY effectiveFrom") suspend fun goals(): List<NutritionGoalEntity>
    @Query("SELECT * FROM weight_record ORDER BY date") suspend fun weights(): List<WeightRecordEntity>
    @Query("SELECT * FROM meal_template ORDER BY id") suspend fun templates(): List<MealTemplateEntity>
    @Query("SELECT * FROM meal_template_item ORDER BY templateId, sortOrder") suspend fun templateItems(): List<MealTemplateItemEntity>
    @Query("SELECT * FROM diet_plan ORDER BY id") suspend fun plans(): List<DietPlanEntity>
    @Query("SELECT * FROM diet_plan_item ORDER BY planId, sortOrder") suspend fun planItems(): List<DietPlanItemEntity>
    @Query("SELECT * FROM planned_item ORDER BY date, sortOrder") suspend fun plannedItems(): List<PlannedItemEntity>
    @Query("SELECT * FROM shopping_list ORDER BY id") suspend fun shoppingLists(): List<ShoppingListEntity>
    @Query("SELECT * FROM shopping_list_item ORDER BY listId, sortOrder") suspend fun shoppingItems(): List<ShoppingListItemEntity>

    @Query(
        "SELECT m.date AS mealDate, m.name AS mealName, e.* FROM diary_entry e JOIN diary_meal m ON m.id = e.mealId " +
            "WHERE (:from IS NULL OR m.date >= :from) AND (:to IS NULL OR m.date <= :to) ORDER BY m.date, m.sortOrder, e.sortOrder, e.id",
    )
    suspend fun diaryCsv(from: Long?, to: Long?): List<DiaryCsvRow>

    // Limpeza dos dados do usuário (a biblioteca original nunca é tocada). Ordem respeita as chaves estrangeiras.
    @Query("DELETE FROM shopping_list") suspend fun deleteShopping()
    @Query("DELETE FROM planned_item") suspend fun deletePlanned()
    @Query("DELETE FROM diet_plan") suspend fun deletePlans()
    @Query("DELETE FROM meal_template") suspend fun deleteTemplates()
    @Query("DELETE FROM diary_meal") suspend fun deleteDiary()
    @Query("DELETE FROM nutrition_goal") suspend fun deleteGoals()
    @Query("DELETE FROM weight_record") suspend fun deleteWeights()
    @Query("DELETE FROM food_usage") suspend fun deleteUsage()
    @Query("DELETE FROM food WHERE origin = 'CUSTOM'") suspend fun deleteCustomFoods()
    @Query("DELETE FROM meal_slot") suspend fun deleteSlots()

    @Insert suspend fun insertSlot(slot: MealSlotEntity): Long
    @Insert suspend fun insertFood(food: FoodEntity): Long
    @Insert suspend fun insertUsage(usage: FoodUsageEntity)
    @Insert suspend fun insertMeal(meal: DiaryMealEntity): Long
    @Insert suspend fun insertEntries(entries: List<DiaryEntryEntity>)
    @Insert suspend fun insertGoals(goals: List<NutritionGoalEntity>)
    @Insert suspend fun insertWeights(weights: List<WeightRecordEntity>)
    @Insert suspend fun insertTemplate(template: MealTemplateEntity): Long
    @Insert suspend fun insertTemplateItems(items: List<MealTemplateItemEntity>)
    @Insert suspend fun insertPlan(plan: DietPlanEntity): Long
    @Insert suspend fun insertPlanItems(items: List<DietPlanItemEntity>)
    @Insert suspend fun insertPlanned(items: List<PlannedItemEntity>)
    @Insert suspend fun insertShoppingList(list: ShoppingListEntity): Long
    @Insert suspend fun insertShoppingItems(items: List<ShoppingListItemEntity>)
}
