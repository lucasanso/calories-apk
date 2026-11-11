package com.calories.data.repository

import androidx.room.withTransaction
import com.calories.data.db.CaloriesDatabase
import com.calories.data.db.entity.DailyNutritionView
import com.calories.data.db.entity.DiaryEntryEntity
import com.calories.data.db.entity.DiaryMealEntity
import com.calories.domain.model.DailyTotals
import com.calories.domain.model.DiaryEntry
import com.calories.domain.model.DiaryMeal
import com.calories.domain.model.MealSlot
import com.calories.domain.model.NewDiaryEntry
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientTotal
import com.calories.domain.nutrition.NutrientTotals
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class DiaryRepositoryImpl @Inject constructor(private val db: CaloriesDatabase, private val clock: Clock) : DiaryRepository {

    private val dao = db.diaryDao()

    override fun observeMeals(date: LocalDate): Flow<List<DiaryMeal>> =
        dao.observeMeals(date.toEpochDay()).map { list -> list.map { it.meal.toDomain(it.entries) } }

    override suspend fun mealEntries(date: LocalDate, slotId: Long): List<DiaryEntry> =
        dao.mealEntries(date.toEpochDay(), slotId).map { it.toDomain() }

    private suspend fun mealFor(date: LocalDate, slot: MealSlot): Long =
        dao.findMeal(date.toEpochDay(), slot.id)?.id
            ?: dao.insertMeal(DiaryMealEntity(date = date.toEpochDay(), slotId = slot.id, name = slot.name, sortOrder = slot.sortOrder))

    override suspend fun addEntry(date: LocalDate, slot: MealSlot, entry: NewDiaryEntry): Long = db.withTransaction {
        val mealId = mealFor(date, slot)
        val now = entry.createdAt.toEpochMilli()
        dao.insertEntry(
            DiaryEntryEntity(
                mealId = mealId, foodId = entry.foodId, foodSourceKey = entry.foodSourceKey, foodName = entry.foodName, brand = entry.brand,
                preparation = entry.preparation.name, dataSourceCode = entry.dataSourceCode, amount = entry.amount, unit = entry.unit.name,
                refAmount = entry.reference.referenceAmount, reference = entry.reference.values.toColumns(), consumed = entry.consumed.toColumns(),
                sortOrder = dao.nextEntryOrder(mealId), createdAt = now, updatedAt = now,
            ),
        )
    }

    override suspend fun getEntry(id: Long): DiaryEntry? = dao.getEntry(id)?.toDomain()

    override suspend fun updateEntryAmount(id: Long, amount: Double, consumed: Map<Nutrient, NutrientValue>) {
        val current = dao.getEntry(id) ?: return
        dao.updateEntry(current.copy(amount = amount, consumed = consumed.toColumns(), updatedAt = clock.millis()))
    }

    override suspend fun deleteEntry(id: Long): DiaryEntry? = db.withTransaction {
        dao.getEntry(id)?.also { dao.deleteEntry(id) }?.toDomain()
    }

    override suspend fun restoreEntry(entry: DiaryEntry) {
        val e = entry
        dao.insertEntry(
            DiaryEntryEntity(
                id = e.id, mealId = e.mealId, foodId = e.foodId, foodSourceKey = e.foodSourceKey, foodName = e.foodName, brand = e.brand,
                preparation = e.preparation.name, dataSourceCode = e.dataSourceCode, amount = e.amount, unit = e.unit.name,
                refAmount = e.reference.referenceAmount, reference = e.reference.values.toColumns(), consumed = e.consumed.toColumns(),
                sortOrder = e.sortOrder, createdAt = e.createdAt.toEpochMilli(), updatedAt = clock.millis(),
            ),
        )
    }

    override suspend fun moveEntry(id: Long, date: LocalDate, slot: MealSlot) = db.withTransaction {
        val current = dao.getEntry(id) ?: return@withTransaction
        val mealId = mealFor(date, slot)
        if (mealId != current.mealId) dao.updateEntry(current.copy(mealId = mealId, sortOrder = dao.nextEntryOrder(mealId), updatedAt = clock.millis()))
    }

    override suspend fun clearMeal(date: LocalDate, slotId: Long) = dao.clearMeal(date.toEpochDay(), slotId)

    override fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotals>> =
        combine(dao.observeDaily(from.toEpochDay(), to.toEpochDay()), db.goalDao().observeGoalsUntil(to.toEpochDay())) { days, goals ->
            val sortedGoals = goals.map { it.toDomain() }.sortedBy { it.effectiveFrom }
            days.map { d ->
                val date = LocalDate.ofEpochDay(d.date)
                DailyTotals(date, d.entryCount, d.toTotals(), sortedGoals.lastOrNull { !it.effectiveFrom.isAfter(date) })
            }
        }

    private fun DailyNutritionView.toTotals(): NutrientTotals {
        fun t(sum: Double?, missing: Int) = NutrientTotal(sum ?: 0.0, missing, entryCount)
        return NutrientTotals(
            mapOf(
                Nutrient.ENERGY to t(energyKcal, energyMissing), Nutrient.PROTEIN to t(proteinG, proteinMissing),
                Nutrient.CARBOHYDRATE to t(carbohydrateG, carbohydrateMissing), Nutrient.FAT to t(fatG, fatMissing),
                Nutrient.SATURATED_FAT to t(saturatedFatG, saturatedFatMissing), Nutrient.FIBER to t(fiberG, fiberMissing),
                Nutrient.SODIUM to t(sodiumMg, sodiumMissing),
            ),
        )
    }
}
