package com.simats.sdc.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simats.sdc.BuildConfig
import com.simats.sdc.core.database.OralCareDatabase
import com.simats.sdc.core.database.PendingImageDao
import com.simats.sdc.core.database.ScreeningDraftDao
import com.simats.sdc.core.network.AuthInterceptor
import com.simats.sdc.core.network.TokenAuthenticator
import com.simats.sdc.data.remote.OralCareApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Never log patient PII bodies in release builds
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideOralCareApiService(retrofit: Retrofit): OralCareApiService =
        retrofit.create(OralCareApiService::class.java)

    @Provides
    @Singleton
    fun provideOralCareDatabase(@ApplicationContext context: Context): OralCareDatabase =
        Room.databaseBuilder(context, OralCareDatabase::class.java, "oralcare_offline.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideScreeningDraftDao(db: OralCareDatabase): ScreeningDraftDao = db.screeningDraftDao()

    @Provides
    fun providePendingImageDao(db: OralCareDatabase): PendingImageDao = db.pendingImageDao()
}
