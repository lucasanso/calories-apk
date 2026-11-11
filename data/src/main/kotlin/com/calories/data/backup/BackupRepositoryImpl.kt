package com.calories.data.backup

import androidx.room.withTransaction
import com.calories.data.db.CaloriesDatabase
import com.calories.data.db.entity.DiaryEntryEntity
import com.calories.data.db.entity.DiaryMealEntity
import com.calories.data.db.entity.DietPlanEntity
import com.calories.data.db.entity.DietPlanItemEntity
import com.calories.data.db.entity.FoodEntity
import com.calories.data.db.entity.FoodUsageEntity
import com.calories.data.db.entity.MealSlotEntity
import com.calories.data.db.entity.MealTemplateEntity
import com.calories.data.db.entity.MealTemplateItemEntity
import com.calories.data.db.entity.NutrientColumns
import com.calories.data.db.entity.NutritionGoalEntity
import com.calories.data.db.entity.PlannedItemEntity
import com.calories.data.db.entity.ShoppingListEntity
import com.calories.data.db.entity.ShoppingListItemEntity
import com.calories.data.db.entity.WeightRecordEntity
import com.calories.data.seed.LibrarySeeder
import com.calories.domain.model.GoalOrigin
import com.calories.domain.model.ThemeMode
import com.calories.domain.model.UserProfile
import com.calories.domain.nutrition.ActivityLevel
import com.calories.domain.nutrition.BiologicalSex
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.nutrition.Objective
import com.calories.domain.nutrition.Preparation
import com.calories.domain.repository.BackupCheck
import com.calories.domain.repository.BackupRepository
import com.calories.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Backup, restauração e exportações (docs/02 §2.9). A restauração valida o arquivo inteiro
 * antes de tocar no banco e aplica tudo numa única transação: ou entra tudo, ou nada muda.
 */
class BackupRepositoryImpl @Inject constructor(
    private val db: CaloriesDatabase,
    private val settings: SettingsRepository,
) : BackupRepository {

    private val dao = db.backupDao()
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }

    // ---------- Exportação ----------

    override suspend fun exportBackup(now: Instant): String = json.encodeToString(BackupFile.serializer(), snapshot(BackupFile.KIND_FULL, now))

    override suspend fun exportPlans(now: Instant): String {
        val full = snapshot(BackupFile.KIND_PLANS, now)
        val usedCustom = full.plans.flatMap { p -> p.items.mapNotNull { it.food.customId } }.toSet()
        val usedSlots = full.plans.flatMap { p -> p.items.mapNotNull { it.slotId } }.toSet()
        return json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                kind = BackupFile.KIND_PLANS, schemaVersion = full.schemaVersion, libraryVersion = full.libraryVersion, exportedAt = full.exportedAt,
                mealSlots = full.mealSlots.filter { it.id in usedSlots }, customFoods = full.customFoods.filter { it.id in usedCustom }, plans = full.plans,
            ),
        )
    }

    private suspend fun snapshot(kind: String, now: Instant): BackupFile = db.withTransaction {
        val libraryKeyById = dao.libraryKeys().associate { it.id to it.sourceKey }
        val customIds = dao.customFoods().map { it.id }.toSet()
        fun ref(foodId: Long?): FoodRef? = when {
            foodId == null -> null
            foodId in customIds -> FoodRef(customId = foodId)
            else -> libraryKeyById[foodId]?.let { FoodRef(libraryKey = it) }
        }
        val categories = dao.categories().associate { it.id to it.code }
        val entriesByMeal = dao.diaryEntries().groupBy { it.mealId }
        val templateItems = dao.templateItems().groupBy { it.templateId }
        val planItems = dao.planItems().groupBy { it.planId }
        val shoppingItems = dao.shoppingItems().groupBy { it.listId }
        val currentSettings = settings.settings.first()
        val profile = settings.profile.first()

        BackupFile(
            kind = kind,
            schemaVersion = SCHEMA_VERSION,
            libraryVersion = db.metaDao().get(LibrarySeeder.KEY_LIBRARY_VERSION)?.toIntOrNull() ?: 0,
            exportedAt = now.toString(),
            mealSlots = dao.slots().map { BSlot(it.id, it.name, it.defaultTimeMinutes, it.sortOrder, it.isActive) },
            customFoods = dao.customFoods().map { f ->
                BFood(f.id, f.name, f.brand, f.categoryId?.let(categories::get), f.preparation, f.referenceAmount, f.referenceUnit, f.nutrients.toB(),
                    f.derivedFromFoodId?.let(libraryKeyById::get), f.isArchived, f.createdAt)
            },
            foodUsage = dao.usage().mapNotNull { u -> ref(u.foodId)?.let { BUsage(it, u.isFavorite, u.useCount, u.lastUsedAt, u.lastAmount) } },
            diary = dao.diaryMeals().map { m ->
                BDiaryMeal(LocalDate.ofEpochDay(m.date).toString(), m.slotId, m.name, m.sortOrder, entriesByMeal[m.id].orEmpty().map { e ->
                    BEntry(ref(e.foodId) ?: e.foodSourceKey?.let { FoodRef(libraryKey = it) }, e.foodName, e.brand, e.preparation, e.dataSourceCode,
                        e.amount, e.unit, e.refAmount, e.reference.toB(), e.consumed.toB(), e.sortOrder, e.createdAt)
                })
            },
            goals = dao.goals().map { BGoal(LocalDate.ofEpochDay(it.effectiveFrom).toString(), it.energyKcal, it.proteinG, it.carbohydrateG, it.fatG, it.fiberG, it.origin) },
            weights = dao.weights().map { BWeight(LocalDate.ofEpochDay(it.date).toString(), it.weightKg, it.note) },
            templates = dao.templates().map { t -> BTemplate(t.name, templateItems[t.id].orEmpty().mapNotNull { i -> ref(i.foodId)?.let { BAmount(it, i.amount) } }) },
            plans = dao.plans().map { p -> BPlan(p.id, p.name, p.description, planItems[p.id].orEmpty().mapNotNull { i -> ref(i.foodId)?.let { BAmount(it, i.amount, i.slotId) } }) },
            plannedItems = dao.plannedItems().mapNotNull { i -> ref(i.foodId)?.let { BPlannedItem(LocalDate.ofEpochDay(i.date).toString(), i.slotId, it, i.amount, i.sourcePlanId) } },
            shoppingLists = dao.shoppingLists().map { l ->
                BShoppingList(l.name, l.fromDate?.let { LocalDate.ofEpochDay(it).toString() }, l.toDate?.let { LocalDate.ofEpochDay(it).toString() }, l.createdAt,
                    shoppingItems[l.id].orEmpty().map { i -> BShoppingItem(ref(i.foodId), i.name, i.detail, i.quantity, i.unit, i.isChecked, i.isManual) })
            },
            settings = BSettings(currentSettings.themeMode.name, currentSettings.dynamicColor),
            profile = profile?.let { BProfile(it.sex.name, it.ageYears, it.weightKg, it.heightCm, it.activity.name, it.objective.name, it.adjustmentPercent, it.proteinPerKg, it.fatPercent) },
        )
    }

    override suspend fun exportDiaryCsv(from: LocalDate?, to: LocalDate?, excelPtBr: Boolean): String {
        val csv = CsvWriter(excelPtBr)
        csv.row(listOf("data", "refeicao", "alimento", "marca", "preparo", "fonte", "quantidade", "unidade",
            "energia_kcal", "proteinas_g", "carboidratos_g", "gorduras_g", "gorduras_saturadas_g", "fibras_g", "sodio_mg"))
        for (r in dao.diaryCsv(from?.toEpochDay(), to?.toEpochDay())) {
            val e = r.entry
            csv.row(
                listOf(LocalDate.ofEpochDay(r.mealDate).toString(), r.mealName, e.foodName, e.brand, Preparation.valueOf(e.preparation).labelPt,
                    e.dataSourceCode, e.amount, MeasureUnit.valueOf(e.unit).symbol) +
                    Nutrient.entries.map { n -> e.consumed.value(n) },
            )
        }
        return csv.toString()
    }

    // ---------- Validação ----------

    private fun parse(text: String): Pair<BackupFile?, String?> = try {
        json.decodeFromString(BackupFile.serializer(), text) to null
    } catch (e: Exception) {
        null to "Arquivo inválido ou corrompido (não é um backup do Calories)."
    }

    override suspend fun check(text: String): BackupCheck {
        val (file, parseError) = parse(text)
        if (file == null) return BackupCheck(listOf(parseError!!), null, emptyMap())
        val libraryKeys = dao.libraryKeys().map { it.sourceKey }.toSet()
        return BackupCheck(validate(file, libraryKeys).take(20), file.exportedAt, counts(file))
    }

    private fun counts(f: BackupFile) = linkedMapOf(
        "Dias no diário" to f.diary.map { it.date }.distinct().size,
        "Alimentos consumidos" to f.diary.sumOf { it.entries.size },
        "Alimentos personalizados" to f.customFoods.size,
        "Planos" to f.plans.size,
        "Itens planejados" to f.plannedItems.size,
        "Registros de peso" to f.weights.size,
        "Metas" to f.goals.size,
        "Listas de compras" to f.shoppingLists.size,
        "Refeições favoritas" to f.templates.size,
    )

    internal fun validate(f: BackupFile, libraryKeys: Set<String>): List<String> = buildList {
        if (f.format != BackupFile.FORMAT) { add("Este arquivo não é um backup do Calories."); return@buildList }
        if (f.formatVersion > BackupFile.FORMAT_VERSION) { add("Backup feito por uma versão mais nova do app (formato ${f.formatVersion}). Atualize o app."); return@buildList }
        if (f.kind != BackupFile.KIND_FULL && f.kind != BackupFile.KIND_PLANS) add("Tipo de arquivo desconhecido: ${f.kind}.")

        val slotIds = f.mealSlots.map { it.id }
        if (slotIds.size != slotIds.toSet().size) add("Refeições com identificador repetido.")
        f.mealSlots.forEach { if (it.name.isBlank()) add("Refeição sem nome.") }
        val customIds = f.customFoods.map { it.id }
        if (customIds.size != customIds.toSet().size) add("Alimentos personalizados com identificador repetido.")

        fun refOk(r: FoodRef, where: String): Boolean {
            val ok = (r.libraryKey != null && r.libraryKey in libraryKeys) || (r.customId != null && r.customId in customIds)
            if (!ok) add("$where: alimento ${r.libraryKey ?: "personalizado #${r.customId}"} não existe nesta versão da biblioteca.")
            return ok
        }
        fun amountOk(v: Double, where: String) { if (!v.isFinite() || v <= 0 || v > NutritionCalculator.MAX_AMOUNT) add("$where: quantidade inválida ($v).") }
        fun dateOk(s: String?, where: String) { if (s != null && runCatching { LocalDate.parse(s) }.isFailure) add("$where: data inválida ($s).") }
        fun prepOk(s: String, where: String) { if (Preparation.entries.none { it.name == s }) add("$where: preparo desconhecido ($s).") }
        fun unitOk(s: String, where: String) { if (MeasureUnit.entries.none { it.name == s }) add("$where: unidade desconhecida ($s).") }
        fun nutrientsOk(n: BNutrients, where: String) {
            listOf(n.energyKcal, n.proteinG, n.carbohydrateG, n.fatG, n.saturatedFatG, n.fiberG, n.sodiumMg).forEach { v ->
                if (v != null && (!v.isFinite() || v < 0)) add("$where: valor nutricional inválido ($v).")
            }
        }
        fun slotOk(id: Long?, where: String) { if (id != null && id !in slotIds) add("$where: refeição #$id inexistente no arquivo.") }

        f.customFoods.forEach { food ->
            val w = "Alimento “${food.name}”"
            if (food.name.isBlank()) add("Alimento personalizado sem nome.")
            prepOk(food.preparation, w); unitOk(food.referenceUnit, w); nutrientsOk(food.nutrients, w)
            if (!food.referenceAmount.isFinite() || food.referenceAmount <= 0) add("$w: quantidade de referência inválida.")
            if (food.nutrients.energyKcal == null) add("$w: sem energia.")
        }
        f.foodUsage.forEach { refOk(it.food, "Favoritos") }
        f.diary.forEach { meal ->
            val w = "Diário ${meal.date}"
            dateOk(meal.date, w); slotOk(meal.slotId, w)
            meal.entries.forEach { e ->
                // Lançamentos do diário têm snapshot: alimento ausente é aceito (fica sem vínculo).
                amountOk(e.amount, w); prepOk(e.preparation, w); unitOk(e.unit, w); nutrientsOk(e.reference, w); nutrientsOk(e.consumed, w)
                if (!e.refAmount.isFinite() || e.refAmount <= 0) add("$w: referência inválida em ${e.foodName}.")
            }
        }
        f.goals.forEach { g ->
            dateOk(g.effectiveFrom, "Meta")
            if (!g.energyKcal.isFinite() || g.energyKcal <= 0) add("Meta de ${g.effectiveFrom}: calorias inválidas.")
            if (GoalOrigin.entries.none { it.name == g.origin }) add("Meta de ${g.effectiveFrom}: origem desconhecida.")
        }
        if (f.goals.map { it.effectiveFrom }.toSet().size != f.goals.size) add("Duas metas com a mesma data.")
        f.weights.forEach { w -> dateOk(w.date, "Peso"); if (!w.weightKg.isFinite() || w.weightKg !in 20.0..400.0) add("Peso de ${w.date} inválido.") }
        if (f.weights.map { it.date }.toSet().size != f.weights.size) add("Dois registros de peso na mesma data.")
        f.templates.forEach { t -> t.items.forEach { i -> refOk(i.food, "Refeição favorita “${t.name}”"); amountOk(i.amount, "Refeição favorita “${t.name}”") } }
        val planIds = f.plans.map { it.id }.toSet()
        f.plans.forEach { p -> p.items.forEach { i -> refOk(i.food, "Plano “${p.name}”"); amountOk(i.amount, "Plano “${p.name}”"); if (i.slotId == null) add("Plano “${p.name}”: item sem refeição.") else slotOk(i.slotId, "Plano “${p.name}”") } }
        f.plannedItems.forEach { i ->
            val w = "Planejamento ${i.date}"
            dateOk(i.date, w); slotOk(i.slotId, w); refOk(i.food, w); amountOk(i.amount, w)
            if (i.planId != null && i.planId !in planIds) add("$w: plano #${i.planId} inexistente no arquivo.")
        }
        f.shoppingLists.forEach { l -> dateOk(l.fromDate, "Lista “${l.name}”"); dateOk(l.toDate, "Lista “${l.name}”") }
        f.settings?.let { s -> if (ThemeMode.entries.none { it.name == s.themeMode }) add("Tema desconhecido: ${s.themeMode}.") }
        f.profile?.let { p ->
            if (BiologicalSex.entries.none { it.name == p.sex } || ActivityLevel.entries.none { it.name == p.activity } || Objective.entries.none { it.name == p.objective }) {
                add("Perfil da calculadora com valores desconhecidos.")
            }
        }
    }

    // ---------- Restauração ----------

    override suspend fun restore(text: String): BackupCheck {
        val (file, parseError) = parse(text)
        if (file == null) return BackupCheck(listOf(parseError!!), null, emptyMap())
        if (file.kind != BackupFile.KIND_FULL) return BackupCheck(listOf("Este arquivo contém só planos; use “Importar planos”."), file.exportedAt, counts(file))
        val keys = dao.libraryKeys()
        val errors = validate(file, keys.map { it.sourceKey }.toSet())
        if (errors.isNotEmpty()) return BackupCheck(errors.take(20), file.exportedAt, counts(file))

        db.withTransaction {
            dao.deleteShopping(); dao.deletePlanned(); dao.deletePlans(); dao.deleteTemplates(); dao.deleteDiary()
            dao.deleteGoals(); dao.deleteWeights(); dao.deleteUsage(); dao.deleteCustomFoods(); dao.deleteSlots()

            // A tabela de refeições foi esvaziada: os ids do arquivo são mantidos.
            val slotIds = file.mealSlots.associate { s -> s.id to dao.insertSlot(MealSlotEntity(id = s.id, name = s.name, defaultTimeMinutes = s.defaultTimeMinutes, sortOrder = s.sortOrder, isActive = s.isActive)) }
            val resolve = insertCustomFoods(file, keys.associate { it.sourceKey to it.id })

            file.foodUsage.forEach { u -> resolve(u.food)?.let { dao.insertUsage(FoodUsageEntity(it, u.isFavorite, u.useCount, u.lastUsedAt, u.lastAmount)) } }
            file.diary.forEach { m ->
                val mealId = dao.insertMeal(DiaryMealEntity(date = LocalDate.parse(m.date).toEpochDay(), slotId = m.slotId?.let(slotIds::get), name = m.name, sortOrder = m.sortOrder))
                dao.insertEntries(m.entries.map { e ->
                    DiaryEntryEntity(
                        mealId = mealId, foodId = e.food?.let(resolve), foodSourceKey = e.food?.libraryKey, foodName = e.foodName, brand = e.brand,
                        preparation = e.preparation, dataSourceCode = e.dataSourceCode, amount = e.amount, unit = e.unit, refAmount = e.refAmount,
                        reference = e.reference.toColumns(), consumed = e.consumed.toColumns(), sortOrder = e.sortOrder, createdAt = e.createdAt, updatedAt = e.createdAt,
                    )
                })
            }
            dao.insertGoals(file.goals.map { NutritionGoalEntity(effectiveFrom = LocalDate.parse(it.effectiveFrom).toEpochDay(), energyKcal = it.energyKcal, proteinG = it.proteinG, carbohydrateG = it.carbohydrateG, fatG = it.fatG, fiberG = it.fiberG, origin = it.origin, createdAt = 0) })
            dao.insertWeights(file.weights.map { WeightRecordEntity(date = LocalDate.parse(it.date).toEpochDay(), weightKg = it.weightKg, note = it.note, createdAt = 0) })
            file.templates.forEach { t ->
                val id = dao.insertTemplate(MealTemplateEntity(name = t.name, createdAt = 0))
                dao.insertTemplateItems(t.items.mapIndexed { i, it -> MealTemplateItemEntity(templateId = id, foodId = resolve(it.food)!!, amount = it.amount, sortOrder = i) })
            }
            val planIds = insertPlans(file, slotIds, resolve)
            dao.insertPlanned(file.plannedItems.mapIndexed { i, it ->
                PlannedItemEntity(date = LocalDate.parse(it.date).toEpochDay(), slotId = slotIds.getValue(it.slotId), foodId = resolve(it.food)!!, amount = it.amount, sourcePlanId = it.planId?.let(planIds::get), sortOrder = i)
            })
            file.shoppingLists.forEach { l ->
                val id = dao.insertShoppingList(ShoppingListEntity(name = l.name, fromDate = l.fromDate?.let { LocalDate.parse(it).toEpochDay() }, toDate = l.toDate?.let { LocalDate.parse(it).toEpochDay() }, createdAt = l.createdAt))
                dao.insertShoppingItems(l.items.mapIndexed { i, it -> ShoppingListItemEntity(listId = id, foodId = it.food?.let(resolve), name = it.name, detail = it.detail, quantity = it.quantity, unit = it.unit, isChecked = it.isChecked, isManual = it.isManual, sortOrder = i) })
            }
        }
        // Preferências ficam no DataStore (fora da transação do banco).
        file.settings?.let { settings.setThemeMode(ThemeMode.valueOf(it.themeMode)); settings.setDynamicColor(it.dynamicColor) }
        file.profile?.let {
            settings.saveProfile(UserProfile(BiologicalSex.valueOf(it.sex), it.ageYears, it.weightKg, it.heightCm, ActivityLevel.valueOf(it.activity),
                Objective.valueOf(it.objective), it.adjustmentPercent, it.proteinPerKg, it.fatPercent))
        }
        return BackupCheck(emptyList(), file.exportedAt, counts(file))
    }

    override suspend fun importPlans(text: String): Int {
        val (file, parseError) = parse(text)
        if (file == null) throw IllegalArgumentException(parseError)
        val keys = dao.libraryKeys()
        val errors = validate(file.copy(diary = emptyList(), plannedItems = emptyList(), templates = emptyList(), foodUsage = emptyList()), keys.map { it.sourceKey }.toSet())
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.take(5).joinToString("\n"))
        if (file.plans.isEmpty()) throw IllegalArgumentException("O arquivo não tem planos.")
        db.withTransaction {
            // Refeições do arquivo são casadas por nome com as existentes; as que faltarem são criadas.
            val existing = dao.slots()
            var nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val usedSlots = file.plans.flatMap { p -> p.items.mapNotNull { it.slotId } }.toSet()
            val slotIds = file.mealSlots.filter { it.id in usedSlots }.associate { s ->
                s.id to (existing.firstOrNull { it.name.equals(s.name, ignoreCase = true) }?.id
                    ?: dao.insertSlot(MealSlotEntity(name = s.name, defaultTimeMinutes = s.defaultTimeMinutes, sortOrder = nextOrder++, isActive = true)))
            }
            val usedCustom = file.plans.flatMap { p -> p.items.mapNotNull { it.food.customId } }.toSet()
            val resolve = insertCustomFoods(file.copy(customFoods = file.customFoods.filter { it.id in usedCustom }), keys.associate { it.sourceKey to it.id })
            insertPlans(file, slotIds, resolve)
        }
        return file.plans.size
    }

    /** Insere os personalizados do arquivo e devolve a função que resolve qualquer FoodRef para um id local. */
    private suspend fun insertCustomFoods(file: BackupFile, libraryIds: Map<String, Long>): (FoodRef) -> Long? {
        val userSource = db.foodQueryDao().sourceId(LibrarySeeder.USER_SOURCE) ?: error("Fonte USER ausente")
        val categoryIds = dao.categories().associate { it.code to it.id }
        val customIds = file.customFoods.associate { f ->
            f.id to dao.insertFood(
                FoodEntity(
                    origin = "CUSTOM", sourceKey = null, dataSourceId = userSource, sourceCode = null, sourceDescription = null, sourceGroup = null,
                    name = f.name, baseName = f.name, brand = f.brand, aliases = null, categoryId = f.categoryCode?.let(categoryIds::get),
                    preparation = f.preparation, referenceAmount = f.referenceAmount, referenceUnit = f.referenceUnit, nutrients = f.nutrients.toColumns(),
                    notes = null, derivedFromFoodId = f.derivedFromKey?.let(libraryIds::get), libraryVersion = null, isDeprecated = false,
                    isArchived = f.isArchived, createdAt = f.createdAt, updatedAt = f.createdAt,
                ),
            )
        }
        return { ref -> ref.customId?.let(customIds::get) ?: ref.libraryKey?.let(libraryIds::get) }
    }

    private suspend fun insertPlans(file: BackupFile, slotIds: Map<Long, Long>, resolve: (FoodRef) -> Long?): Map<Long, Long> =
        file.plans.associate { p ->
            val id = dao.insertPlan(DietPlanEntity(name = p.name, description = p.description, createdAt = 0, updatedAt = 0))
            dao.insertPlanItems(p.items.mapIndexed { i, it -> DietPlanItemEntity(planId = id, slotId = slotIds.getValue(it.slotId!!), foodId = resolve(it.food)!!, amount = it.amount, sortOrder = i) })
            p.id to id
        }

    private fun NutrientColumns.toB() = BNutrients(energyKcal, proteinG, carbohydrateG, fatG, saturatedFatG, fiberG, sodiumMg, traceMask, notApplicableMask)
    private fun BNutrients.toColumns() = NutrientColumns(energyKcal, proteinG, carbohydrateG, fatG, saturatedFatG, fiberG, sodiumMg, traceMask, notApplicableMask)
    private fun NutrientColumns.value(n: Nutrient): Double? = when (n) {
        Nutrient.ENERGY -> energyKcal
        Nutrient.PROTEIN -> proteinG
        Nutrient.CARBOHYDRATE -> carbohydrateG
        Nutrient.FAT -> fatG
        Nutrient.SATURATED_FAT -> saturatedFatG
        Nutrient.FIBER -> fiberG
        Nutrient.SODIUM -> sodiumMg
    }

    companion object {
        const val SCHEMA_VERSION = 2
    }
}
