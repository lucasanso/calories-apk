package com.calories.domain.repository

import com.calories.domain.model.DietPlan
import com.calories.domain.model.NewShoppingItem
import com.calories.domain.model.PlannedDayOverview
import com.calories.domain.model.PlannedItem
import com.calories.domain.model.ShoppingList
import com.calories.domain.model.WeightRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface WeightRepository {
    fun observe(from: LocalDate, to: LocalDate): Flow<List<WeightRecord>>
    fun observeLatest(): Flow<WeightRecord?>
    /** Um registro por dia: gravar de novo na mesma data substitui. */
    suspend fun save(record: WeightRecord)
    suspend fun delete(date: LocalDate)
}

interface PlanRepository {
    fun observePlans(): Flow<List<DietPlan>>
    fun observePlan(id: Long): Flow<DietPlan?>
    suspend fun getPlan(id: Long): DietPlan?
    suspend fun createPlan(name: String, description: String?): Long
    suspend fun updatePlan(id: Long, name: String, description: String?)
    suspend fun deletePlan(id: Long)
    suspend fun addPlanItem(planId: Long, slotId: Long, foodId: Long, amount: Double): Long
    suspend fun updatePlanItem(itemId: Long, amount: Double)
    suspend fun deletePlanItem(itemId: Long)

    fun observePlannedDay(date: LocalDate): Flow<List<PlannedItem>>
    fun observeOverview(from: LocalDate, to: LocalDate): Flow<List<PlannedDayOverview>>
    suspend fun plannedItems(from: LocalDate, to: LocalDate): List<PlannedItem>
    suspend fun addPlannedItem(date: LocalDate, slotId: Long, foodId: Long, amount: Double, sourcePlanId: Long?): Long
    suspend fun updatePlannedItem(itemId: Long, amount: Double)
    suspend fun deletePlannedItem(itemId: Long)
    suspend fun clearPlannedDay(date: LocalDate)
    /** Executa [block] numa transação (aplicar plano em várias datas é tudo ou nada). */
    suspend fun <T> transaction(block: suspend () -> T): T
}

interface ShoppingRepository {
    fun observeLists(): Flow<List<ShoppingList>>
    fun observeList(id: Long): Flow<ShoppingList?>
    suspend fun create(name: String, from: LocalDate?, to: LocalDate?, items: List<NewShoppingItem>, now: Instant): Long
    suspend fun setChecked(itemId: Long, checked: Boolean)
    suspend fun updateQuantity(itemId: Long, quantity: Double?)
    suspend fun addManual(listId: Long, name: String, quantity: Double?, unit: String?)
    suspend fun deleteItem(itemId: Long)
    suspend fun clearChecked(listId: Long)
    suspend fun deleteList(id: Long)
}

/** Resultado da validação de um arquivo de backup, antes de tocar no banco. */
data class BackupCheck(
    val errors: List<String>,
    val exportedAt: String?,
    val counts: Map<String, Int>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

interface BackupRepository {
    /** Backup completo dos dados do usuário (a biblioteca vem do app e é referenciada por chave). */
    suspend fun exportBackup(now: Instant): String
    suspend fun check(text: String): BackupCheck
    /** Valida e substitui TODOS os dados do usuário numa transação (tudo ou nada). */
    suspend fun restore(text: String): BackupCheck
    /** Diário em CSV (RFC 4180, UTF-8 com BOM). [excelPtBr] usa ";" e vírgula decimal. */
    suspend fun exportDiaryCsv(from: LocalDate?, to: LocalDate?, excelPtBr: Boolean): String
    /** Só os planos (e os alimentos personalizados que eles usam). */
    suspend fun exportPlans(now: Instant): String
    /** Importa planos de um arquivo de planos ou de backup; não apaga nada. Retorna quantos planos entraram. */
    suspend fun importPlans(text: String): Int
}
