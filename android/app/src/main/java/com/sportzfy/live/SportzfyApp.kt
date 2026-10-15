package com.sportzfy.live

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sportzfy.live.di.appModules
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SportzfyApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SportzfyApp)
            modules(appModules)
        }
    }

    // Coil shares the app's OkHttp client so posters and crests from
    // streamed.pk carry the same browser headers as API calls.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { get<OkHttpClient>() })) }
            .crossfade(true)
            .build()
}
