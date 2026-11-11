package com.calories.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.calories.data.db.dao.CustomFoodDao
import com.calories.data.db.dao.DiaryDao
import com.calories.data.db.dao.FoodQueryDao
import com.calories.data.db.dao.FoodUsageDao
import com.calories.data.db.dao.GoalDao
import com.calories.data.db.dao.LibrarySeedDao
import com.calories.data.db.dao.MealSlotDao
import com.calories.data.db.dao.MealTemplateDao
import com.calories.data.db.dao.MetaDao
import com.calories.data.db.entity.AppMetaEntity
import com.calories.data.db.entity.DailyNutritionView
import com.calories.data.db.entity.DataSourceEntity
import com.calories.data.db.entity.DiaryEntryEntity
import com.calories.data.db.entity.DiaryMealEntity
import com.calories.data.db.entity.FoodCategoryEntity
import com.calories.data.db.entity.FoodEntity
import com.calories.data.db.entity.FoodFtsEntity
import com.calories.data.db.entity.FoodUsageEntity
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.db.entity.MealTemplateEntity
import com.calories.data.db.entity.MealTemplateItemEntity
import com.calories.data.db.entity.NutritionGoalEntity

/**
 * Banco local (única fonte de verdade). Esquemas exportados em `data/schemas/`;
 * mudanças futuras usam migrations testadas, nunca recriação destrutiva.
 */
@Database(
    entities = [
        DataSourceEntity::class, FoodCategoryEntity::class, FoodEntity::class, FoodFtsEntity::class, FoodUsageEntity::class,
        MealSlotEntity::class, DiaryMealEntity::class, DiaryEntryEntity::class, NutritionGoalEntity::class,
        MealTemplateEntity::class, MealTemplateItemEntity::class, AppMetaEntity::class,
    ],
    views = [DailyNutritionView::class],
    version = 1,
    exportSchema = true,
)
abstract class CaloriesDatabase : RoomDatabase() {
    abstract fun foodQueryDao(): FoodQueryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun librarySeedDao(): LibrarySeedDao
    abstract fun foodUsageDao(): FoodUsageDao
    abstract fun diaryDao(): DiaryDao
    abstract fun mealSlotDao(): MealSlotDao
    abstract fun goalDao(): GoalDao
    abstract fun mealTemplateDao(): MealTemplateDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "calories.db"
    }
}
