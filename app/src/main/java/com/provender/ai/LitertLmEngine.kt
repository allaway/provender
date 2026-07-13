package com.provender.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.provender.data.settings.AppSettingsRepository
import com.provender.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The real on-device engine: Gemma 3n via the LiteRT-LM Kotlin API (SPEC §2 Tier 2).
 *
 * All LiteRT-LM calls are blocking, so everything runs on [ioDispatcher]; a single [Mutex]
 * serializes inference because one loaded model serves one request at a time. The native
 * engine is created lazily and rebuilt when the model path or backend preference changes.
 */
@Singleton
class LitertLmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val settings: AppSettingsRepository,
    private val capabilityChecker: CapabilityChecker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LlmEngine {

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var engineKey: String? = null

    override suspend fun extractInventory(
        photos: List<PhotoInput>,
        ocrText: String?,
    ): Result<List<ExtractedItem>> = withConversation(EXTRACTION_SAMPLER) { conversation ->
        val contents = buildList {
            photos.forEach { add(Content.ImageFile(it.toAbsolutePath())) }
            add(Content.Text(Prompts.extraction(ocrText)))
        }
        val reply = conversation.sendMessage(Contents.of(contents)).toString()
        parseWithRetry<ExtractedItem>(conversation, reply)
    }

    override suspend fun generateIdeas(request: IdeaRequest): Result<List<MealIdea>> =
        withConversation(IDEAS_SAMPLER) { conversation ->
            val reply = conversation.sendMessage(Prompts.ideas(request)).toString()
            parseWithRetry<MealIdea>(conversation, reply)
        }

    override suspend fun warmUp(): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching { obtainEngine() }.map { }
        }
    }

    override suspend fun rawPrompt(prompt: String): Result<String> =
        withConversation(RAW_SAMPLER) { conversation ->
            conversation.sendMessage(prompt).toString()
        }

    /** Runs [block] with a fresh conversation on the shared engine, serialized + off-main. */
    private suspend fun <T> withConversation(
        sampler: SamplerConfig,
        block: (Conversation) -> T,
    ): Result<T> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val engine = obtainEngine()
                engine.createConversation(ConversationConfig(samplerConfig = sampler))
                    .use(block)
            }
        }
    }

    /** Must be called with [mutex] held. */
    private fun obtainEngine(): Engine {
        val variant = settings.selectedVariant.value
        val capability = capabilityChecker.check(variant)
        if (!capability.supported) {
            throw ModelNotReadyException(capability.reason ?: "Device not capable")
        }
        val modelFile = modelRepository.installedModelFile(variant)
            ?: throw ModelNotReadyException(
                "The on-device model isn't downloaded yet. Get it in Settings → Model.",
            )
        val backend = settings.backend.value
        val key = "${modelFile.absolutePath}|$backend"
        engine?.let { existing ->
            if (engineKey == key) return existing
            existing.close()
            engine = null
        }
        val litertBackend = when (backend) {
            LlmBackend.CPU -> Backend.CPU()
            LlmBackend.GPU -> Backend.GPU()
        }
        val created = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = litertBackend,
                visionBackend = litertBackend,
                cacheDir = context.cacheDir.absolutePath,
            ),
        )
        created.initialize()
        engine = created
        engineKey = key
        return created
    }

    /** SPEC §4: one retry with a "return only JSON" reprimand, then a recoverable error. */
    private inline fun <reified T> parseWithRetry(
        conversation: Conversation,
        firstReply: String,
    ): List<T> {
        LlmJson.parseArray<T>(firstReply)?.let { return it }
        val secondReply = conversation.sendMessage(Prompts.JSON_REPRIMAND).toString()
        return LlmJson.parseArray<T>(secondReply)
            ?: throw MalformedModelOutputException(
                "The model returned unparseable output twice. Try again or rescan.",
            )
    }

    private fun PhotoInput.toAbsolutePath(): String = when {
        uri.startsWith("file://") -> Uri.parse(uri).path ?: uri.removePrefix("file://")
        else -> uri
    }

    private companion object {
        // Low temperature for extraction: we want faithful reads, not creativity.
        val EXTRACTION_SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)

        // Higher temperature for idea generation.
        val IDEAS_SAMPLER = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.9)

        val RAW_SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
    }
}
