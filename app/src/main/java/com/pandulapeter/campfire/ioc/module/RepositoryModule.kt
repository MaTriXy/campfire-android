package com.pandulapeter.campfire.ioc.module

import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.storage.StorageManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object RepositoryModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideSongInfoRepository(storageManager: StorageManager, networkManager: NetworkManager) = SongInfoRepository(storageManager, networkManager)
}