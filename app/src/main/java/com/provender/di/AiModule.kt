package com.provender.di

import com.provender.ai.LitertLmEngine
import com.provender.ai.LlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    /** The real on-device engine; tests keep using FakeLlmEngine directly. */
    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: LitertLmEngine): LlmEngine
}
