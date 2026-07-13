package com.provender.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.provender.ai.LlmBackend
import com.provender.ai.ModelVariant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsRepositoryTest {

    private fun newRepository(): DefaultAppSettingsRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return DefaultAppSettingsRepository(
            context.getSharedPreferences("test_settings", Context.MODE_PRIVATE),
        )
    }

    @Test
    fun `defaults are E2B and CPU`() {
        val repository = newRepository()

        assertEquals(ModelVariant.E2B, repository.selectedVariant.value)
        assertEquals(LlmBackend.CPU, repository.backend.value)
    }

    @Test
    fun `writes survive a repository restart`() {
        newRepository().apply {
            setSelectedVariant(ModelVariant.E4B)
            setBackend(LlmBackend.GPU)
        }

        val reloaded = newRepository()

        assertEquals(ModelVariant.E4B, reloaded.selectedVariant.value)
        assertEquals(LlmBackend.GPU, reloaded.backend.value)
    }

    @Test
    fun `flows emit the new value on set`() {
        val repository = newRepository()

        repository.setBackend(LlmBackend.GPU)

        assertEquals(LlmBackend.GPU, repository.backend.value)
    }
}
