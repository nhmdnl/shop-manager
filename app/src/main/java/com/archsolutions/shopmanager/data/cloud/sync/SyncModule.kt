package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {
    @Binds abstract fun bindCloudSync(sync: RoomCloudSync): CloudSync
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideFillMarker(@ApplicationContext context: Context): FillMarker = FillMarker(context.filesDir)
}
