package com.aistudio.netgate.service.policy

import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.service.ProxyFilterDecision

/**
 * Result of a network policy evaluation for a domain, URL path, or client request.
 */
sealed interface PolicyDecision {
    val isAllowed: Boolean
    val reason: String
    val isAdBlock: Boolean

    /**
     * Request is permitted through the firewall / proxy.
     */
    data class Allow(
        override val reason: String = "Allowed",
        val isWhitelisted: Boolean = false,
        val isPassthrough: Boolean = false,
        val aliasHost: String? = null,
        val lookupLatencyMicros: Long = 0L
    ) : PolicyDecision {
        override val isAllowed: Boolean get() = true
        override val isAdBlock: Boolean get() = false
    }

    /**
     * Request is rejected, dropped, or sinkholed.
     */
    data class Block(
        override val reason: String,
        override val isAdBlock: Boolean = false,
        val category: BlockCategory = BlockCategory.GENERIC,
        val filterListName: String = "",
        val blockReason: AdBlockReason = AdBlockReason.NONE,
        val lookupLatencyMicros: Long = 0L
    ) : PolicyDecision {
        override val isAllowed: Boolean get() = false
    }

    enum class BlockCategory {
        GENERIC,
        WEBSITE_BLOCKLIST,
        WHITELIST_LOCKDOWN,
        AD_TRACKER,
        YOUTUBE_AD,
        APP_FIREWALL,
        DOH_BYPASS_CANARY,
        HOTSPOT_CLIENT_BANNED,
        HOTSPOT_DATA_CAP
    }

    fun toProxyFilterDecision(): ProxyFilterDecision {
        return when (this) {
            is Allow -> ProxyFilterDecision(
                allow = true,
                reason = reason,
                isAdBlock = false,
                latencyMicros = lookupLatencyMicros
            )
            is Block -> ProxyFilterDecision(
                allow = false,
                reason = reason,
                isAdBlock = isAdBlock,
                filterListName = filterListName,
                blockReason = blockReason,
                latencyMicros = lookupLatencyMicros
            )
        }
    }
}


