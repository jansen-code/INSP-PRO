package com.raylson.jansen.inspetor.platform

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * ═══════════════════════════════════════════════════════════════════
 * SecureStorage.android.kt (androidMain)
 *
 * `actual` do SecureStorage usando EncryptedSharedPreferences, exatamente
 * como o antigo `SecurePrefs` do app original — trocamos só o transporte
 * (Activity Context → applicationContext injetado via expect fun),
 * mantendo AES256_SIV pra chaves e AES256_GCM pro conteúdo.
 *
 * IMPORTANTE: é preciso injetar um `Context` de aplicação em algum ponto
 * de inicialização (ex.: uma classe `Application` ou `initKoin(context)`)
 * antes da primeira chamada de `createSecureStorage`. Isso não existia
 * antes porque a Activity já tinha `this` disponível; no KMP, o contexto
 * precisa ser guardado uma vez, no boot do app.
 * ═══════════════════════════════════════════════════════════════════
 */
object AndroidContextHolder {
    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

private class AndroidSecureStorage(name: String) : SecureStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(AndroidContextHolder.appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            AndroidContextHolder.appContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
    override fun clearAll() { prefs.edit().clear().apply() }
}

actual fun createSecureStorage(name: String): SecureStorage = AndroidSecureStorage(name)

/*
 * ─────────────────────────────────────────────────────────────────
 * iosMain (esboço — criar em iosMain/SecureStorage.ios.kt):
 *
 * O iOS usa o Keychain nativo (Security.framework) em vez de arquivo
 * criptografado em disco. Cada par chave/valor vira um item de Keychain
 * (kSecClassGenericPassword), que já é criptografado pelo próprio
 * Secure Enclave do aparelho — não existe "modo texto plano" possível.
 *
 * actual fun createSecureStorage(name: String): SecureStorage =
 *     KeychainSecureStorage(service = name)
 *
 * A implementação usa cinterop com Security.framework
 * (SecItemAdd / SecItemCopyMatching / SecItemUpdate / SecItemDelete),
 * codificando Int/Boolean como String antes de gravar (Keychain só
 * guarda NSData/String nativamente).
 * ─────────────────────────────────────────────────────────────────
 */

