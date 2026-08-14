package io.github.nastechresearch.nastech.ui.pages.setting.components

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.OpenRouterRouting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.ai.provider.ClaudePromptCacheTtl
import me.rerere.ai.provider.ProviderSetting
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.data.openrouter.OpenRouterOAuthManager
import io.github.nastechresearch.nastech.data.openrouter.OpenRouterOAuthStatus
import io.github.nastechresearch.nastech.data.datastore.DEFAULT_PROVIDERS
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.theme.JetbrainsMono
import io.github.nastechresearch.nastech.ui.theme.glassContentColor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.koinInject
import kotlin.reflect.KClass

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    val formContent = glassContentColor(
        GlassSurface.SETTINGS,
        MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    CompositionLocalProvider(LocalContentColor provides formContent) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
        ) {
            if (!provider.builtIn) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ProviderSetting.Types.forEachIndexed { index, type ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ProviderSetting.Types.size
                            ),
                            label = { Text(type.simpleName ?: "") },
                            selected = provider::class == type,
                            onClick = { onEdit(provider.convertTo(type)) }
                        )
                    }
                }
            }

            when (provider) {
                is ProviderSetting.OpenAI -> {
                    ProviderConfigureOpenAI(provider, onEdit)
                }

                is ProviderSetting.Google -> {
                    ProviderConfigureGoogle(provider, onEdit)
                }

                is ProviderSetting.Claude -> {
                    ProviderConfigureClaude(provider, onEdit)
                }

                is ProviderSetting.Codex -> Unit
                is ProviderSetting.Grok -> Unit
                is ProviderSetting.GeminiOAuth -> Unit
            }
        }
    }
}

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) return this

    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
        is ProviderSetting.Codex -> "" // OAuth, no API key
        is ProviderSetting.Grok -> "" // OAuth, no API key
        is ProviderSetting.GeminiOAuth -> "" // OAuth, no API key
        else -> "" // retired provider record; migration removes it on load
    }
    val sourceBaseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.Codex -> "" // OAuth, no base URL
        is ProviderSetting.Grok -> "" // OAuth, no base URL
        is ProviderSetting.GeminiOAuth -> "" // OAuth, no base URL
        else -> "" // retired provider record; migration removes it on load
    }
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = sourceBaseUrl.convertToTargetBaseUrl(targetDefaultBaseUrl)

    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )


        else -> error("Unsupported provider type: $type")
    }
}

internal fun ProviderSetting.defaultBaseUrlForReset(): String {
    val defaultProvider = DEFAULT_PROVIDERS.find { it.id == id }
    if (defaultProvider != null) {
        when (this) {
            is ProviderSetting.OpenAI -> if (defaultProvider is ProviderSetting.OpenAI) return defaultProvider.baseUrl
            is ProviderSetting.Google -> if (defaultProvider is ProviderSetting.Google) return defaultProvider.baseUrl
            is ProviderSetting.Claude -> if (defaultProvider is ProviderSetting.Claude) return defaultProvider.baseUrl
            is ProviderSetting.Codex -> return "" // OAuth, no base URL
            is ProviderSetting.Grok -> return "" // OAuth, no base URL
            is ProviderSetting.GeminiOAuth -> return "" // OAuth, no base URL
            else -> return "" // retired provider record; migration removes it on load
        }
    }
    return when (this) {
        is ProviderSetting.OpenAI -> ProviderSetting.OpenAI().baseUrl
        is ProviderSetting.Google -> ProviderSetting.Google().baseUrl
        is ProviderSetting.Claude -> ProviderSetting.Claude().baseUrl
        is ProviderSetting.Codex -> ""
        is ProviderSetting.Grok -> ""
        is ProviderSetting.GeminiOAuth -> ""
        else -> "" // retired provider record; migration removes it on load
    }
}

internal fun ProviderSetting.resetBaseUrlToDefault(): ProviderSetting {
    val defaultBaseUrl = defaultBaseUrlForReset()
    return when (this) {
        is ProviderSetting.OpenAI -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Google -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Claude -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Codex -> this // OAuth, no base URL to reset
        is ProviderSetting.Grok -> this // OAuth, no base URL to reset
        is ProviderSetting.GeminiOAuth -> this // OAuth, no base URL to reset
        else -> this // retired provider record; migration removes it on load
    }
}

internal fun ProviderSetting.isUsingDefaultBaseUrl(): Boolean {
    val baseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.Codex -> return true // OAuth, no base URL concept
        is ProviderSetting.Grok -> return true // OAuth, no base URL concept
        is ProviderSetting.GeminiOAuth -> return true // OAuth, no base URL concept
        else -> return true // retired provider record; migration removes it on load
    }
    return baseUrl == defaultBaseUrlForReset()
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) return targetDefaultBaseUrl
    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder().encodedPath(convertedPath).build().toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()
    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }
    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") return ""
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

private fun String.isValidBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"
private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

@Composable
private fun ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val toaster = LocalToaster.current
    val openRouterOAuthManager = koinInject<OpenRouterOAuthManager>()
    val openRouterOAuthStatus by openRouterOAuthManager.status.collectAsStateWithLifecycle()
    val isOpenRouter = provider.baseUrl.toHttpUrlOrNull()?.host == "openrouter.ai"

    LaunchedEffect(openRouterOAuthStatus, provider.id) {
        when (val status = openRouterOAuthStatus) {
            is OpenRouterOAuthStatus.Success -> {
                if (status.providerId == provider.id) {
                    onEdit(provider.copy(apiKey = status.apiKey, enabled = true))
                    toaster.show(
                        "OpenRouter connected. Refresh the model catalogue to choose a model.",
                        type = ToastType.Success,
                    )
                    openRouterOAuthManager.consumeResult()
                }
            }
            is OpenRouterOAuthStatus.Error -> {
                toaster.show(status.message, type = ToastType.Error)
                openRouterOAuthManager.consumeResult()
            }
            else -> Unit
        }
    }

    provider.description()

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (isOpenRouter) {
        OutlinedButton(
            onClick = { openRouterOAuthManager.startLogin(provider.id) },
            modifier = Modifier.fillMaxWidth(),
            enabled = openRouterOAuthStatus !is OpenRouterOAuthStatus.Exchanging,
        ) {
            Text(
                when (openRouterOAuthStatus) {
                    OpenRouterOAuthStatus.Exchanging -> "Connecting OpenRouter…"
                    OpenRouterOAuthStatus.AwaitingBrowser -> "Continue OpenRouter connection in your browser"
                    else -> "Connect with OpenRouter"
                }
            )
        }
        Text(
            "Secure browser sign-in creates a user-owned OpenRouter key with PKCE. You can also paste an existing key below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    var keyVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = provider.apiKey,
        onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { keyVisible = !keyVisible }) {
                Icon(if (keyVisible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = stringResource(if (keyVisible) R.string.accessibility_hide_password else R.string.accessibility_show_password))
            }
        },
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
        modifier = Modifier.fillMaxWidth(),
        isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
    )

    if (!provider.useResponseApi) {
        OutlinedTextField(
            value = provider.chatCompletionsPath,
            onValueChange = { onEdit(provider.copy(chatCompletionsPath = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_api_path)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !provider.builtIn,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
        )
    }

    val responseAPIWarning = stringResource(R.string.setting_provider_page_response_api_warning)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_response_api))
        Switch(
            checked = provider.useResponseApi,
            onCheckedChange = {
                onEdit(provider.copy(useResponseApi = it))
                if (it && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                    toaster.show(message = responseAPIWarning, type = ToastType.Warning)
                }
            }
        )
    }

    // OpenRouter is the only OpenAI-compatible host where this does anything: it gates
    // the cache_control breakpoints required by Anthropic/Gemini/Qwen models routed
    // through it. Other models on OpenRouter cache automatically regardless.
    if (isOpenRouter) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.setting_provider_page_claude_prompt_caching),
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = provider.promptCaching,
                onCheckedChange = {
                    onEdit(provider.copy(promptCaching = it))
                }
            )
        }
        OpenRouterRoutingSection(
            routing = provider.routing,
            onChange = { onEdit(provider.copy(routing = it)) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_include_history_reasoning))
        Switch(
            checked = provider.includeHistoryReasoning,
            onCheckedChange = { onEdit(provider.copy(includeHistoryReasoning = it)) }
        )
    }
}



@Composable
private fun OpenRouterRoutingSection(
    routing: OpenRouterRouting,
    onChange: (OpenRouterRouting) -> Unit,
) {
    fun listToText(list: List<String>) = list.joinToString(", ")
    fun textToList(text: String) = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("OpenRouter routing", style = MaterialTheme.typography.titleSmall)

    // Sort
    val sortOptions = listOf(null, "price", "throughput", "latency")
    val sortLabels = listOf("Auto", "Price", "Throughput", "Latency")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        sortOptions.forEachIndexed { index, option ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sortOptions.size),
                selected = routing.sort == option,
                onClick = { onChange(routing.copy(sort = option)) },
                label = {
                    // Equal-width segments are ~1/4 of the row, so the longest label
                    // ("Throughput") would wrap to two lines and break the pill on
                    // narrow screens. Keep it single-line and shrink to fit instead.
                    Text(
                        sortLabels[index],
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = 14.sp,
                            stepSize = 1.sp,
                        ),
                    )
                },
            )
        }
    }

    OutlinedTextField(
        value = listToText(routing.order),
        onValueChange = { onChange(routing.copy(order = textToList(it))) },
        label = { Text("Provider order (slugs, comma-separated)") },
        placeholder = { Text("anthropic, google-vertex") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listToText(routing.only),
        onValueChange = { onChange(routing.copy(only = textToList(it))) },
        label = { Text("Only these providers") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listToText(routing.ignore),
        onValueChange = { onChange(routing.copy(ignore = textToList(it))) },
        label = { Text("Ignore these providers") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listToText(routing.fallbackModels),
        onValueChange = { onChange(routing.copy(fallbackModels = textToList(it))) },
        label = { Text("Fallback models (ids, comma-separated)") },
        placeholder = { Text("anthropic/claude-sonnet-4.5, deepseek/deepseek-chat") },
        supportingText = { Text("Tried in order when the primary model is down, rate-limited, or refuses") },
        modifier = Modifier.fillMaxWidth(),
    )

    RoutingToggle("Allow fallbacks beyond the list", routing.allowFallbacks) {
        onChange(routing.copy(allowFallbacks = it))
    }
    RoutingToggle("Require providers to support all parameters", routing.requireParameters) {
        onChange(routing.copy(requireParameters = it))
    }
    RoutingToggle("Block data-collecting providers", routing.dataCollection == "deny") {
        onChange(routing.copy(dataCollection = if (it) "deny" else null))
    }
    RoutingToggle("Zero Data Retention only", routing.zdr) {
        onChange(routing.copy(zdr = it))
    }

    Text(
        "Max price (USD per 1M tokens). Leave empty or tap the clear icon for no price limit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Local edit buffers so a partial decimal entry ("0.") isn't snapped away while typing:
    // the parsed Double drives routing, the raw text drives the field.
    var promptPriceText by remember { mutableStateOf(routing.maxPricePrompt?.toString() ?: "") }
    var completionPriceText by remember { mutableStateOf(routing.maxPriceCompletion?.toString() ?: "") }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = promptPriceText,
            onValueChange = {
                promptPriceText = it
                onChange(routing.copy(maxPricePrompt = it.toDoubleOrNull()))
            },
            label = { Text("Max $/1M prompt") },
            singleLine = true,
            trailingIcon = {
                if (promptPriceText.isNotEmpty()) {
                    IconButton(onClick = {
                        promptPriceText = ""
                        onChange(routing.copy(maxPricePrompt = null))
                    }) {
                        Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.accessibility_clear_text))
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = completionPriceText,
            onValueChange = {
                completionPriceText = it
                onChange(routing.copy(maxPriceCompletion = it.toDoubleOrNull()))
            },
            label = { Text("Max $/1M completion") },
            singleLine = true,
            trailingIcon = {
                if (completionPriceText.isNotEmpty()) {
                    IconButton(onClick = {
                        completionPriceText = ""
                        onChange(routing.copy(maxPriceCompletion = null))
                    }) {
                        Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.accessibility_clear_text))
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }

    OutlinedTextField(
        value = listToText(routing.quantizations),
        onValueChange = { onChange(routing.copy(quantizations = textToList(it))) },
        label = { Text("Quantizations (e.g. fp8, fp16)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RoutingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    provider.description()

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    var keyVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = provider.apiKey,
        onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { keyVisible = !keyVisible }) {
                Icon(if (keyVisible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = stringResource(if (keyVisible) R.string.accessibility_hide_password else R.string.accessibility_show_password))
            }
        },
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
        modifier = Modifier.fillMaxWidth(),
        isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_claude_prompt_caching))
        Switch(
            checked = provider.promptCaching,
            onCheckedChange = { onEdit(provider.copy(promptCaching = it)) }
        )
    }

    if (provider.promptCaching) {
        Text(stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ClaudePromptCacheTtl.entries.forEachIndexed { index, ttl ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ClaudePromptCacheTtl.entries.size
                    ),
                    label = {
                        Text(
                            when (ttl) {
                                ClaudePromptCacheTtl.FIVE_MINUTES -> stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_5m)
                                ClaudePromptCacheTtl.ONE_HOUR -> stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_1h)
                            }
                        )
                    },
                    selected = provider.promptCacheTtl == ttl,
                    onClick = { onEdit(provider.copy(promptCacheTtl = ttl)) }
                )
            }
        }
    }
}

@Composable
private fun ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val serviceAccountJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return@rememberLauncherForActivityResult
            val json = Json.parseToJsonElement(content).jsonObject
            onEdit(
                provider.copy(
                    projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.projectId,
                    serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.serviceAccountEmail,
                    privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.privateKey,
                )
            )
            toaster.show("Service account imported", type = ToastType.Success)
        } catch (e: Exception) {
            toaster.show("Failed to import: ${e.message}", type = ToastType.Error)
        }
    }

    provider.description()

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (!(provider.vertexAI && provider.useServiceAccount)) {
        var keyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = provider.apiKey,
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(if (keyVisible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = stringResource(if (keyVisible) R.string.accessibility_hide_password else R.string.accessibility_show_password))
                }
            },
        )
    }

    if (!provider.vertexAI) {
        OutlinedTextField(
            value = provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
            modifier = Modifier.fillMaxWidth(),
            isError = provider.baseUrl.isNotBlank() && (
                !provider.baseUrl.isValidBaseUrl() || !provider.baseUrl.endsWith("/v1beta")
                ),
            supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                { Text("The base URL usually ends with `/v1beta`") }
            } else null,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_vertex_ai))
        Switch(
            checked = provider.vertexAI,
            onCheckedChange = { onEdit(provider.copy(vertexAI = it)) }
        )
    }

    if (provider.vertexAI) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.setting_provider_page_use_service_account))
            Switch(
                checked = provider.useServiceAccount,
                onCheckedChange = { onEdit(provider.copy(useServiceAccount = it)) }
            )
        }
    }

    if (provider.vertexAI && provider.useServiceAccount) {
        OutlinedButton(
            onClick = { serviceAccountJsonLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setting_provider_page_import_service_account_json))
        }

        OutlinedTextField(
            value = provider.serviceAccountEmail,
            onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_service_account_email)) },
            modifier = Modifier.fillMaxWidth(),
        )

        var privateKeyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = provider.privateKey,
            onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_private_key)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6,
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
            visualTransformation = if (privateKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { privateKeyVisible = !privateKeyVisible }) {
                    Icon(if (privateKeyVisible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = stringResource(if (privateKeyVisible) R.string.accessibility_hide_password else R.string.accessibility_show_password))
                }
            },
        )

        OutlinedTextField(
            value = provider.location,
            onValueChange = { onEdit(provider.copy(location = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_location)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = provider.projectId,
            onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_project_id)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
