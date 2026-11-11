package com.calories.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.calories.data.db.entity.DietPlanEntity
import com.calories.data.db.entity.DietPlanItemEntity
import com.calories.data.db.entity.NutrientColumns
import com.calories.data.db.entity.PlannedItemEntity
import com.calories.data.db.entity.ShoppingListEntity
import com.calories.data.db.entity.ShoppingListItemEntity
import com.calories.data.db.entity.WeightRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_record WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observe(from: Long, to: Long): Flow<List<WeightRecordEntity>>

    @Query("SELECT * FROM weight_record ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<WeightRecordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WeightRecordEntity)

    @Query("DELETE FROM weight_record WHERE date = :date")
    suspend fun delete(date: Long)
}

/** Item de plano ou do calendário com os dados atuais do alimento. */
data class PlanFoodRow(
    val id: Long,
    val ownerId: Long,
    val date: Long?,
    val slotId: Long,
    val foodId: Long,
    val amount: Double,
    val sortOrder: Int,
    val sourcePlanId: Long?,
    val foodName: String,
    val preparation: String,
    val referenceAmount: Double,
    val referenceUnit: String,
    @Embedded val nutrients: NutrientColumns,
)

private const val FOOD_COLUMNS =
    "f.name AS foodName, f.preparation, f.referenceAmount, f.referenceUnit, f.energyKcal, f.proteinG, f.carbohydrateG, f.fatG, " +
        "f.saturatedFatG, f.fiberG, f.sodiumMg, f.traceMask, f.notApplicableMask"

@Dao
interface PlanDao {
    @Query("SELECT * FROM diet_plan ORDER BY name COLLATE NOCASE")
    fun observePlans(): Flow<List<DietPlanEntity>>

    @Query("SELECT * FROM diet_plan WHERE id = :id")
    fun observePlan(id: Long): Flow<DietPlanEntity?>

    @Query("SELECT * FROM diet_plan WHERE id = :id")
    suspend fun getPlan(id: Long): DietPlanEntity?

    @Query("SELECT i.id, i.planId AS ownerId, NULL AS date, i.slotId, i.foodId, i.amount, i.sortOrder, NULL AS sourcePlanId, $FOOD_COLUMNS FROM diet_plan_item i JOIN food f ON f.id = i.foodId ORDER BY i.sortOrder, i.id")
    fun observeAllPlanItems(): Flow<List<PlanFoodRow>>

    @Query("SELECT i.id, i.planId AS ownerId, NULL AS date, i.slotId, i.foodId, i.amount, i.sortOrder, NULL AS sourcePlanId, $FOOD_COLUMNS FROM diet_plan_item i JOIN food f ON f.id = i.foodId WHERE i.planId = :planId ORDER BY i.sortOrder, i.id")
    fun observePlanItems(planId: Long): Flow<List<PlanFoodRow>>

    @Query("SELECT i.id, i.planId AS ownerId, NULL AS date, i.slotId, i.foodId, i.amount, i.sortOrder, NULL AS sourcePlanId, $FOOD_COLUMNS FROM diet_plan_item i JOIN food f ON f.id = i.foodId WHERE i.planId = :planId ORDER BY i.sortOrder, i.id")
    suspend fun planItems(planId: Long): List<PlanFoodRow>

    @Insert
    suspend fun insertPlan(plan: DietPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: DietPlanEntity)

    @Query("DELETE FROM diet_plan WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM diet_plan_item WHERE planId = :planId")
    suspend fun nextPlanOrder(planId: Long): Int

    @Insert
    suspend fun insertPlanItem(item: DietPlanItemEntity): Long

    @Query("UPDATE diet_plan_item SET amount = :amount WHERE id = :id")
    suspend fun updatePlanItem(id: Long, amount: Double)

    @Query("DELETE FROM diet_plan_item WHERE id = :id")
    suspend fun deletePlanItem(id: Long)

    @Query("SELECT i.id, i.date AS ownerId, i.date, i.slotId, i.foodId, i.amount, i.sortOrder, i.sourcePlanId, $FOOD_COLUMNS FROM planned_item i JOIN food f ON f.id = i.foodId WHERE i.date BETWEEN :from AND :to ORDER BY i.date, i.sortOrder, i.id")
    fun observePlanned(from: Long, to: Long): Flow<List<PlanFoodRow>>

    @Query("SELECT i.id, i.date AS ownerId, i.date, i.slotId, i.foodId, i.amount, i.sortOrder, i.sourcePlanId, $FOOD_COLUMNS FROM planned_item i JOIN food f ON f.id = i.foodId WHERE i.date BETWEEN :from AND :to ORDER BY i.date, i.sortOrder, i.id")
    suspend fun planned(from: Long, to: Long): List<PlanFoodRow>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM planned_item WHERE date = :date")
    suspend fun nextPlannedOrder(date: Long): Int

    @Insert
    suspend fun insertPlanned(item: PlannedItemEntity): Long

    @Query("UPDATE planned_item SET amount = :amount WHERE id = :id")
    suspend fun updatePlanned(id: Long, amount: Double)

    @Query("DELETE FROM planned_item WHERE id = :id")
    suspend fun deletePlanned(id: Long)

    @Query("DELETE FROM planned_item WHERE date = :date")
    suspend fun clearDay(date: Long)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_list ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_list_item ORDER BY isChecked, sortOrder, id")
    fun observeAllItems(): Flow<List<ShoppingListItemEntity>>

    @Insert
    suspend fun insertList(list: ShoppingListEntity): Long

    @Insert
    suspend fun insertItems(items: List<ShoppingListItemEntity>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM shopping_list_item WHERE listId = :listId")
    suspend fun nextOrder(listId: Long): Int

    @Query("UPDATE shopping_list_item SET isChecked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE shopping_list_item SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double?)

    @Query("DELETE FROM shopping_list_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM shopping_list_item WHERE listId = :listId AND isChecked = 1")
    suspend fun clearChecked(listId: Long)

    @Query("DELETE FROM shopping_list WHERE id = :id")
    suspend fun deleteList(id: Long)
}
