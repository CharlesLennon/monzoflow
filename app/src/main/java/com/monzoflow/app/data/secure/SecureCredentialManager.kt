package com.monzoflow.app.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import timber.log.Timber

enum class AuthMode {
    PERSONAL_ACCESS_TOKEN,
    OAUTH2
}

class SecureCredentialManager(private val context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs()

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "monzoflow_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.w(e, "EncryptedSharedPreferences creation failed, using fallback prefs")
            context.getSharedPreferences("monzoflow_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    // Auth Mode Selection
    var authMode: AuthMode
        get() = try {
            AuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, AuthMode.PERSONAL_ACCESS_TOKEN.name) ?: AuthMode.PERSONAL_ACCESS_TOKEN.name)
        } catch (e: Exception) {
            AuthMode.PERSONAL_ACCESS_TOKEN
        }
        set(value) = prefs.edit().putString(KEY_AUTH_MODE, value.name).apply()

    // Personal Access Token (PAT)
    var personalAccessToken: String
        get() = prefs.getString(KEY_PAT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PAT, value.trim()).apply()

    // OAuth2 Credentials
    var oauthClientId: String
        get() = prefs.getString(KEY_OAUTH_CLIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OAUTH_CLIENT_ID, value.trim()).apply()

    var oauthClientSecret: String
        get() = prefs.getString(KEY_OAUTH_CLIENT_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OAUTH_CLIENT_SECRET, value.trim()).apply()

    var oauthAccessToken: String
        get() = prefs.getString(KEY_OAUTH_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OAUTH_ACCESS_TOKEN, value.trim()).apply()

    var oauthRefreshToken: String
        get() = prefs.getString(KEY_OAUTH_REFRESH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OAUTH_REFRESH_TOKEN, value.trim()).apply()

    var oauthTokenExpiresAt: Long
        get() = prefs.getLong(KEY_OAUTH_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_OAUTH_EXPIRES_AT, value).apply()

    // Selected Monzo Account ID
    var selectedAccountId: String
        get() = prefs.getString(KEY_SELECTED_ACCOUNT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_ACCOUNT_ID, value.trim()).apply()

    // Webhook Server Port (Default 8080)
    var webhookServerPort: Int
        get() = prefs.getInt(KEY_WEBHOOK_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_WEBHOOK_PORT, value).apply()

    // Active Bearer Token resolver
    val activeBearerToken: String
        get() = when (authMode) {
            AuthMode.PERSONAL_ACCESS_TOKEN -> personalAccessToken
            AuthMode.OAUTH2 -> oauthAccessToken.ifBlank { personalAccessToken }
        }

    val isConfigured: Boolean
        get() = activeBearerToken.isNotBlank()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_PAT = "personal_access_token"
        private const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        private const val KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret"
        private const val KEY_OAUTH_ACCESS_TOKEN = "oauth_access_token"
        private const val KEY_OAUTH_REFRESH_TOKEN = "oauth_refresh_token"
        private const val KEY_OAUTH_EXPIRES_AT = "oauth_expires_at"
        private const val KEY_SELECTED_ACCOUNT_ID = "selected_account_id"
        private const val KEY_WEBHOOK_PORT = "webhook_port"
    }
}
