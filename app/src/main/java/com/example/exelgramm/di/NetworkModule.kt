package com.example.exelgramm.di

import android.content.Context
import com.example.exelgramm.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SEC = 30L
    private const val READ_TIMEOUT_SEC = 60L
    private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideBaseHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES))
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @Named("noRedirect")
    fun provideNoRedirectHttpClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    @Provides
    @Singleton
    @Named("default")
    fun provideDefaultHttpClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .followRedirects(true)
        .build()
}
