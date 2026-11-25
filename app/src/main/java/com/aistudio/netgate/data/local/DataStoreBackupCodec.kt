package com.aistudio.netgate.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Converts a [Preferences] snapshot into type-tagged string pairs and back, so the whole
 * DataStore can travel inside an encrypted backup without listing every key by hand.
 *
 * Tag prefixes: b=boolean, i=int, l=long, f=float, s=string, ss=string set.
 */
object DataStoreBackupCodec {

    private const val SET_SEPARATOR = '\u0001'

    fun encode(preferences: Preferences): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in preferences.asMap()) {
            val encoded = when (value) {
                is Boolean -> "b:$value"
                is Int -> "i:$value"
                is Long -> "l:$value"
                is Float -> "f:$value"
                is String -> "s:$value"
                is Set<*> -> "ss:" + value.filterIsInstance<String>().joinToString(SET_SEPARATOR.toString())
                else -> null
            }
            if (encoded != null) out[key.name] = encoded
        }
        return out
    }

    fun apply(target: MutablePreferences, encoded: Map<String, String>) {
        for ((name, raw) in encoded) {
            if (name.isBlank() || raw.length < 2) continue
            when {
                raw.startsWith("b:") ->
                    target[booleanPreferencesKey(name)] = raw.substring(2).toBoolean()
                raw.startsWith("i:") ->
                    target[intPreferencesKey(name)] = raw.substring(2).toIntOrNull() ?: 0
                raw.startsWith("l:") ->
                    target[longPreferencesKey(name)] = raw.substring(2).toLongOrNull() ?: 0L
                raw.startsWith("f:") ->
                    target[floatPreferencesKey(name)] = raw.substring(2).toFloatOrNull() ?: 0f
                raw.startsWith("ss:") -> {
                    val values = raw.substring(3)
                        .split(SET_SEPARATOR)
                        .filter { it.isNotEmpty() }
                        .toSet()
                    target[stringSetPreferencesKey(name)] = values
                }
                raw.startsWith("s:") ->
                    target[stringPreferencesKey(name)] = raw.substring(2)
            }
        }
    }
}
