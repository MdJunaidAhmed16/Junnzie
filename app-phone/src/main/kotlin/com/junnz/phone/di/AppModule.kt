package com.junnz.phone.di

import android.content.Context
import androidx.room.Room
import com.junnz.phone.BuildConfig
import com.junnz.phone.data.db.AppDatabase
import com.junnz.phone.data.db.ReminderDao
import com.junnz.phone.data.repository.ReminderRepository
import com.junnz.phone.embedding.GoogleEmbeddingService
import com.junnz.phone.embedding.LocalEmbeddingService
import com.junnz.phone.matching.InMemoryCooldownStore
import com.junnz.phone.matching.RepositoryReminderQuery
import com.junnz.phone.service.AsrService
import com.junnz.phone.service.GoogleCloudSpeechAsrService
import com.junnz.phone.service.StubAsrService
import com.junnz.shared.matching.CooldownStore
import com.junnz.shared.matching.EmbeddingService
import com.junnz.shared.matching.MatchingEngine
import com.junnz.shared.matching.ReminderQuery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "junnz.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun provideAsrService(): AsrService {
        val key = BuildConfig.GOOGLE_SPEECH_API_KEY
        return if (key.isNotBlank()) {
            GoogleCloudSpeechAsrService(key)
        } else {
            StubAsrService()
        }
    }

    @Provides
    @Singleton
    fun provideEmbeddingService(): EmbeddingService {
        val key = BuildConfig.GOOGLE_EMBEDDING_API_KEY
        return if (key.isNotBlank()) GoogleEmbeddingService(key) else LocalEmbeddingService()
    }

    @Provides
    @Singleton
    fun provideCooldownStore(): CooldownStore = InMemoryCooldownStore()

    @Provides
    @Singleton
    fun provideReminderQuery(repository: ReminderRepository): ReminderQuery =
        RepositoryReminderQuery(repository)

    @Provides
    @Singleton
    fun provideMatchingEngine(
        reminderQuery: ReminderQuery,
        embeddingService: EmbeddingService,
        cooldownStore: CooldownStore,
    ): MatchingEngine = MatchingEngine(reminderQuery, embeddingService, cooldownStore)
}
