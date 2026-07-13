package com.provender.ai

import javax.inject.Inject
import javax.inject.Singleton

/** Raw hardware facts, injected so capability logic stays a pure, JVM-testable function. */
data class DeviceSpecs(
    val totalRamBytes: Long,
    val supported64BitAbis: List<String>,
)

/** Result of the VLM capability check (SPEC.md §2 "Hardware reality check"). */
data class VlmCapability(
    val supported: Boolean,
    /** Human-readable explanation when [supported] is false. */
    val reason: String? = null,
)

@Singleton
class CapabilityChecker @Inject constructor(
    private val specs: DeviceSpecs,
) {

    fun check(variant: ModelVariant): VlmCapability = check(specs, variant)

    companion object {
        fun check(specs: DeviceSpecs, variant: ModelVariant): VlmCapability {
            if ("arm64-v8a" !in specs.supported64BitAbis) {
                return VlmCapability(
                    supported = false,
                    reason = "This device's processor (no arm64 support) can't run the " +
                        "on-device model. Barcode scanning and manual entry still work fully.",
                )
            }
            if (specs.totalRamBytes < variant.minTotalRamBytes) {
                val neededGb = variant.minTotalRamBytes / (1024.0 * 1024 * 1024)
                return VlmCapability(
                    supported = false,
                    reason = "This device doesn't have enough memory for ${variant.displayName} " +
                        "(needs ~%.0f GB RAM). Barcode scanning and manual entry still work fully."
                            .format(neededGb + 0.5),
                )
            }
            return VlmCapability(supported = true)
        }
    }
}
