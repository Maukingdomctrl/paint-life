package com.mauforge.paintlife.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.SessionClock
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-wide scope for work that must outlive any screen (e.g. first-launch bookkeeping). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaintLifeDatabase =
        PaintLifeDatabase.build(context)

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("user_preferences") },
        )

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider =
        TimeProvider(clock = Clock.systemUTC(), zoneSupplier = { ZoneId.systemDefault() })

    @Provides
    @Singleton
    fun provideDayClock(@ApplicationContext context: Context, time: TimeProvider): DayClock =
        DayClock(time, DayClock.systemTimeChanges(context))

    @Provides
    @Singleton
    fun provideSessionClock(@ApplicationContext context: Context): SessionClock = SessionClock.system(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
