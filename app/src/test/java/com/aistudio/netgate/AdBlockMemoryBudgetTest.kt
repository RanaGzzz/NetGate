package com.aistudio.netgate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.local.AdFilterListEntity
import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.service.AdBlockFilterEngine
import com.aistudio.netgate.service.AdBlockMatcherSnapshot
import com.aistudio.netgate.service.AdRuleMatchMeta
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the memory-safety logic added to the ad-block matcher: the heap-derived domain budget,
 * the compact owner index used instead of per-domain metadata, and the skipped-list counter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdBlockMemoryBudgetTest {

    private lateinit var context: Context
    private val createdListIds = mutableListOf<String>()
    private val megabyte = 1024L * 1024L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        createdListIds.forEach { AdBlockFilterEngine.deleteCacheFiles(context, it) }
        createdListIds.clear()
    }

    @Test
    fun `domainBudgetForHeap clamps small heaps to the minimum budget`() {
        assertEquals(120_000, AdBlockFilterEngine.domainBudgetForHeap(0L))
        assertEquals(120_000, AdBlockFilterEngine.domainBudgetForHeap(32L * megabyte))
        // 128 MB would scale to 89,600 domains, below the floor.
        assertEquals(120_000, AdBlockFilterEngine.domainBudgetForHeap(128L * megabyte))
    }

    @Test
    fun `domainBudgetForHeap scales with heap size between the clamps`() {
        assertEquals(179_200, AdBlockFilterEngine.domainBudgetForHeap(256L * megabyte))
        assertEquals(268_800, AdBlockFilterEngine.domainBudgetForHeap(384L * megabyte))
        assertEquals(358_400, AdBlockFilterEngine.domainBudgetForHeap(512L * megabyte))
    }

    @Test
    fun `domainBudgetForHeap clamps large heaps to the maximum budget`() {
        // 1 GB would scale to 716,800 domains, above the ceiling.
        assertEquals(450_000, AdBlockFilterEngine.domainBudgetForHeap(1024L * megabyte))
        assertEquals(450_000, AdBlockFilterEngine.domainBudgetForHeap(8L * 1024L * megabyte))
        assertEquals(450_000, AdBlockFilterEngine.domainBudgetForHeap(Long.MAX_VALUE))
    }

    @Test
    fun `domainBudgetForHeap never shrinks as the heap grows`() {
        var previous = 0
        var heapMb = 0L
        while (heapMb <= 2048L) {
            val budget = AdBlockFilterEngine.domainBudgetForHeap(heapMb * megabyte)
            assertTrue(
                "budget shrank from $previous to $budget at ${heapMb}MB",
                budget >= previous
            )
            assertTrue(budget in 120_000..450_000)
            previous = budget
            heapMb += 64L
        }
    }

    @Test
    fun `owner index resolves list metadata for exact and parent domains`() {
        val listA = AdRuleMatchMeta("list_a", "List A", AdBlockReason.ADS)
        val listB = AdRuleMatchMeta("list_b", "List B", AdBlockReason.TRACKER)
        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = setOf("ads.example.com", "example.com"),
            exceptionDomains = emptySet(),
            ownerListIndex = mapOf(
                "ads.example.com" to 1.toByte(),
                "example.com" to 0.toByte()
            ),
            ownerListMeta = listOf(listA, listB)
        )

        val exact = snapshot.matchBlockedRule("ads.example.com")
        assertTrue(exact.isBlocked)
        assertEquals("ads.example.com", exact.matchedRuleDomain)
        assertEquals("List B", exact.filterListTitle)
        assertEquals(AdBlockReason.TRACKER, exact.blockReason)

        // A subdomain inherits the metadata of the rule that actually matched.
        val child = snapshot.matchBlockedRule("cdn.ads.example.com")
        assertTrue(child.isBlocked)
        assertEquals("ads.example.com", child.matchedRuleDomain)
        assertEquals("List B", child.filterListTitle)

        val parentOnly = snapshot.matchBlockedRule("deep.sub.example.com")
        assertTrue(parentOnly.isBlocked)
        assertEquals("example.com", parentOnly.matchedRuleDomain)
        assertEquals("List A", parentOnly.filterListTitle)
        assertEquals(AdBlockReason.ADS, parentOnly.blockReason)

        assertFalse(snapshot.matchBlockedRule("clean.test").isBlocked)
    }

    @Test
    fun `owner index falls back to the core shield when no metadata matches`() {
        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = setOf("orphan.example"),
            exceptionDomains = emptySet(),
            ownerListIndex = mapOf("orphan.example" to 9.toByte()),
            ownerListMeta = listOf(AdRuleMatchMeta("list_a", "List A", AdBlockReason.ADS))
        )

        val result = snapshot.matchBlockedRule("orphan.example")
        assertTrue(result.isBlocked)
        assertEquals("NetGate Core Ad Shield", result.filterListTitle)
    }

    @Test
    fun `legacy per-domain metadata still wins when provided`() {
        val legacyMeta = AdRuleMatchMeta("list_legacy", "Legacy List", AdBlockReason.MALWARE)
        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = setOf("legacy.example"),
            exceptionDomains = emptySet(),
            domainMeta = mapOf("legacy.example" to legacyMeta),
            ownerListIndex = mapOf("legacy.example" to 0.toByte()),
            ownerListMeta = listOf(AdRuleMatchMeta("list_a", "List A", AdBlockReason.ADS))
        )

        val result = snapshot.matchBlockedRule("legacy.example")
        assertEquals("Legacy List", result.filterListTitle)
        assertEquals(AdBlockReason.MALWARE, result.blockReason)
    }

    @Test
    fun `truncatedListCount reports lists skipped by the memory budget`() {
        val budget = AdBlockFilterEngine.BUILTIN_AD_CORE_DOMAINS.size + 10
        val lists = (1..3).map { index ->
            cacheList(
                id = "test_budget_list_$index",
                title = "Budget List $index",
                domains = (1..50).map { "d$index-$it.test" }
            )
        }

        val snapshot = AdBlockFilterEngine.buildMatcherSnapshot(
            context = context,
            enabledLists = lists,
            domainBudget = budget
        )

        // The core shield plus the first 10 domains of list 1 fill the budget exactly.
        assertEquals(budget, snapshot.blockedDomains.size)
        assertEquals(2, snapshot.truncatedListCount)
        assertEquals(2, AdBlockFilterEngine.perfStatsFlow.value.truncatedListCount)

        // Domains that did fit still resolve to their owning list through the compact index.
        val kept = snapshot.matchBlockedRule("d1-1.test")
        assertTrue(kept.isBlocked)
        assertEquals("Budget List 1", kept.filterListTitle)
        assertFalse(snapshot.matchBlockedRule("d3-1.test").isBlocked)
    }

    @Test
    fun `truncatedListCount stays zero when every list fits`() {
        val lists = (1..2).map { index ->
            cacheList(
                id = "test_fitting_list_$index",
                title = "Fitting List $index",
                domains = (1..20).map { "f$index-$it.test" }
            )
        }

        val snapshot = AdBlockFilterEngine.buildMatcherSnapshot(
            context = context,
            enabledLists = lists,
            domainBudget = 10_000
        )

        assertEquals(0, snapshot.truncatedListCount)
        assertEquals(0, AdBlockFilterEngine.perfStatsFlow.value.truncatedListCount)
        assertEquals(
            AdBlockFilterEngine.BUILTIN_AD_CORE_DOMAINS.size + 40,
            snapshot.blockedDomains.size
        )
        assertEquals("Fitting List 2", snapshot.matchBlockedRule("f2-7.test").filterListTitle)
    }

    private fun cacheList(
        id: String,
        title: String,
        domains: List<String>
    ): AdFilterListEntity {
        AdBlockFilterEngine.writeCompressedCache(context, id, domains)
        createdListIds += id
        return AdFilterListEntity(
            id = id,
            title = title,
            description = "Test list",
            sourceUrl = "https://example.test/$id.hosts",
            category = "ads",
            isEnabled = true,
            domainCount = domains.size
        )
    }
}
