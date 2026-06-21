package com.junnz.wear.di

import android.content.Context
import com.junnz.wear.sync.CaptureResultBus
import com.junnz.wear.sync.WatchReminderCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearModule {

    @Provides
    @Singleton
    fun provideWatchReminderCache(): WatchReminderCache = WatchReminderCache()

    @Provides
    @Singleton
    fun provideCaptureResultBus(): CaptureResultBus = CaptureResultBus()
}
