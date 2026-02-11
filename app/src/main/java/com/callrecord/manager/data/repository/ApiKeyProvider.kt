package com.callrecord.manager.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Singleton provider for StepFun API Key management.
 * Uses SharedPreferences for persistent storage.
 * Initialize via [init] in MainActivity before use.
 */
object ApiKeyProvider {

    private const val PREF_NAME = "callrecord_settings"
    private const val KEY_API_KEY = "stepfun_api_key"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize the provider with application context.
     * Must be called once during app startup (e.g. in MainActivity.onCreate).
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Get the currently stored API Key.
     * Returns empty string if not configured.
     */
    fun getApiKey(): String {
        return prefs?.getString(KEY_API_KEY, "") ?: ""
    }

    /**
     * Save an API Key to persistent storage.
     */
    fun saveApiKey(apiKey: String) {
        prefs?.edit()?.putString(KEY_API_KEY, apiKey)?.apply()
    }

    /**
     * Clear the stored API Key.
     */
    fun clearApiKey() {
        prefs?.edit()?.remove(KEY_API_KEY)?.apply()
    }

    /**
     * Check whether an API Key has been configured.
     */
    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }
}
