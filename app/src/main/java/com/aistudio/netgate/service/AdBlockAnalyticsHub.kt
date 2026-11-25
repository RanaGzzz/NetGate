package com.aistudio.netgate.service

import com.aistudio.netgate.data.model.AdAnalyticsSummary
import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.TopAppStat
import com.aistudio.netgate.data.model.TopDomainStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, lock-free-friendly telemetry hub tracking per-session Ad & Filter Log analytics:
 * total requests, total blocked, category breakdown (Ads, Tracker, Malware, Phishing, Telemetry,
 * Custom Rule), top blocked domains, top apps generating requests, and per-domain hit counts.
 */
object AdBlockAnalyticsHub {

    private data class DomainCounter(
        val domain: String,
        val hits: AtomicLong = AtomicLong(0L),
        @Volatile var reason: AdBlockReason = AdBlockReason.ADS,
        @Volatile var filterListName: String = ""
    )

    private data class AppCounter(
        val appPackage: String,
        @Volatile var appName: String,
        val totalRequests: AtomicLong = AtomicLong(0L),
        val blockedRequests: AtomicLong = AtomicLong(0L)
    )

    private val totalRequests = AtomicLong(0L)
    private val totalBlockedRequests = AtomicLong(0L)
    private val adsBlocked = AtomicLong(0L)
    private val trackersBlocked = AtomicLong(0L)
    private val malwareBlocked = AtomicLong(0L)
    private val phishingBlocked = AtomicLong(0L)
    private val telemetryBlocked = AtomicLong(0L)
    private val customRuleBlocked = AtomicLong(0L)

    private val blockedDomainMap = ConcurrentHashMap<String, DomainCounter>(256)
    private val appStatMap = ConcurrentHashMap<String, AppCounter>(64)
    private val entryHitMap = ConcurrentHashMap<String, AtomicLong>(512)

    private val _summaryFlow = MutableStateFlow(AdAnalyticsSummary())
    val summaryFlow: StateFlow<AdAnalyticsSummary> = _summaryFlow.asStateFlow()

    /**
     * Records a single DNS / HTTP / HTTPS request and returns its running hit count for the Filter Log.
     */
    fun recordEvent(
        domain: String,
        status: DnsQueryStatus,
        blockReason: AdBlockReason,
        filterListName: String,
        appPackage: String,
        appName: String
    ): Int {
        totalRequests.incrementAndGet()

        val isBlocked = status == DnsQueryStatus.BLOCKED_AD ||
            status == DnsQueryStatus.BLOCKED_WEBSITE ||
            status == DnsQueryStatus.BLOCKED_HTTPS ||
            status == DnsQueryStatus.BLOCKED_APP

        val cleanDomain = domain.trim().trimEnd('.').lowercase()
        val hitKey = "$cleanDomain|${status.name}"
        val hitCounter = entryHitMap.computeIfAbsent(hitKey) { AtomicLong(0L) }
        val currentHitCount = hitCounter.incrementAndGet().toInt()
        if (entryHitMap.size > 4096) {
            entryHitMap.clear()
        }

        if (isBlocked) {
            totalBlockedRequests.incrementAndGet()
            when (blockReason) {
                AdBlockReason.ADS -> adsBlocked.incrementAndGet()
                AdBlockReason.TRACKER -> trackersBlocked.incrementAndGet()
                AdBlockReason.MALWARE -> malwareBlocked.incrementAndGet()
                AdBlockReason.PHISHING -> phishingBlocked.incrementAndGet()
                AdBlockReason.TELEMETRY -> telemetryBlocked.incrementAndGet()
                AdBlockReason.CUSTOM_RULE -> customRuleBlocked.incrementAndGet()
                AdBlockReason.NONE -> {
                    if (status == DnsQueryStatus.BLOCKED_AD || status == DnsQueryStatus.BLOCKED_HTTPS) {
                        adsBlocked.incrementAndGet()
                    } else {
                        customRuleBlocked.incrementAndGet()
                    }
                }
            }

            if (cleanDomain.isNotEmpty()) {
                val counter = blockedDomainMap.computeIfAbsent(cleanDomain) {
                    DomainCounter(domain = cleanDomain)
                }
                counter.hits.incrementAndGet()
                if (blockReason != AdBlockReason.NONE) {
                    counter.reason = blockReason
                }
                if (filterListName.isNotBlank()) {
                    counter.filterListName = filterListName
                }
                if (blockedDomainMap.size > 2048) {
                    trimLowestDomains()
                }
            }
        }

        val resolvedPkg = appPackage.trim().ifEmpty { "system" }
        val resolvedName = appName.trim().ifEmpty {
            if (resolvedPkg == "system") "System / VPN" else resolvedPkg
        }
        val appCounter = appStatMap.computeIfAbsent(resolvedPkg) {
            AppCounter(appPackage = resolvedPkg, appName = resolvedName)
        }
        if (appName.isNotBlank()) {
            appCounter.appName = appName
        }
        appCounter.totalRequests.incrementAndGet()
        if (isBlocked) {
            appCounter.blockedRequests.incrementAndGet()
        }

        publishSnapshot()
        return currentHitCount
    }

    fun clear() {
        totalRequests.set(0L)
        totalBlockedRequests.set(0L)
        adsBlocked.set(0L)
        trackersBlocked.set(0L)
        malwareBlocked.set(0L)
        phishingBlocked.set(0L)
        telemetryBlocked.set(0L)
        customRuleBlocked.set(0L)
        blockedDomainMap.clear()
        appStatMap.clear()
        entryHitMap.clear()
        _summaryFlow.value = AdAnalyticsSummary()
    }

    private fun trimLowestDomains() {
        val toRemove = blockedDomainMap.values
            .sortedBy { it.hits.get() }
            .take(512)
        for (item in toRemove) {
            blockedDomainMap.remove(item.domain)
        }
    }

    private fun publishSnapshot() {
        val topDomains = blockedDomainMap.values
            .map {
                TopDomainStat(
                    domain = it.domain,
                    hits = it.hits.get(),
                    blockReason = it.reason,
                    filterListName = it.filterListName
                )
            }
            .sortedByDescending { it.hits }
            .take(10)

        val topApps = appStatMap.values
            .map {
                TopAppStat(
                    appPackage = it.appPackage,
                    appName = it.appName,
                    totalRequests = it.totalRequests.get(),
                    blockedRequests = it.blockedRequests.get()
                )
            }
            .sortedByDescending { it.totalRequests }
            .take(10)

        _summaryFlow.value = AdAnalyticsSummary(
            totalRequests = totalRequests.get(),
            totalBlockedRequests = totalBlockedRequests.get(),
            adsBlocked = adsBlocked.get(),
            trackersBlocked = trackersBlocked.get(),
            malwareBlocked = malwareBlocked.get(),
            phishingBlocked = phishingBlocked.get(),
            telemetryBlocked = telemetryBlocked.get(),
            customRuleBlocked = customRuleBlocked.get(),
            topBlockedDomains = topDomains,
            topApps = topApps
        )
    }
}
