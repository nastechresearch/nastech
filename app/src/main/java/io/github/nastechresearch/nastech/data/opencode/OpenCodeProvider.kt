package io.github.nastechresearch.nastech.data.opencode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/**
 * Connects Nastech to a user-operated OpenCode server. OpenCode runs the coding agent on the
 * configured server; the Android app neither bundles nor starts an agent runtime locally.
 */
class OpenCodeProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : Provider<ProviderSetting.OpenCode> {

    override suspend fun listModels(providerSetting: ProviderSetting.OpenCode): List<Model> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkHealth(providerSetting)
                val payload = execute(providerSetting, Request.Builder()
                    .url("${providerSetting.normalizedServerUrl()}/api/models")
                    .get()
                    .build())
                parseModels(payload)
            }.getOrElse { emptyList() }
                .ifEmpty { providerSetting.models }
                .ifEmpty { listOf(DEFAULT_MODEL) }
        }

    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenCode,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult = withContext(Dispatchers.IO) {
        val prompt = messages.toOpenCodePrompt()
        val sessionId = ensureSession(providerSetting)
        val previousAssistantText = fetchLatestAssistantText(providerSetting, sessionId)
        submitPrompt(providerSetting, sessionId, prompt)
        val responseText = awaitAssistantText(providerSetting, sessionId, previousAssistantText)
        TextGenerationResult(
            id = sessionId,
            model = params.model.modelId.ifBlank { DEFAULT_MODEL.modelId },
            message = UIMessage.assistant(responseText),
            finishReason = "stop",
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenCode,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = flow {
        val response = generateText(providerSetting, messages, params)
        val id = "opencode-${UUID.randomUUID()}"
        emit(StreamChunk.TextStart(id))
        emit(StreamChunk.TextDelta(id, response.message.toText()))
        emit(StreamChunk.TextEnd(id))
        emit(StreamChunk.Finish(response.finishReason, response.id, response.model))
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> {
        error("Image generation is not supported by the OpenCode provider")
    }

    private fun checkHealth(setting: ProviderSetting.OpenCode) {
        execute(setting, Request.Builder()
            .url("${setting.normalizedServerUrl()}/api/health")
            .get()
            .build())
    }

    private fun ensureSession(setting: ProviderSetting.OpenCode): String {
        if (setting.sessionId.isNotBlank()) return setting.sessionId.trim()
        val body = "{}".toRequestBody(JSON)
        val response = execute(setting, Request.Builder()
            .url("${setting.normalizedServerUrl()}/api/session")
            .post(body)
            .build())
        return response.sessionId()
            ?: error("OpenCode did not return a session ID when creating a session")
    }

    private fun submitPrompt(setting: ProviderSetting.OpenCode, sessionId: String, prompt: String) {
        val body = JSONObject()
            .put("text", prompt)
            .toString()
            .toRequestBody(JSON)
        execute(setting, Request.Builder()
            .url("${setting.normalizedServerUrl()}/api/session/$sessionId/prompt")
            .post(body)
            .build())
    }

    private suspend fun awaitAssistantText(
        setting: ProviderSetting.OpenCode,
        sessionId: String,
        previousAssistantText: String?,
    ): String {
        var latest = previousAssistantText.orEmpty()
        var stablePolls = 0
        repeat(MAX_COMPLETION_POLLS) {
            delay(POLL_INTERVAL_MILLIS)
            val snapshot = withContext(Dispatchers.IO) {
                fetchLatestAssistantSnapshot(setting, sessionId)
            }
            val text = snapshot?.text.orEmpty()
            if (text.isBlank() || text == previousAssistantText) return@repeat
            stablePolls = if (text == latest) stablePolls + 1 else 0
            latest = text
            if (snapshot.finished || stablePolls >= STABLE_TEXT_POLLS) return latest
        }
        if (latest.isNotBlank() && latest != previousAssistantText) return latest
        error("Timed out waiting for OpenCode to complete the session response")
    }

    private fun fetchLatestAssistantText(setting: ProviderSetting.OpenCode, sessionId: String): String? =
        fetchLatestAssistantSnapshot(setting, sessionId)?.text

    private fun fetchLatestAssistantSnapshot(
        setting: ProviderSetting.OpenCode,
        sessionId: String,
    ): AssistantSnapshot? {
        val payload = execute(setting, Request.Builder()
            .url("${setting.normalizedServerUrl()}/api/session/$sessionId/message")
            .get()
            .build())
        return payload.messageObjects().asReversed().firstNotNullOfOrNull { message ->
            if (message.messageRole() != "assistant") return@firstNotNullOfOrNull null
            message.messageText()?.takeIf(String::isNotBlank)?.let { text ->
                AssistantSnapshot(text = text, finished = message.isFinished())
            }
        }
    }

    private fun execute(setting: ProviderSetting.OpenCode, request: Request): JsonElement {
        val authenticated = request.newBuilder()
            .apply {
                if (setting.username.isNotBlank() || setting.password.isNotBlank()) {
                    header("Authorization", Credentials.basic(setting.username, setting.password))
                }
            }
            .build()
        return client.newCall(authenticated).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("OpenCode request failed: ${response.code} ${response.message} ${body.take(512)}")
            }
            if (body.isBlank()) return@use JsonObject(emptyMap())
            json.parseToJsonElement(body)
        }
    }

    private fun parseModels(payload: JsonElement): List<Model> = payload.modelObjects()
        .mapNotNull { item ->
            val id = item.string("id")
                ?: item.string("modelID")
                ?: item.string("modelId")
                ?: item.string("name")
                ?: return@mapNotNull null
            Model(
                modelId = id,
                displayName = item.string("name") ?: id,
                inputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL),
            )
        }
        .distinctBy(Model::modelId)

    private fun JsonElement.modelObjects(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOfNotNull(
            this["data"] as? JsonArray,
            this["models"] as? JsonArray,
        ).firstOrNull()?.mapNotNull { it as? JsonObject } ?: emptyList()
        else -> emptyList()
    }

    private fun JsonElement.messageObjects(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOfNotNull(
            this["data"] as? JsonArray,
            this["messages"] as? JsonArray,
        ).firstOrNull()?.mapNotNull { it as? JsonObject } ?: emptyList()
        else -> emptyList()
    }

    private fun JsonElement.sessionId(): String? = when (this) {
        is JsonObject -> string("id")
            ?: (this["data"] as? JsonObject)?.string("id")
            ?: string("sessionID")
        else -> null
    }

    private fun JsonObject.messageRole(): String? =
        (this["info"] as? JsonObject)?.string("role") ?: string("role")

    private fun JsonObject.messageText(): String? {
        val parts = (this["parts"] as? JsonArray)
            ?: (this["content"] as? JsonArray)
            ?: return string("text")
        return parts.mapNotNull { part ->
            (part as? JsonObject)?.let { objectPart ->
                objectPart.string("text") ?: objectPart.string("content")
            }
        }.joinToString(separator = "\n").takeIf(String::isNotBlank)
    }

    private fun JsonObject.isFinished(): Boolean {
        val status = string("status") ?: (this["info"] as? JsonObject)?.string("status")
        if (status in setOf("completed", "complete", "finished", "done")) return true
        val time = (this["info"] as? JsonObject)?.get("time") as? JsonObject
        return time?.containsKey("completed") == true || time?.containsKey("completedAt") == true
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun ProviderSetting.OpenCode.normalizedServerUrl(): String = serverUrl.trim().trimEnd('/')
        .ifBlank { error("OpenCode server URL is required") }

    private fun List<UIMessage>.toOpenCodePrompt(): String {
        val nonEmptyMessages = filter { it.toText().isNotBlank() }
        require(nonEmptyMessages.isNotEmpty()) { "OpenCode requires at least one non-empty text message" }
        return nonEmptyMessages.joinToString(separator = "\n\n") { message ->
            val role = when (message.role) {
                MessageRole.SYSTEM -> "System"
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.TOOL -> "Tool"
            }
            "$role:\n${message.toText()}"
        }
    }

    private data class AssistantSnapshot(
        val text: String,
        val finished: Boolean,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val DEFAULT_MODEL = Model(
            modelId = "opencode",
            displayName = "OpenCode server default",
            inputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.TOOL),
        )
        const val POLL_INTERVAL_MILLIS = 750L
        const val MAX_COMPLETION_POLLS = 160
        const val STABLE_TEXT_POLLS = 3
    }
}
