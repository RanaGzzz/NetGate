package com.example.service.policy

import com.example.service.ProxyFilterDecision

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
        val aliasHost: String? = null
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
        val category: BlockCategory = BlockCategory.GENERIC
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
        return ProxyFilterDecision(
            allow = isAllowed,
            reason = reason,
            isAdBlock = isAdBlock
        )
    }
}


