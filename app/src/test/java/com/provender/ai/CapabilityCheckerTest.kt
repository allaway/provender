package com.provender.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityCheckerTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `capable device passes for E2B`() {
        val specs = DeviceSpecs(totalRamBytes = 8 * gb, supported64BitAbis = listOf("arm64-v8a"))

        val result = CapabilityChecker.check(specs, ModelVariant.E2B)

        assertTrue(result.supported)
        assertEquals(null, result.reason)
    }

    @Test
    fun `low ram device is rejected with an explanation`() {
        val specs = DeviceSpecs(totalRamBytes = 4 * gb, supported64BitAbis = listOf("arm64-v8a"))

        val result = CapabilityChecker.check(specs, ModelVariant.E2B)

        assertTrue(!result.supported)
        assertNotNull(result.reason)
    }

    @Test
    fun `E4B needs more ram than E2B`() {
        val sixGbDevice = DeviceSpecs(
            totalRamBytes = 6 * gb,
            supported64BitAbis = listOf("arm64-v8a"),
        )

        assertTrue(CapabilityChecker.check(sixGbDevice, ModelVariant.E2B).supported)
        assertTrue(!CapabilityChecker.check(sixGbDevice, ModelVariant.E4B).supported)
    }

    @Test
    fun `non-arm64 device is rejected`() {
        val specs = DeviceSpecs(totalRamBytes = 16 * gb, supported64BitAbis = emptyList())

        val result = CapabilityChecker.check(specs, ModelVariant.E2B)

        assertTrue(!result.supported)
        assertNotNull(result.reason)
    }
}
