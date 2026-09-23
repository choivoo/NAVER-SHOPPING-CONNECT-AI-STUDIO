package com.shoppingconnect.aistudio.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.shoppingconnect.aistudio.BuildConfig
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.core.network.await
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.core.security.SecretStore
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface NaverAuthState {
    data object Disconnected : NaverAuthState
    data object Connecting : NaverAuthState
    data class Connected(val nickname: String) : NaverAuthState
    data object Expired : NaverAuthState
    data class Failed(val message: String) : NaverAuthState
}

/**
 * Official NAVER Login (네이버 로그인, OAuth 2.0 authorization-code flow).
 *
 *  App → Custom Tab (nid.naver.com/oauth2.0/authorize) → user logs in on NAVER's own page
 *      → redirect with code → token exchange → access token in Android Keystore.
 *
 * The app never sees the NAVER password. The client secret is never compiled into the APK:
 * the exchange runs on a backend (NAVER_TOKEN_EXCHANGE_URL, recommended) or — developer mode
 * only — with a secret the user typed into Settings, stored encrypted by Keystore.
 */
@Singleton
class NaverAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("api") private val http: OkHttpClient,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<NaverAuthState>(NaverAuthState.Disconnected)
    val state: StateFlow<NaverAuthState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("naver_oauth", Context.MODE_PRIVATE)

    data class Config(val clientId: String, val redirectUri: String, val exchangeUrl: String)

    fun config(s: AppSettings) = Config(
        clientId = s.naverClientIdOverride.ifBlank { BuildConfig.NAVER_CLIENT_ID },
        redirectUri = s.naverRedirectUriOverride.ifBlank { BuildConfig.NAVER_REDIRECT_URI },
        exchangeUrl = s.naverTokenExchangeUrlOverride.ifBlank { BuildConfig.NAVER_TOKEN_EXCHANGE_URL },
    )

    suspend fun restore() {
        val s = settings.current()
        _state.value = if (secrets.has(SecretKeyName.NAVER_ACCESS_TOKEN)) NaverAuthState.Connected(s.naverNickname.ifBlank { "NAVER" }) else NaverAuthState.Disconnected
    }

    suspend fun canConnect(): String? {
        val c = config(settings.current())
        if (c.clientId.isBlank()) return "NAVER Client ID가 설정되지 않았습니다. 설정 → NAVER에서 입력하거나 local.properties에 NAVER_CLIENT_ID를 지정하세요."
        if (c.exchangeUrl.isBlank() && !secrets.has(SecretKeyName.NAVER_CLIENT_SECRET)) {
            return "토큰 교환 서버(NAVER_TOKEN_EXCHANGE_URL)가 없습니다. 백엔드를 설정하거나 개발자 모드에서 Client Secret을 입력하세요."
        }
        return null
    }

    /** Opens the official NAVER authorization page in a Custom Tab. */
    suspend fun startLogin(activityContext: Context) {
        canConnect()?.let { throw AppException(ErrorKind.NotConfigured, it) }
        val c = config(settings.current())
        val state = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_STATE, state).putLong(KEY_STATE_AT, System.currentTimeMillis()).apply()
        val url = AUTHORIZE.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", c.clientId)
            .addQueryParameter("redirect_uri", c.redirectUri)
            .addQueryParameter("state", state)
            .build()
        _state.value = NaverAuthState.Connecting
        val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(activityContext, Uri.parse(url.toString()))
    }

    fun isCallback(uri: Uri?): Boolean = uri?.scheme == "aistudio" && uri.host == "oauth" && uri.path?.startsWith("/naver") == true

    /** Handles aistudio://oauth/naver?code=…&state=… (or ?error=…). */
    suspend fun handleCallback(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (error != null) {
            _state.value = if (error == "access_denied") NaverAuthState.Disconnected else NaverAuthState.Failed("네이버 로그인이 취소되었거나 실패했습니다.")
            return
        }
        val expected = prefs.getString(KEY_STATE, null)
        val issued = prefs.getLong(KEY_STATE_AT, 0)
        val state = uri.getQueryParameter("state")
        val code = uri.getQueryParameter("code")
        prefs.edit().remove(KEY_STATE).apply()
        if (expected == null || state != expected || System.currentTimeMillis() - issued > 10 * 60_000) {
            _state.value = NaverAuthState.Failed("로그인 요청이 유효하지 않습니다. 다시 시도해 주세요.")
            return
        }
        if (code.isNullOrBlank()) { _state.value = NaverAuthState.Failed("인증 코드가 없습니다."); return }
        try {
            val tokens = exchange(mapOf("grant_type" to "authorization_code", "code" to code, "state" to state))
            store(tokens)
            val nickname = fetchNickname(tokens.access_token)
            settings.update { it.copy(naverNickname = nickname, naverConnectedAt = System.currentTimeMillis()) }
            _state.value = NaverAuthState.Connected(nickname)
            AppLog.i(TAG, "NAVER connected")
        } catch (e: AppException) {
            _state.value = NaverAuthState.Failed(e.userMessage)
        } catch (e: Exception) {
            AppLog.e(TAG, "token exchange failed", e)
            _state.value = NaverAuthState.Failed("네이버 토큰 교환에 실패했습니다.")
        }
    }

    @Serializable
    data class TokenResponse(
        val access_token: String = "", val refresh_token: String = "", val token_type: String = "", val expires_in: String = "",
        val error: String? = null, val error_description: String? = null,
    )

    private suspend fun exchange(params: Map<String, String>): TokenResponse {
        val c = config(settings.current())
        val req = if (c.exchangeUrl.isNotBlank()) {
            val url = c.exchangeUrl.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: throw AppException(ErrorKind.NotConfigured, "토큰 교환 서버 주소는 https여야 합니다.")
            val form = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) }; add("redirect_uri", c.redirectUri) }.build()
            Request.Builder().url(url).post(form).build()
        } else {
            val secret = secrets.get(SecretKeyName.NAVER_CLIENT_SECRET) ?: throw AppException(ErrorKind.NotConfigured, "Client Secret이 없습니다.")
            val url = TOKEN.toHttpUrl().newBuilder().addQueryParameter("client_id", c.clientId).addQueryParameter("client_secret", secret)
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            Request.Builder().url(url).post(FormBody.Builder().build()).build()
        }
        http.newCall(req).await().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AppException(ErrorKind.AuthExpired, "HTTP ${resp.code}")
            val t = AppJson.decodeFromString(TokenResponse.serializer(), body)
            if (t.error != null) throw AppException(ErrorKind.AuthExpired, t.error_description)
            return t
        }
    }

    private fun store(t: TokenResponse) {
        if (t.access_token.isNotBlank()) secrets.put(SecretKeyName.NAVER_ACCESS_TOKEN, t.access_token)
        if (t.refresh_token.isNotBlank()) secrets.put(SecretKeyName.NAVER_REFRESH_TOKEN, t.refresh_token)
    }

    private suspend fun fetchNickname(accessToken: String): String {
        val req = Request.Builder().url(PROFILE).header("Authorization", "Bearer $accessToken").build()
        return runCatching {
            http.newCall(req).await().use { resp ->
                if (resp.code == 401) throw AppException(ErrorKind.AuthExpired)
                val obj = AppJson.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                // Data minimisation: only the display nickname is kept.
                obj["response"]?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: "NAVER"
            }
        }.getOrDefault("NAVER")
    }

    /** Verifies the token; refreshes it once when it has expired. */
    suspend fun ensureValid(): Boolean {
        val token = secrets.get(SecretKeyName.NAVER_ACCESS_TOKEN) ?: return false
        val req = Request.Builder().url(PROFILE).header("Authorization", "Bearer $token").build()
        val ok = runCatching { http.newCall(req).await().use { it.code != 401 } }.getOrDefault(true)
        if (ok) return true
        val refresh = secrets.get(SecretKeyName.NAVER_REFRESH_TOKEN)
        if (refresh == null) { _state.value = NaverAuthState.Expired; return false }
        return runCatching {
            store(exchange(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh)))
            true
        }.getOrElse { _state.value = NaverAuthState.Expired; false }
    }

    /** Revokes the token at NAVER (when possible) and deletes it locally. */
    suspend fun disconnect() {
        val token = secrets.get(SecretKeyName.NAVER_ACCESS_TOKEN)
        if (token != null) runCatching { exchange(mapOf("grant_type" to "delete", "access_token" to token, "service_provider" to "NAVER")) }
            .onFailure { AppLog.w(TAG, "remote revoke failed; local token removed anyway", it) }
        secrets.put(SecretKeyName.NAVER_ACCESS_TOKEN, null)
        secrets.put(SecretKeyName.NAVER_REFRESH_TOKEN, null)
        settings.update { it.copy(naverNickname = "", naverConnectedAt = 0) }
        _state.value = NaverAuthState.Disconnected
    }

    fun cancelPending() { if (_state.value == NaverAuthState.Connecting) _state.value = NaverAuthState.Disconnected }

    private companion object {
        const val TAG = "NaverAuth"
        const val AUTHORIZE = "https://nid.naver.com/oauth2.0/authorize"
        const val TOKEN = "https://nid.naver.com/oauth2.0/token"
        const val PROFILE = "https://openapi.naver.com/v1/nid/me"
        const val KEY_STATE = "state"
        const val KEY_STATE_AT = "state_at"
    }
}
