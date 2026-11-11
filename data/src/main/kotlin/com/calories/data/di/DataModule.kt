package com.calories.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.calories.data.db.CaloriesDatabase
import com.calories.data.preferences.SettingsRepositoryImpl
import com.calories.data.repository.DiaryRepositoryImpl
import com.calories.data.repository.FoodRepositoryImpl
import com.calories.data.repository.GoalRepositoryImpl
import com.calories.data.repository.MealSlotRepositoryImpl
import com.calories.data.repository.MealTemplateRepositoryImpl
import com.calories.data.seed.AndroidLibraryAssetSource
import com.calories.data.seed.LibraryAssetSource
import com.calories.data.seed.LibrarySeeder
import com.calories.domain.repository.DiaryRepository
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.GoalRepository
import com.calories.domain.repository.LibraryRepository
import com.calories.domain.repository.MealSlotRepository
import com.calories.domain.repository.MealTemplateRepository
import com.calories.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CaloriesDatabase =
        Room.databaseBuilder(context, CaloriesDatabase::class.java, CaloriesDatabase.NAME).build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun foods(impl: FoodRepositoryImpl): FoodRepository
    @Binds abstract fun diary(impl: DiaryRepositoryImpl): DiaryRepository
    @Binds abstract fun slots(impl: MealSlotRepositoryImpl): MealSlotRepository
    @Binds abstract fun goals(impl: GoalRepositoryImpl): GoalRepository
    @Binds abstract fun templates(impl: MealTemplateRepositoryImpl): MealTemplateRepository
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun library(impl: LibrarySeeder): LibraryRepository
    @Binds abstract fun assetSource(impl: AndroidLibraryAssetSource): LibraryAssetSource
}
