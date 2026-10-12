package com.sportzfy.live

import android.app.Application
import com.sportzfy.live.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SportzfyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SportzfyApp)
            modules(appModules)
        }
    }
}
