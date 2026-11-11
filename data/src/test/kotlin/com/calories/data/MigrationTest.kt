package com.calories.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.calories.data.db.CaloriesDatabase
import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.GoalRepositoryImpl
import com.calories.data.repository.WeightRepositoryImpl
import com.calories.domain.model.WeightRecord
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

/** A versão 0.3.0 (esquema 1) precisa abrir na 0.4.0 (esquema 2) sem perder nada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CaloriesDatabase::class.java)

    @Test
    fun `migracao 1 para 2 preserva diario, metas e refeicoes e cria as tabelas novas`() = runBlocking<Unit> {
        val day = LocalDate.of(2026, 11, 10).toEpochDay()
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO data_source (id, code, name, license, citation, carbohydrateBasis) VALUES (1, 'TACO4', 'TACO', 'L', 'C', 'TOTAL_BY_DIFFERENCE')")
            db.execSQL(
                "INSERT INTO food (id, origin, sourceKey, dataSourceId, name, preparation, referenceAmount, referenceUnit, energyKcal, proteinG, carbohydrateG, fatG, " +
                    "traceMask, notApplicableMask, isDeprecated, isArchived, createdAt, updatedAt) VALUES " +
                    "(1, 'LIBRARY', 'TACO4:410', 1, 'Frango, peito, sem pele, grelhado', 'GRILLED', 100, 'GRAM', 159, 32, 0, 2.5, 0, 32, 0, 0, 0, 0)",
            )
            db.execSQL("INSERT INTO meal_slot (id, name, defaultTimeMinutes, sortOrder, isActive) VALUES (2, 'Almoço', 720, 1, 1)")
            db.execSQL("INSERT INTO diary_meal (id, date, slotId, name, sortOrder) VALUES (1, $day, 2, 'Almoço', 1)")
            db.execSQL(
                "INSERT INTO diary_entry (id, mealId, foodId, foodSourceKey, foodName, preparation, dataSourceCode, amount, unit, refAmount, " +
                    "ref_energyKcal, ref_proteinG, ref_carbohydrateG, ref_fatG, ref_traceMask, ref_notApplicableMask, " +
                    "energyKcal, proteinG, carbohydrateG, fatG, traceMask, notApplicableMask, sortOrder, createdAt, updatedAt) VALUES " +
                    "(1, 1, 1, 'TACO4:410', 'Frango, peito, sem pele, grelhado', 'GRILLED', 'TACO4', 150, 'GRAM', 100, 159, 32, 0, 2.5, 0, 32, 238.5, 48, 0, 3.75, 0, 32, 0, 0, 0)",
            )
            db.execSQL("INSERT INTO nutrition_goal (id, effectiveFrom, energyKcal, origin, createdAt) VALUES (1, ${day - 5}, 2100, 'MANUAL', 0)")
        }

        helper.runMigrationsAndValidate(name, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), CaloriesDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val entry = DiaryRepositoryImpl(db, TestDb.clock).observeMeals(LocalDate.ofEpochDay(day)).first().single().entries.single()
            assertEquals(NutrientValue.Measured(238.5), entry.consumed[Nutrient.ENERGY])
            assertEquals(NutrientValue.NotApplicable, entry.consumed[Nutrient.FIBER])
            assertEquals(2100.0, GoalRepositoryImpl(db, TestDb.clock).goalFor(LocalDate.ofEpochDay(day))?.energyKcal)
            // Tabelas novas utilizáveis.
            val weights = WeightRepositoryImpl(db, TestDb.clock)
            weights.save(WeightRecord(LocalDate.ofEpochDay(day), 78.4, null))
            assertEquals(78.4, weights.observeLatest().first()?.weightKg)
        } finally {
            db.close()
        }
    }
}
