package com.calories.domain.repository

import com.calories.domain.model.AppSettings
import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.DailyTotals
import com.calories.domain.model.DiaryEntry
import com.calories.domain.model.DiaryMeal
import com.calories.domain.model.Food
import com.calories.domain.model.FoodCategory
import com.calories.domain.model.FoodFilter
import com.calories.domain.model.FoodSource
import com.calories.domain.model.LibraryStatus
import com.calories.domain.model.MealSlot
import com.calories.domain.model.MealTemplate
import com.calories.domain.model.NewDiaryEntry
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.ThemeMode
import com.calories.domain.model.UserProfile
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface FoodRepository {
    /** Busca por texto (sem acento, por prefixo, inclui sinônimos). Texto vazio lista conforme o filtro. */
    fun search(query: String, filter: FoodFilter, categoryId: Long?, limit: Int = 200): Flow<List<Food>>
    fun observe(id: Long): Flow<Food?>
    suspend fun get(id: Long): Food?
    fun observeCategories(): Flow<List<FoodCategory>>
    fun observeSources(): Flow<List<FoodSource>>
    suspend fun createCustom(draft: CustomFoodDraft, now: Instant): Long
    /** Atualiza só alimentos personalizados; alimentos da biblioteca são somente leitura. */
    suspend fun updateCustom(id: Long, draft: CustomFoodDraft, now: Instant)
    /** Exclui; se o alimento estiver em refeições favoritas, arquiva. Retorna `true` se excluiu. */
    suspend fun deleteCustom(id: Long): Boolean
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun recordUsage(id: Long, amount: Double, at: Instant)
    suspend fun lastAmount(id: Long): Double?
}

interface DiaryRepository {
    fun observeMeals(date: LocalDate): Flow<List<DiaryMeal>>
    suspend fun mealEntries(date: LocalDate, slotId: Long): List<DiaryEntry>
    /** Cria a refeição do dia sob demanda e insere o lançamento. */
    suspend fun addEntry(date: LocalDate, slot: MealSlot, entry: NewDiaryEntry): Long
    suspend fun getEntry(id: Long): DiaryEntry?
    suspend fun updateEntryAmount(id: Long, amount: Double, consumed: Map<Nutrient, NutrientValue>)
    /** Remove e devolve o lançamento (para "Desfazer"). */
    suspend fun deleteEntry(id: Long): DiaryEntry?
    suspend fun restoreEntry(entry: DiaryEntry)
    suspend fun moveEntry(id: Long, date: LocalDate, slot: MealSlot)
    suspend fun clearMeal(date: LocalDate, slotId: Long)
    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotals>>
}

interface MealSlotRepository {
    fun observeSlots(includeInactive: Boolean = false): Flow<List<MealSlot>>
    suspend fun get(id: Long): MealSlot?
    suspend fun save(slot: MealSlot): Long
    suspend fun reorder(idsInOrder: List<Long>)
}

interface GoalRepository {
    /** Meta vigente na data: a de maior `effectiveFrom` ≤ [date]. */
    fun observeGoalFor(date: LocalDate): Flow<NutritionGoal?>
    suspend fun goalFor(date: LocalDate): NutritionGoal?
    /** Grava a meta a partir de `goal.effectiveFrom` (substitui a do mesmo dia). */
    suspend fun setGoal(goal: NutritionGoal)
    suspend fun goalsBetween(from: LocalDate, to: LocalDate): List<NutritionGoal>
    /** Todas as metas, em ordem de vigência (para gráficos de períodos). */
    fun observeGoals(): Flow<List<NutritionGoal>>
}

interface MealTemplateRepository {
    fun observeTemplates(): Flow<List<MealTemplate>>
    suspend fun get(id: Long): MealTemplate?
    suspend fun create(name: String, items: List<Pair<Long, Double>>): Long
    suspend fun delete(id: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    val profile: Flow<UserProfile?>
    suspend fun saveProfile(profile: UserProfile)
}

interface LibraryRepository {
    /** Importa/atualiza a biblioteca do asset quando necessário (idempotente). */
    suspend fun ensureLibrary(): LibraryStatus
}
