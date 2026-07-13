package com.provender.di

import com.provender.data.repository.BarcodeLookupRepository
import com.provender.data.repository.DefaultBarcodeLookupRepository
import com.provender.data.repository.DefaultInventoryRepository
import com.provender.data.repository.DefaultSnapshotRepository
import com.provender.data.repository.InventoryRepository
import com.provender.data.repository.SnapshotRepository
import com.provender.mlkit.MlKitOcrClient
import com.provender.mlkit.OcrClient
import com.provender.network.BarcodeProductSource
import com.provender.network.OpenFoodFactsSource
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

    @Binds
    @Singleton
    abstract fun bindSnapshotRepository(impl: DefaultSnapshotRepository): SnapshotRepository

    @Binds
    @Singleton
    abstract fun bindBarcodeLookupRepository(
        impl: DefaultBarcodeLookupRepository,
    ): BarcodeLookupRepository

    @Binds
    @Singleton
    abstract fun bindBarcodeProductSource(impl: OpenFoodFactsSource): BarcodeProductSource

    @Binds
    @Singleton
    abstract fun bindOcrClient(impl: MlKitOcrClient): OcrClient
}
