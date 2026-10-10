package com.specsnap.data.repository

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
import com.specsnap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends a captured component image to Gemini and returns its schema-constrained JSON response. */
class GeminiRepository(
    apiKey: String = BuildConfig.GEMINI_API_KEY,
) : AutoCloseable {

    private val client: Client

    init {
        require(apiKey.isNotBlank()) {
            "GEMINI_API_KEY is missing. Add it to the root local.properties file."
        }
        client = Client.builder().apiKey(apiKey).build()
    }

    /**
     * Analyzes an already-cropped component image. Image capture and cropping are handled by
     * later pipeline steps; this repository owns only the Gemini request.
     */
    suspend fun analyzeComponent(
        imageBytes: ByteArray,
        mimeType: String = JPEG_MIME_TYPE,
    ): String = withContext(Dispatchers.IO) {
        require(imageBytes.isNotEmpty()) { "The component image is empty." }
        require(mimeType == JPEG_MIME_TYPE || mimeType == PNG_MIME_TYPE) {
            "Unsupported image type. Provide a JPEG or PNG image."
        }

        val request = Content.fromParts(
            Part.fromText(ANALYSIS_PROMPT),
            Part.fromBytes(imageBytes, mimeType),
        )
        val response = client.models.generateContent(
            MODEL_NAME,
            request,
            GENERATION_CONFIG,
        )
        response.checkFinishReason()
        response.text()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("Gemini returned an empty component analysis.")
    }

    override fun close() {
        client.close()
    }

    private companion object {
        const val MODEL_NAME = "gemini-3.6-flash"
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val PNG_MIME_TYPE = "image/png"

        val GENERATION_CONFIG: GenerateContentConfig = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(componentResponseSchema())
            .build()

        val ANALYSIS_PROMPT = """
            Identify the electronic component shown in the image and extract only information
            supported by visible markings or reliable component identification. Read package
            markings carefully. Do not invent a part number, manufacturer, pinout, or rating.
            If a field cannot be determined, use the string "Unknown". For pinouts, include only
            pins and functions that can be identified with confidence; use an empty array if none
            can be determined. Include safetyAlerts as an empty array when no relevant warning is
            supported by the image. Return only JSON matching the supplied response schema.
        """.trimIndent()

        fun componentResponseSchema(): Schema = objectSchema(
            properties = mapOf(
                "partNumber" to stringSchema("Identified part number, or Unknown."),
                "manufacturer" to stringSchema("Identified manufacturer, or Unknown."),
                "category" to stringSchema("Component category, or Unknown."),
                "description" to stringSchema("Brief component description, or Unknown."),
                "pinouts" to Schema.builder()
                    .type(Type.Known.ARRAY)
                    .items(
                        objectSchema(
                            properties = mapOf(
                                "pinNumber" to Schema.builder()
                                    .type(Type.Known.INTEGER)
                                    .description("Pin number.")
                                    .build(),
                                "pinName" to stringSchema("Pin name, or Unknown."),
                                "function" to stringSchema("Pin function, or Unknown."),
                            ),
                            required = listOf("pinNumber", "pinName", "function"),
                        ),
                    )
                    .description("Identified pin assignments; empty when unknown.")
                    .build(),
                "electricalRatings" to objectSchema(
                    properties = mapOf(
                        "minVoltage" to stringSchema("Minimum voltage, including units, or Unknown."),
                        "maxVoltage" to stringSchema("Maximum voltage, including units, or Unknown."),
                        "maxCurrent" to stringSchema("Maximum current, including units, or Unknown."),
                        "operatingTempRange" to stringSchema(
                            "Operating temperature range, including units, or Unknown.",
                        ),
                    ),
                    required = listOf("minVoltage", "maxVoltage", "maxCurrent", "operatingTempRange"),
                ),
                "safetyAlerts" to Schema.builder()
                    .type(Type.Known.ARRAY)
                    .items(stringSchema("Safety warning supported by the image."))
                    .description("Relevant safety warnings, or an empty array.")
                    .build(),
            ),
            required = listOf(
                "partNumber",
                "manufacturer",
                "category",
                "description",
                "pinouts",
                "electricalRatings",
                "safetyAlerts",
            ),
        )

        fun objectSchema(
            properties: Map<String, Schema>,
            required: List<String>,
        ): Schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(properties)
            .required(*required.toTypedArray())
            .build()

        fun stringSchema(description: String): Schema = Schema.builder()
            .type(Type.Known.STRING)
            .description(description)
            .build()
    }
}
