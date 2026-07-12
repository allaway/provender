package com.provender.di

import com.provender.ai.FakeLlmEngine
import com.provender.ai.LlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    /** FakeLlmEngine until the LiteRT-LM implementation lands in Phase 2. */
    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: FakeLlmEngine): LlmEngine
}
