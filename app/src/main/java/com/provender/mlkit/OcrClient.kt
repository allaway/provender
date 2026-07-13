package com.provender.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * On-device label OCR (SPEC §2 Tier 1). The recognized text is auxiliary context for the
 * VLM extraction prompt — it never gates the flow, so failures degrade to empty text.
 */
interface OcrClient {
    suspend fun recognizeText(photoPath: String): String
}

@Singleton
class MlKitOcrClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrClient {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(photoPath: String): String = runCatching {
        val image = InputImage.fromFilePath(context, Uri.fromFile(File(photoPath)))
        recognizer.process(image).await().text
    }.getOrDefault("")
}
