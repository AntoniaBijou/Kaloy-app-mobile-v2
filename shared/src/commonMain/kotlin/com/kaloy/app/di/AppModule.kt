package com.kaloy.app.di

import com.kaloy.app.core.network.createHttpClient
import com.kaloy.app.core.offline.OfflineDownloadCoordinator
import com.kaloy.app.core.offline.OfflineFileStore
import com.kaloy.app.core.offline.OfflinePlaylistCatalog
import com.kaloy.app.core.queue.QueueManager
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.repository.AuthRepository
import com.kaloy.app.data.repository.AuthRepositoryImpl
import com.kaloy.app.data.repository.HistoriqueRechercheRepository
import com.kaloy.app.data.repository.LecteurRepository
import com.kaloy.app.data.repository.LecteurRepositoryImpl
import com.kaloy.app.data.repository.MeRepository
import com.kaloy.app.data.repository.MeRepositoryImpl
import com.kaloy.app.data.repository.PlaylistRepository
import com.kaloy.app.data.repository.PlaylistRepositoryImpl
import com.kaloy.app.presentation.lecteur.LecteurViewModel
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val appModule = module {
    single { Settings() }
    single { AuthSessionManager(get()) }
    single { OfflinePlaylistCatalog(get()) }
    single { OfflineDownloadCoordinator(get(), get<OfflineFileStore>(), get(), get()) }
    single { createHttpClient(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<MeRepository> { MeRepositoryImpl(get(), get()) }
    single<LecteurRepository> { LecteurRepositoryImpl(get(), get()) }
    single { HistoriqueRechercheRepository(get(), get()) }
    single { QueueManager() }
    single { LecteurViewModel(get(), get(), get(), get(), get(), get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get()) }
}
