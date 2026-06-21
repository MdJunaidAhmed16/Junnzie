package com.junnz.phone

import android.app.Application
import com.junnz.phone.service.WearCaptureHandler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class JunnzPhoneApp : Application() {

    // Injected to force construction at app startup — registers MessageClient listener immediately
    @Inject lateinit var wearCaptureHandler: WearCaptureHandler

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
