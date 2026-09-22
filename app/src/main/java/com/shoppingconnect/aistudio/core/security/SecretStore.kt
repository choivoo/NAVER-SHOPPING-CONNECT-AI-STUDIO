package com.shoppingconnect.aistudio.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.shoppingconnect.aistudio.core.common.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Keys of every secret the app may hold. Values are never logged. */
enum class SecretKeyName { CLAUDE_API_KEY, AI_PROXY_TOKEN, NAVER_ACCESS_TOKEN, NAVER_REFRESH_TOKEN, NAVER_CLIENT_SECRET, NAVER_SEARCH_CLIENT_ID, NAVER_SEARCH_CLIENT_SECRET }

interface SecretStore {
    fun get(key: SecretKeyName): String?
    fun put(key: SecretKeyName, value: String?)
    fun has(key: SecretKeyName): Boolean = !get(key).isNullOrBlank()
    fun clearAll()
    val changes: StateFlow<Int>
}

/**
 * AES-256-GCM encryption with a non-exportable key held by Android Keystore.
 * Ciphertext (IV + data) is stored in a private SharedPreferences file that is excluded from backup.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun get(key: SecretKeyName): String? {
        val stored = prefs.getString(key.name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_LEN)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLog.w(TAG, "secret ${key.name} could not be decrypted; discarding", e)
            prefs.edit().remove(key.name).apply()
            null
        }
    }

    override fun put(key: SecretKeyName, value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit().remove(key.name).apply()
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val enc = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
            val out = cipher.iv + enc
            prefs.edit().putString(key.name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        }
        _changes.value++
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
        _changes.value++
    }

    private companion object {
        const val TAG = "SecretStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "aistudio_secret_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

/** In-memory implementation used by unit tests. */
class InMemorySecretStore : SecretStore {
    private val map = mutableMapOf<SecretKeyName, String>()
    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()
    override fun get(key: SecretKeyName) = map[key]
    override fun put(key: SecretKeyName, value: String?) {
        if (value.isNullOrBlank()) map.remove(key) else map[key] = value
        _changes.value++
    }
    override fun clearAll() { map.clear(); _changes.value++ }
}
