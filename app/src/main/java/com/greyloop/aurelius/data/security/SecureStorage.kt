package com.greyloop.aurelius.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure API key storage using EncryptedSharedPreferences.
 * NEVER store API keys in plain SharedPreferences.
 */
class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var minimaxApiKey: String
        get() = securePrefs.getString(KEY_MINIMAX_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_MINIMAX_API_KEY, value).apply()

    var codingPlanKey: String
        get() = securePrefs.getString(KEY_CODING_PLAN_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_CODING_PLAN_KEY, value).apply()

    var region: String
        get() = securePrefs.getString(KEY_REGION, REGION_GLOBAL) ?: REGION_GLOBAL
        set(value) = securePrefs.edit().putString(KEY_REGION, value).apply()

    var planType: String
        get() = securePrefs.getString(KEY_PLAN_TYPE, PLAN_STANDARD) ?: PLAN_STANDARD
        set(value) = securePrefs.edit().putString(KEY_PLAN_TYPE, value).apply()

    var themeMode: String
        get() = securePrefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = securePrefs.edit().putString(KEY_THEME_MODE, value).apply()

    var setupComplete: Boolean
        get() = securePrefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = securePrefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    fun clearAll() {
        securePrefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "aurelius_secure_prefs"
        private const val KEY_MINIMAX_API_KEY = "minimax_api_key"
        private const val KEY_CODING_PLAN_KEY = "coding_plan_key"
        private const val KEY_REGION = "region"
        private const val KEY_PLAN_TYPE = "plan_type"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_THEME_MODE = "theme_mode"

        const val REGION_GLOBAL = "global"
        const val REGION_CHINA = "china"
        const val PLAN_STANDARD = "standard"
        const val PLAN_CODING_PLAN_PLUS = "coding_plan_plus"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
