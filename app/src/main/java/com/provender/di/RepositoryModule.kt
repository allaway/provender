package com.provender.di

import com.provender.data.repository.DefaultInventoryRepository
import com.provender.data.repository.InventoryRepository
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
    abstract fun bindInventoryRepository(impl: DefaultInventoryRepository): InventoryRepository
}
