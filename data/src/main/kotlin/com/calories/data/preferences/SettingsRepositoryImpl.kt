package com.calories.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calories.domain.model.AppSettings
import com.calories.domain.model.ThemeMode
import com.calories.domain.model.UserProfile
import com.calories.domain.nutrition.ActivityLevel
import com.calories.domain.nutrition.BiologicalSex
import com.calories.domain.nutrition.Objective
import com.calories.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Preferências e perfil da calculadora, guardados só no aparelho (DataStore). */
class SettingsRepositoryImpl @Inject constructor(private val store: DataStore<Preferences>) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SEX = stringPreferencesKey("profile_sex")
        val AGE = intPreferencesKey("profile_age")
        val WEIGHT = doublePreferencesKey("profile_weight")
        val HEIGHT = doublePreferencesKey("profile_height")
        val ACTIVITY = stringPreferencesKey("profile_activity")
        val OBJECTIVE = stringPreferencesKey("profile_objective")
        val ADJUSTMENT = doublePreferencesKey("profile_adjustment")
        val PROTEIN_PER_KG = doublePreferencesKey("profile_protein_per_kg")
        val FAT_PERCENT = doublePreferencesKey("profile_fat_percent")
    }

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: false,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.THEME] = mode.name } }

    override suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[Keys.DYNAMIC] = enabled } }

    override val profile: Flow<UserProfile?> = store.data.map { p ->
        val sex = p[Keys.SEX]?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() } ?: return@map null
        UserProfile(
            sex = sex,
            ageYears = p[Keys.AGE] ?: return@map null,
            weightKg = p[Keys.WEIGHT] ?: return@map null,
            heightCm = p[Keys.HEIGHT] ?: return@map null,
            activity = p[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: ActivityLevel.MODERATE,
            objective = p[Keys.OBJECTIVE]?.let { runCatching { Objective.valueOf(it) }.getOrNull() } ?: Objective.MAINTAIN,
            adjustmentPercent = p[Keys.ADJUSTMENT] ?: 15.0,
            proteinPerKg = p[Keys.PROTEIN_PER_KG] ?: 1.6,
            fatPercent = p[Keys.FAT_PERCENT] ?: 30.0,
        )
    }

    override suspend fun saveProfile(profile: UserProfile) {
        store.edit {
            it[Keys.SEX] = profile.sex.name
            it[Keys.AGE] = profile.ageYears
            it[Keys.WEIGHT] = profile.weightKg
            it[Keys.HEIGHT] = profile.heightCm
            it[Keys.ACTIVITY] = profile.activity.name
            it[Keys.OBJECTIVE] = profile.objective.name
            it[Keys.ADJUSTMENT] = profile.adjustmentPercent
            it[Keys.PROTEIN_PER_KG] = profile.proteinPerKg
            it[Keys.FAT_PERCENT] = profile.fatPercent
        }
    }
}
