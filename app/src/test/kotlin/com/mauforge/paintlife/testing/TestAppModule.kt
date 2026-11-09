package com.mauforge.paintlife.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.di.AppModule
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import javax.inject.Singleton

/**
 * Replaces [AppModule] in @HiltAndroidTest tests: a fresh in-memory database and a throwaway
 * DataStore file per test, the system clock, and no system broadcasts.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaintLifeDatabase =
        Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.cacheDir, "test_prefs_${UUID.randomUUID()}.preferences_pb") },
        )

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider(Clock.systemUTC()) { ZoneId.systemDefault() }

    @Provides
    @Singleton
    fun provideDayClock(time: TimeProvider): DayClock = DayClock(time, emptyFlow())

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
