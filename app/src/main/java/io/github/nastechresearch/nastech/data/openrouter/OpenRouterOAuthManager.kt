package io.github.nastechresearch.nastech.data.openrouter

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.uuid.Uuid

private const val AUTH_URL = "https://openrouter.ai/auth"
private const val EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
private const val REDIRECT_URI = "nastech://openrouter/oauth"
private const val PREFS_NAME = "openrouter_pkce"
private const val KEY_VERIFIER = "code_verifier"
private const val KEY_PROVIDER_ID = "provider_id"
private const val KEY_CREATED_AT = "created_at"
private const val MAX_AUTH_AGE_MILLIS = 10 * 60 * 1_000L

sealed interface OpenRouterOAuthStatus {
    data object Idle : OpenRouterOAuthStatus
    data object AwaitingBrowser : OpenRouterOAuthStatus
    data object Exchanging : OpenRouterOAuthStatus
    data class Success(val providerId: Uuid, val apiKey: String) : OpenRouterOAuthStatus
    data class Error(val message: String) : OpenRouterOAuthStatus
}

/**
 * OpenRouter's official PKCE connection flow for a user-owned API key.
 *
 * The verifier is intentionally temporary: it is cleared after a successful or failed
 * exchange and is rejected when the authorization window is more than ten minutes old.
 */
class OpenRouterOAuthManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<OpenRouterOAuthStatus>(OpenRouterOAuthStatus.Idle)
    val status: StateFlow<OpenRouterOAuthStatus> = _status.asStateFlow()

    fun startLogin(providerId: Uuid) {
        val verifier = generateVerifier()
        preferences().edit()
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_PROVIDER_ID, providerId.toString())
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        _status.value = OpenRouterOAuthStatus.AwaitingBrowser

        val url = "$AUTH_URL?callback_url=${REDIRECT_URI.encodeQueryValue()}" +
            "&code_challenge=${sha256UrlSafe(verifier)}&code_challenge_method=S256"
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .also { it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(context, url.toUri())
    }

    fun handleRedirect(code: String?) {
        if (code.isNullOrBlank()) {
            _status.value = OpenRouterOAuthStatus.Error("OpenRouter did not return an authorization code")
            clearPendingAuthorization()
            return
        }
        val prefs = preferences()
        val verifier = prefs.getString(KEY_VERIFIER, null)
        val providerId = prefs.getString(KEY_PROVIDER_ID, null)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        if (verifier.isNullOrBlank() || providerId == null || System.currentTimeMillis() - createdAt > MAX_AUTH_AGE_MILLIS) {
            clearPendingAuthorization()
            _status.value = OpenRouterOAuthStatus.Error("This OpenRouter connection expired. Please start again.")
            return
        }

        _status.value = OpenRouterOAuthStatus.Exchanging
        scope.launch {
            runCatching {
                val body = JSONObject()
                    .put("code", code)
                    .put("code_verifier", verifier)
                    .put("code_challenge_method", "S256")
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(EXCHANGE_URL)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()
                val key = httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("OpenRouter key exchange failed: HTTP ${response.code}")
                    }
                    JSONObject(responseBody).optString("key").trim().also {
                        require(it.isNotEmpty()) { "OpenRouter returned no API key" }
                    }
                }
                OpenRouterOAuthStatus.Success(providerId, key)
            }.onSuccess { _status.value = it }
                .onFailure { error ->
                    _status.value = OpenRouterOAuthStatus.Error(error.message ?: "OpenRouter connection failed")
                }
            clearPendingAuthorization()
        }
    }

    fun consumeResult() {
        _status.value = OpenRouterOAuthStatus.Idle
    }

    private fun preferences() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun clearPendingAuthorization() {
        preferences().edit().clear().apply()
    }

    private fun generateVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256UrlSafe(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII)))

    private fun String.encodeQueryValue(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
