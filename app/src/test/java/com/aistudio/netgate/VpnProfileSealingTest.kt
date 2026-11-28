package com.aistudio.netgate

import android.util.Base64
import com.aistudio.netgate.service.security.KeystoreSecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Backward compatibility and failure handling of the at-rest VPN config sealing. */
@RunWith(RobolectricTestRunner::class)
class VpnProfileSealingTest {

    private val legacyConfig = "[Interface]\nPrivateKey = c2VjcmV0\nAddress = 10.0.0.2/32"

    @Test
    fun `a legacy plaintext config is accepted and marked for re-sealing`() {
        // Values written before sealing existed are readable and must keep working.
        assertEquals(legacyConfig, KeystoreSecretBox.open(legacyConfig))
        assertTrue(KeystoreSecretBox.requiresSealing(legacyConfig))
        assertFalse(KeystoreSecretBox.isSealed(legacyConfig))
        assertFalse(KeystoreSecretBox.isUnreadable(legacyConfig))
    }

    @Test
    fun `sealed and unreadable values are not re-sealed`() {
        val sealed = KeystoreSecretBox.seal(legacyConfig)
        if (KeystoreSecretBox.isSealed(sealed)) {
            assertFalse(KeystoreSecretBox.requiresSealing(sealed))
            assertEquals(legacyConfig, KeystoreSecretBox.open(sealed))
        }
        val placeholder = KeystoreSecretBox.UNREADABLE_MARKER + "\n# sealed:enc:v1:AAAA:BBBB"
        assertFalse(KeystoreSecretBox.requiresSealing(placeholder))
        assertFalse(KeystoreSecretBox.requiresSealing(""))
    }

    @Test
    fun `a tampered ciphertext reports a re-import instead of crashing`() {
        val iv = Base64.encodeToString(ByteArray(12) { it.toByte() }, Base64.NO_WRAP)
        val tampered = Base64.encodeToString(ByteArray(24) { (it * 7).toByte() }, Base64.NO_WRAP)
        val value = "enc:v1:$iv:$tampered"

        val opened = KeystoreSecretBox.open(value)

        assertNotNull(opened)
        assertTrue(KeystoreSecretBox.isUnreadable(opened))
        assertTrue(opened.contains("re-import", ignoreCase = true))
        // The unusable payload is kept rather than discarded.
        assertTrue(opened.contains("enc:v1:"))
        // And it is never sealed again on the next save.
        assertEquals(opened, KeystoreSecretBox.seal(opened))
    }

    @Test
    fun `a truncated sealed value does not throw`() {
        val opened = KeystoreSecretBox.open("enc:v1:")
        assertNotNull(opened)
        assertTrue(KeystoreSecretBox.isUnreadable(opened))
    }
}
