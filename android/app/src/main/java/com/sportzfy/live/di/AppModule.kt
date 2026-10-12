package com.sportzfy.live.di

import com.sportzfy.live.BuildConfig
import com.sportzfy.live.core.network.HttpClientFactory
import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.core.network.StreamedApiService
import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.data.repository.MatchRepositoryImpl
import com.sportzfy.live.data.repository.StreamRepositoryImpl
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.domain.repository.StreamRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.create

val networkModule = module {
    single<Json> { NetworkJson }
    single<OkHttpClient> { HttpClientFactory.okHttpClient(enableLogging = BuildConfig.DEBUG) }
    single<Retrofit> { HttpClientFactory.retrofit(get(), get()) }
    single<StreamedApiService> { get<Retrofit>().create() }
}

val dataModule = module {
    single<Clock> { Clock.System }
    single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
    single<StreamRepository> { StreamRepositoryImpl(get(), get(), get()) }
}

val appModules = listOf(networkModule, dataModule)
