package com.simats.sdc.di

import com.simats.sdc.data.repository.OralCareRepositoryImpl
import com.simats.sdc.domain.repository.OralCareRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindOralCareRepository(impl: OralCareRepositoryImpl): OralCareRepository
}
