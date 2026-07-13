package com.provender.di

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import com.provender.ai.DefaultModelRepository
import com.provender.ai.DeviceSpecs
import com.provender.ai.ModelRepository
import com.provender.data.settings.AppSettingsRepository
import com.provender.data.settings.DefaultAppSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelProvidesModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("provender_settings", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideDeviceSpecs(@ApplicationContext context: Context): DeviceSpecs {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService<ActivityManager>()?.getMemoryInfo(memoryInfo)
        return DeviceSpecs(
            totalRamBytes = memoryInfo.totalMem,
            supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS.orEmpty().toList(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelBindsModule {

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: DefaultModelRepository): ModelRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: DefaultAppSettingsRepository): AppSettingsRepository
}
