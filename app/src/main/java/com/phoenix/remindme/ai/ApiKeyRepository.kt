@file:Suppress("DEPRECATION")

package com.phoenix.remindme.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val securePreferences: SharedPreferences by lazy {
        runCatching { createSecurePreferences(context) }
            .getOrElse {
                context.deleteSharedPreferences(SECURE_FILE)
                createSecurePreferences(context)
            }
    }
    private val onboardingPreferences =
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun getKey(): String? = runCatching {
        securePreferences.getString(KEY_API_KEY, null)
    }.getOrNull()

    fun hasKey(): Boolean = !getKey().isNullOrBlank()

    fun saveKey(key: String) {
        securePreferences.edit().putString(KEY_API_KEY, key.trim()).apply()
        onboardingPreferences.edit()
            .putBoolean(KEY_COMPLETE, true)
            .putBoolean(KEY_SKIPPED, false)
            .apply()
    }

    fun clearKey() {
        securePreferences.edit().remove(KEY_API_KEY).apply()
        onboardingPreferences.edit().putBoolean(KEY_COMPLETE, false).apply()
    }

    fun skipOnboarding() {
        onboardingPreferences.edit().putBoolean(KEY_SKIPPED, true).apply()
    }

    fun shouldShowOnboarding(): Boolean =
        !hasKey() && !onboardingPreferences.getBoolean(KEY_SKIPPED, false)

    companion object {
        fun isValidFormat(key: String): Boolean =
            key.trim().startsWith("sk-ant-") && key.trim().length > 20

        private const val KEY_API_KEY = "claude_api_key"
        private const val KEY_COMPLETE = "onboarding_complete"
        private const val KEY_SKIPPED = "onboarding_skipped"
        private const val SECURE_FILE = "remindme_secure"

        private fun createSecurePreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                SECURE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
