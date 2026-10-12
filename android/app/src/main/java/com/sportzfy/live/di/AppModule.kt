package com.sportzfy.live.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.sportzfy.live.BuildConfig
import com.sportzfy.live.core.network.HttpClientFactory
import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.core.network.StreamedApiService
import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.data.config.AppConfigRepositoryImpl
import com.sportzfy.live.data.config.DataStoreConfigCache
import com.sportzfy.live.data.config.HttpConfigSource
import com.sportzfy.live.data.repository.FavoritesRepositoryImpl
import com.sportzfy.live.data.repository.MatchRepositoryImpl
import com.sportzfy.live.data.repository.StreamRepositoryImpl
import com.sportzfy.live.domain.repository.AppConfigRepository
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.domain.repository.StreamRepository
import com.sportzfy.live.presentation.catalog.CatalogViewModel
import com.sportzfy.live.presentation.navigation.WatchRoute
import com.sportzfy.live.presentation.watch.WatchViewModel
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.create

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")
private val Context.appConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

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
    single<FavoritesRepository> { FavoritesRepositoryImpl(androidContext().favoritesDataStore) }
    single<AppConfigRepository> {
        AppConfigRepositoryImpl(
            source = HttpConfigSource(get(), BuildConfig.REMOTE_CONFIG_URL, get()),
            cache = DataStoreConfigCache(androidContext().appConfigDataStore),
            json = get(),
            clock = get(),
        )
    }
}

val presentationModule = module {
    viewModelOf(::CatalogViewModel)
    viewModel {
        WatchViewModel(
            initialMatchId = get<SavedStateHandle>().toRoute<WatchRoute>().matchId,
            matchRepository = get(),
            streamRepository = get(),
            favoritesRepository = get(),
        )
    }
}

val appModules = listOf(networkModule, dataModule, presentationModule)
