package com.aistudio.netgate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.GitHubReleaseInfo
import com.aistudio.netgate.data.network.GitHubReleaseUpdateChecker
import com.aistudio.netgate.data.network.LocalNetworkPermission
import com.aistudio.netgate.service.HashedDomainSet
import com.aistudio.netgate.service.security.KeystoreSecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the follow-up work done after 3.9.6: the compact matcher set, the beta update channel,
 * the two new preferences and the Android 17 local-network gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReleaseHardeningTest {

    @Test
    fun `hashed domain set answers membership and owner`() {
        val set = HashedDomainSet.Builder(4).apply {
            add("doubleclick.net", 3)
            add("ads.example.com", 7)
            add("doubleclick.net", 9)
        }.build()

        assertEquals(2, set.size)
        assertTrue(set.contains("doubleclick.net"))
        assertTrue(set.contains("ads.example.com"))
        assertFalse(set.contains("example.com"))
        // The first owner recorded for a duplicate wins.
        assertEquals(3, set.ownerOf("doubleclick.net"))
        assertEquals(7, set.ownerOf("ads.example.com"))
        assertEquals(HashedDomainSet.NO_OWNER, set.ownerOf("example.com"))
    }

    @Test
    fun `hashed domain set reports a duplicate instead of growing`() {
        val builder = HashedDomainSet.Builder(2)
        assertTrue(builder.add("tracker.example"))
        assertFalse(builder.add("tracker.example"))
        assertTrue(builder.add("other.example"))
        assertEquals(2, builder.uniqueSize)
        assertEquals(2, builder.build().size)
    }

    @Test
    fun `hashed domain set survives growth past its initial capacity`() {
        val builder = HashedDomainSet.Builder(2)
        val domains = (0 until 5_000).map { "host$it.example.com" }
        domains.forEach { builder.add(it) }
        assertEquals(domains.size, builder.uniqueSize)

        val set = builder.build()
        assertEquals(domains.size, set.size)
        assertTrue(set.contains("host0.example.com"))
        assertTrue(set.contains("host4999.example.com"))
        assertFalse(set.contains("host5000.example.com"))
    }

    @Test
    fun `hashed domain set refuses iteration instead of inventing entries`() {
        val set = HashedDomainSet.Builder(1).apply { add("a.example") }.build()
        try {
            set.iterator()
            fail("HashedDomainSet must not pretend to iterate over domains it no longer stores")
        } catch (expected: UnsupportedOperationException) {
            assertTrue(expected.message!!.contains("hashes"))
        }
    }

    @Test
    fun `empty hashed set is empty`() {
        assertTrue(HashedDomainSet.EMPTY.isEmpty())
        assertEquals(0, HashedDomainSet.EMPTY.size)
        assertFalse(HashedDomainSet.EMPTY.contains("anything.example"))
    }

    @Test
    fun `stable channel ignores prereleases`() {
        val beta = release("3.10-beta.1", prerelease = true)
        val stable = release("3.9.6", prerelease = false)

        val selected = GitHubReleaseUpdateChecker.selectLatestRelease(
            recentReleases = listOf(beta, stable),
            stableLatest = stable,
            includePrereleases = false
        )
        assertEquals("3.9.6", selected?.versionName)
    }

    @Test
    fun `beta channel picks the newest build including prereleases`() {
        val stable = release("3.9.6", prerelease = false)
        val beta = release("3.10-beta.1", prerelease = true)

        val selected = GitHubReleaseUpdateChecker.selectLatestRelease(
            recentReleases = listOf(stable, beta),
            stableLatest = stable,
            includePrereleases = true
        )
        assertEquals("3.10-beta.1", selected?.versionName)
    }

    @Test
    fun `beta channel keeps an older prerelease below a newer stable`() {
        val stable = release("3.10", prerelease = false)
        val oldBeta = release("3.10-beta.1", prerelease = true)

        val selected = GitHubReleaseUpdateChecker.selectLatestRelease(
            recentReleases = listOf(oldBeta, stable),
            stableLatest = null,
            includePrereleases = true
        )
        assertEquals("3.10", selected?.versionName)
    }

    @Test
    fun `release selection falls back to nothing when no release exists`() {
        assertNull(
            GitHubReleaseUpdateChecker.selectLatestRelease(
                recentReleases = emptyList(),
                stableLatest = null,
                includePrereleases = true
            )
        )
    }

    @Test
    fun `power and update-channel preferences default to off`() {
        val prefs = FirewallPreferencesState()
        assertFalse(prefs.lowPowerMode)
        assertFalse(prefs.includeBetaUpdates)
    }

    @Test
    fun `local network permission is not required before Android 17`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(LocalNetworkPermission.isRequired())
        assertTrue(LocalNetworkPermission.isGranted(context))
    }

    @Test
    fun `stored vpn configs round-trip through the secret box`() {
        val config = "[Interface]\nPrivateKey = c2VjcmV0LXByaXZhdGUta2V5\nAddress = 10.0.0.2/32"
        val sealed = KeystoreSecretBox.seal(config)

        // Without a usable Android Keystore (Robolectric) sealing degrades to plaintext, so the
        // round-trip has to hold either way; when it is available the value must not be readable.
        if (sealed != config) {
            assertTrue(KeystoreSecretBox.isSealed(sealed))
            assertFalse(sealed.contains("PrivateKey"))
        }
        assertEquals(config, KeystoreSecretBox.open(sealed))
    }

    @Test
    fun `a lost keystore key leaves a usable diagnostic instead of an empty config`() {
        val placeholder = KeystoreSecretBox.UNREADABLE_MARKER + "\n# sealed:enc:v1:AAAA:BBBB"

        assertTrue(KeystoreSecretBox.isUnreadable(placeholder))
        assertFalse(KeystoreSecretBox.isSealed(placeholder))
        // Sealing the placeholder again would bury the text and push the payload out of reach.
        assertEquals(placeholder, KeystoreSecretBox.seal(placeholder))
        assertEquals(placeholder, KeystoreSecretBox.open(placeholder))
        assertTrue(placeholder.contains("enc:v1:"))
        assertFalse(placeholder.isBlank())
    }

    @Test
    fun `sealing is idempotent`() {
        val once = KeystoreSecretBox.seal("token=abc")
        assertEquals(once, KeystoreSecretBox.seal(once))
        assertEquals("token=abc", KeystoreSecretBox.open(once))
    }

    private fun release(version: String, prerelease: Boolean) = GitHubReleaseInfo(
        tagName = "v$version",
        versionName = version,
        title = "NetGate $version",
        body = "",
        publishedAt = "2026-11-26",
        htmlUrl = "https://github.com/RanaGzzz/NetGate/releases/tag/v$version",
        isPrerelease = prerelease
    )
}
