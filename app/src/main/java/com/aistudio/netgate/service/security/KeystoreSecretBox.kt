package com.aistudio.netgate.service.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM sealing for secrets kept in Room (VPN profiles hold WireGuard private keys and OpenVPN
 * credentials in their raw config).
 *
 * The key lives in the Android Keystore and never leaves it, so a database file copied off the
 * device - a rooted backup, an adb pull, a shared APK data folder - does not contain usable
 * credentials. Values carry an `enc:v1:` prefix so rows written before this existed stay readable:
 * unencrypted configs are returned unchanged and are re-sealed the next time they are saved.
 *
 * If the Keystore is unavailable (Robolectric, a broken OEM provider) the helpers degrade to
 * plaintext rather than losing the user's profile; the failure is logged once per call site.
 */
object KeystoreSecretBox {

    /**
     * First line of a config whose credentials cannot be opened any more. It is deliberately
     * readable text rather than an empty string, so the profile shows as unusable instead of
     * looking like an empty file.
     */
    const val UNREADABLE_MARKER =
        "# NetGate: this profile's sealed credentials are unavailable"

    private const val TAG = "KeystoreSecretBox"
    private const val KEY_ALIAS = "netgate.vpn.profiles.v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val SEPARATOR = ":"
    private const val TAG_BITS = 128

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    /** True when the stored value is the placeholder left behind by a lost Keystore key. */
    fun isUnreadable(value: String): Boolean = value.startsWith(UNREADABLE_MARKER)

    fun seal(plaintext: String): String {
        // Re-sealing an unreadable placeholder would bury the diagnostic text in a new layer and
        // drop the original payload out of reach.
        if (plaintext.isEmpty() || isSealed(plaintext) || isUnreadable(plaintext)) return plaintext
        val key = secretKey() ?: return plaintext
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val payload = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            PREFIX +
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
                Base64.encodeToString(payload, Base64.NO_WRAP)
        }.getOrElse { error ->
            Log.w(TAG, "Could not seal a stored secret; keeping it readable", error)
            plaintext
        }
    }

    fun open(value: String): String {
        if (!isSealed(value)) return value
        val key = secretKey() ?: return unreadable(value)
        return runCatching {
            val body = value.removePrefix(PREFIX)
            val iv = Base64.decode(body.substringBefore(SEPARATOR), Base64.NO_WRAP)
            val payload = Base64.decode(body.substringAfter(SEPARATOR), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrElse { error ->
            Log.e(TAG, "Could not open a sealed secret", error)
            unreadable(value)
        }
    }

    /**
     * Placeholder kept in place of a config that cannot be decrypted. It keeps the encrypted
     * payload so nothing is silently discarded, and it starts with [UNREADABLE_MARKER] so the UI
     * can label the profile as needing a re-import.
     */
    private fun unreadable(sealedValue: String): String = buildString {
        appendLine(UNREADABLE_MARKER)
        appendLine("# The Android Keystore key that protected it is gone (the data was restored to a")
        appendLine("# different device, or the keystore was reset), so this profile cannot connect.")
        appendLine("# Re-import the configuration to use it again. The encrypted payload is kept")
        appendLine("# below in case the key is ever recovered.")
        append("# sealed:$sealedValue")
    }

    private fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey(keyStore)
    }.getOrNull()

    private fun generateKey(keyStore: KeyStore): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        val key = generator.generateKey()
        // Keep the local view consistent with what the generator wrote.
        if (keyStore.containsAlias(KEY_ALIAS).not()) {
            Log.w(TAG, "Generated key is missing from the keystore")
        }
        return key
    }
}
