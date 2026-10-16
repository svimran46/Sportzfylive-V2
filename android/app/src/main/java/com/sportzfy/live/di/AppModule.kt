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
import com.sportzfy.live.core.time.ZoneProvider
import com.sportzfy.live.data.config.AppConfigRepositoryImpl
import com.sportzfy.live.data.config.DataStoreConfigCache
import com.sportzfy.live.data.channels.ChannelLibraryRepositoryImpl
import com.sportzfy.live.data.channels.ChannelRepositoryImpl
import com.sportzfy.live.data.channels.HttpChannelSource
import com.sportzfy.live.data.config.HttpConfigSource
import com.sportzfy.live.data.repository.FavoritesRepositoryImpl
import com.sportzfy.live.data.repository.MatchRepositoryImpl
import com.sportzfy.live.data.repository.StreamRepositoryImpl
import com.sportzfy.live.data.repository.WorkerStreamFallback
import com.sportzfy.live.data.settings.SettingsRepositoryImpl
import com.sportzfy.live.domain.repository.AppConfigRepository
import com.sportzfy.live.domain.repository.ChannelLibraryRepository
import com.sportzfy.live.domain.repository.ChannelRepository
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.domain.repository.SettingsRepository
import com.sportzfy.live.domain.repository.StreamRepository
import com.sportzfy.live.presentation.catalog.CatalogViewModel
import com.sportzfy.live.presentation.livetv.LiveTvViewModel
import com.sportzfy.live.presentation.livetv.player.TvPlayerViewModel
import com.sportzfy.live.presentation.navigation.WatchRoute
import com.sportzfy.live.presentation.settings.SettingsViewModel
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
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.channelLibraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "channel_library")

val networkModule = module {
    single<Json> { NetworkJson }
    single<OkHttpClient> { HttpClientFactory.okHttpClient(enableLogging = BuildConfig.DEBUG) }
    single<Retrofit> { HttpClientFactory.retrofit(get(), get()) }
    single<StreamedApiService> { get<Retrofit>().create() }
}

val dataModule = module {
    single<Clock> { Clock.System }
    single<ZoneProvider> { ZoneProvider.System }
    single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
    single<StreamRepository> {
        val config = get<AppConfigRepository>()
        StreamRepositoryImpl(
            api = get(),
            json = get(),
            clock = get(),
            // streamed.pk can block phones (DDoS-Guard); the website's Worker is the second route.
            fallback = WorkerStreamFallback(get(), get(), baseUrl = { config.config.value.streamProxyUrl }),
        )
    }
    single<FavoritesRepository> { FavoritesRepositoryImpl(androidContext().favoritesDataStore) }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext().settingsDataStore) }
    single<ChannelLibraryRepository> { ChannelLibraryRepositoryImpl(androidContext().channelLibraryDataStore) }
    single<AppConfigRepository> {
        AppConfigRepositoryImpl(
            source = HttpConfigSource(get(), BuildConfig.REMOTE_CONFIG_URL, get()),
            cache = DataStoreConfigCache(androidContext().appConfigDataStore),
            json = get(),
            clock = get(),
        )
    }
    single<ChannelRepository> {
        val config = get<AppConfigRepository>()
        ChannelRepositoryImpl(
            source = HttpChannelSource(get(), get()),
            json = get(),
            clock = get(),
            // Read on each load, so a new channelsUrl applies without a restart.
            url = { config.config.value.channelsUrl },
        )
    }
}

val presentationModule = module {
    viewModelOf(::CatalogViewModel)
    viewModelOf(::LiveTvViewModel)
    viewModelOf(::TvPlayerViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel {
        WatchViewModel(
            initialMatchId = get<SavedStateHandle>().toRoute<WatchRoute>().matchId,
            matchRepository = get(),
            streamRepository = get(),
            favoritesRepository = get(),
            settings = get<SettingsRepository>().settings,
        )
    }
}

val appModules = listOf(networkModule, dataModule, presentationModule)
