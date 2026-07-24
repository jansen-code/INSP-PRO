@file:OptIn(ExperimentalForeignApi::class)

package com.raylson.jansen.inspetor.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

private class KeychainSecureStorage(private val suiteName: String) : SecureStorage {

    private val defaults: NSUserDefaults =
        NSUserDefaults(suiteName = suiteName) ?: NSUserDefaults.standardUserDefaults

    override fun getString(key: String, default: String?): String? =
        defaults.stringForKey(key) ?: default

    override fun putString(key: String, value: String) {
        defaults.setObject(value, key)
    }

    override fun getInt(key: String, default: Int): Int {
        val v = defaults.integerForKey(key)
        return v.toInt()
    }

    override fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), key)
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        defaults.boolForKey(key)

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override fun clearAll() {
        defaults.removePersistentDomainForName(domainName = suiteName)
    }
}

actual fun createSecureStorage(name: String): SecureStorage =
    KeychainSecureStorage(name)
