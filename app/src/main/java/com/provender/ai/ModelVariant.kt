package com.provender.ai

/**
 * Downloadable Gemma 3n model variants (SPEC.md §2 Tier 2). E2B is the default; E4B is the
 * quality option for high-RAM devices.
 *
 * The Hugging Face repos are license-gated: unauthenticated downloads return 401/403 until
 * the user has accepted Google's Gemma license. The Settings screen therefore also offers a
 * document-picker import as the reliable path (download the file in a browser, then import).
 */
enum class ModelVariant(
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val huggingFacePage: String,
    /** Approximate download size, for display only. */
    val approxDownloadBytes: Long,
    /** Minimum device RAM to enable this variant. */
    val minTotalRamBytes: Long,
) {
    E2B(
        displayName = "Gemma 3n E2B (recommended)",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        downloadUrl =
            "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
        huggingFacePage = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm",
        approxDownloadBytes = 3_930_000_000L, // ~3.7 GiB
        minTotalRamBytes = 5_500L * 1024 * 1024, // 6 GB devices report ~5.6-5.8 GiB
    ),
    E4B(
        displayName = "Gemma 3n E4B (higher quality)",
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        downloadUrl =
            "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm",
        huggingFacePage = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm",
        approxDownloadBytes = 4_700_000_000L, // ~4.4 GiB
        minTotalRamBytes = 7_000L * 1024 * 1024, // 8 GB devices
    ),
}

/** Inference backend choice; benchmarked selection may replace the manual toggle later. */
enum class LlmBackend {
    CPU,
    GPU,
}
