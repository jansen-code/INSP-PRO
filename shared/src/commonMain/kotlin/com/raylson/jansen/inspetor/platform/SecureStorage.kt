package com.raylson.jansen.inspetor.platform

/**
 * ═══════════════════════════════════════════════════════════════════
 * SecureStorage.kt (commonMain)
 *
 * Substitui `SecurePrefs.get(context, name)` (usado na DashboardActivity
 * para "last_station", "last_hm_<estacao>", "last_lago_na" etc.) por uma
 * interface KMP. NUNCA implementar em texto plano — Android usa
 * EncryptedSharedPreferences (Jetpack Security), iOS usa Keychain.
 * ═══════════════════════════════════════════════════════════════════
 */
interface SecureStorage {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun clearAll()
}

/**
 * `name` mapeia pro mesmo conceito de arquivo de preferências original
 * (ex.: "inspetor_prefs", "historico_prefs").
 */
expect fun createSecureStorage(name: String): SecureStorage
