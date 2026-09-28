package com.downtify.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ServerConfig(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "downtify-secure", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var url: String get() = prefs.getString("url", "") ?: ""; set(value) = prefs.edit().putString("url", value).apply()
    var serverId: String get() = prefs.getString("server_id", "") ?: ""; set(value) = prefs.edit().putString("server_id", value).apply()
    var token: String get() = prefs.getString("token", "") ?: ""; set(value) = prefs.edit().putString("token", value).apply()
    var legacyWeb: Boolean get() = prefs.getBoolean("legacy_web", false); set(value) = prefs.edit().putBoolean("legacy_web", value).apply()
    var cursor: Long get() = prefs.getLong("cursor", 0); set(value) = prefs.edit().putLong("cursor", value).apply()
    var wifiOnly: Boolean get() = prefs.getBoolean("wifi_only", false); set(value) = prefs.edit().putBoolean("wifi_only", value).apply()
    var quality: String get() = prefs.getString("quality", "original") ?: "original"; set(value) = prefs.edit().putString("quality", value).apply()
}
