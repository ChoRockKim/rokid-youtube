package com.chorockkim.rokidyoutube.auth

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google sign-in with the official OAuth 2.0 device flow ("TVs and Limited Input devices"):
 * https://developers.google.com/identity/protocols/oauth2/limited-input-device
 *
 * The glasses show a short code, the user enters it at google.com/device on their phone and
 * approves read-only YouTube access. Uses the user's own OAuth client (see README), not any
 * YouTube app's credentials.
 */
class GoogleAuth(
    private val clientId: String,
    private val clientSecret: String,
    private val store: TokenStore,
    /** Created on first use, so screens can be built (and snapshotted) without a network stack. */
    private val httpClient: () -> OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val http by lazy(httpClient)

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresAtMs: Long,
        val intervalSeconds: Int
    )

    data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtMs: Long)

    interface TokenStore {
        fun load(): Tokens?
        fun save(tokens: Tokens?)
    }

    sealed interface PollResult {
        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Denied : PollResult
        data object Expired : PollResult
        data object SignedIn : PollResult
    }

    class AuthException(message: String) : IOException(message)

    val isConfigured get() = clientId.isNotBlank() && clientSecret.isNotBlank()
    val isSignedIn get() = store.load() != null

    suspend fun requestDeviceCode(): DeviceCode = withContext(Dispatchers.IO) {
        val json = post(
            DEVICE_CODE_URL,
            mapOf("client_id" to clientId, "scope" to SCOPE)
        )
        parseDeviceCode(json, now())
    }

    /** One poll of the token endpoint; call again after [DeviceCode.intervalSeconds]. */
    suspend fun poll(code: DeviceCode): PollResult = withContext(Dispatchers.IO) {
        val json = post(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "device_code" to code.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ),
            allowErrors = true
        )
        when (json.getString("error", null)) {
            null -> {
                store.save(parseTokens(json, now(), previousRefresh = null))
                PollResult.SignedIn
            }
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "access_denied" -> PollResult.Denied
            "expired_token" -> PollResult.Expired
            else -> throw AuthException(json.getString("error_description", json.getString("error")))
        }
    }

    /** Polls until the user approves, denies or the code expires. */
    suspend fun waitForApproval(code: DeviceCode): PollResult {
        var interval = code.intervalSeconds.coerceAtLeast(5)
        while (now() < code.expiresAtMs) {
            delay(interval * 1000L)
            when (val result = poll(code)) {
                PollResult.Pending -> Unit
                PollResult.SlowDown -> interval += 5
                else -> return result
            }
        }
        return PollResult.Expired
    }

    /** @return a valid access token, refreshing it if needed, or null when signed out. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val tokens = store.load() ?: return@withContext null
        if (now() < tokens.expiresAtMs - 60_000) return@withContext tokens.accessToken
        val json = post(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "refresh_token" to tokens.refreshToken,
                "grant_type" to "refresh_token"
            ),
            allowErrors = true
        )
        if (json.getString("error", null) == "invalid_grant") {
            // Revoked or expired: Google ended the link, the user has to sign in again.
            store.save(null)
            return@withContext null
        }
        if (json.has("error")) throw AuthException(json.getString("error"))
        parseTokens(json, now(), tokens.refreshToken).also(store::save).accessToken
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val tokens = store.load()
        store.save(null)
        if (tokens != null) {
            runCatching {
                http.newCall(
                    Request.Builder().url(REVOKE_URL)
                        .post(FormBody.Builder().add("token", tokens.refreshToken).build())
                        .build()
                ).execute().close()
            }
        }
    }

    private fun post(url: String, form: Map<String, String>, allowErrors: Boolean = false): JsonObject {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        http.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
            val text = response.body.string()
            val json = runCatching { JsonParser.`object`().from(text) }.getOrNull()
                ?: throw AuthException("HTTP ${response.code}")
            if (!response.isSuccessful && !allowErrors) {
                throw AuthException(json.getString("error_description", json.getString("error", "HTTP ${response.code}")))
            }
            return json
        }
    }

    companion object {
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

        fun parseDeviceCode(json: JsonObject, nowMs: Long) = DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.getString("verification_url", json.getString("verification_uri", "https://www.google.com/device")),
            expiresAtMs = nowMs + json.getInt("expires_in", 1800) * 1000L,
            intervalSeconds = json.getInt("interval", 5)
        )

        fun parseTokens(json: JsonObject, nowMs: Long, previousRefresh: String?) = Tokens(
            accessToken = json.getString("access_token"),
            // Refresh responses usually omit refresh_token; keep the one we have.
            refreshToken = json.getString("refresh_token", null) ?: previousRefresh
                ?: throw AuthException("No refresh token"),
            expiresAtMs = nowMs + json.getInt("expires_in", 3600) * 1000L
        )
    }
}
