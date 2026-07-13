package com.provender

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.provender.ai.LlmEngine
import com.provender.data.repository.InventoryRepository
import com.provender.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ProvenderApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var inventoryRepository: InventoryRepository

    @Inject lateinit var llmEngine: LlmEngine

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            inventoryRepository.seedDefaultLocationsIfEmpty()
        }
        // Eager session init hides model-load latency (SPEC §2); a no-op failure when the
        // model isn't downloaded or the device can't run it.
        applicationScope.launch {
            llmEngine.warmUp()
        }
    }
}
