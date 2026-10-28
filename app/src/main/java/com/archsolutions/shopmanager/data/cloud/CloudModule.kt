package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import com.archsolutions.shopmanager.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudModule {
    @Provides
    @Singleton
    fun provideCloudStore(@ApplicationContext context: Context): CloudStore =
        CloudStore(context.cloudDataStore, KeystoreTokenCipher())

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    /** Null when the build has no cloud URL (or an unusable one): the cloud then stays Off. */
    @Provides
    @Singleton
    fun provideCloudApi(client: OkHttpClient, store: CloudStore): CloudApi? {
        val base = BuildConfig.CLOUD_BASE_URL.takeIf { it.isNotBlank() }?.toHttpUrlOrNull() ?: return null
        return CloudApi(base, client, BuildConfig.VERSION_NAME) { store.token() }
    }
}
