package com.example.service.dns

import com.example.service.policy.NetworkPolicy
import com.example.service.policy.NetworkPolicyEngine
import com.example.service.policy.PolicyContext
import com.example.service.policy.PolicyDecision

/**
 * The single DNS processing pipeline.
 *
 * ```
 * parse (DnsPacketParser) → policy (NetworkPolicyEngine) → block / QUIC downgrade
 *      → SafeSearch rewrite → transport (DnsTransportManager) → response (DnsResponseBuilder)
 * ```
 *
 * The engine is deliberately side-effect free: it returns a [DnsOutcome] and the owning
 * `NetGateVpnService` applies counters, DNS logs, connection monitoring and the TUN write in
 * the same order as before the refactor. Domain filtering is *not* reimplemented here — every
 * allow/block/rewrite decision comes from the existing policy package.
 */
class DnsEngine(
    private val transportManager: DnsTransportManager = DnsTransportManager(),
    private val responder: DnsResponseBuilder = DnsResponseBuilder
) {

    /** Parses an intercepted UDP/53 packet; returns null for non-DNS or malformed traffic. */
    fun parseUdpQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        return DnsPacketParser.parseUdpDnsQuery(packet, length)
    }

    /** Builds the immediate RST used to defeat DoH/DoT connections to routed resolver IPs. */
    fun buildTcpReset(packet: ByteArray, length: Int): ByteArray? {
        return DnsResponseBuilder.buildTcpResetPacket(packet, length)
    }

    /** Builds the NXDOMAIN packet returned for blocked queries. */
    fun buildBlockedResponse(query: ParsedDnsQuery): ByteArray {
        return responder.buildBlockedDnsResponsePacket(query)
    }

    /**
     * Resolves a raw DNS payload (no policy evaluation) through the standard transport chain.
     * Used by the HTTPS proxy for its internal A/AAAA lookups so there is exactly one resolver.
     */
    fun resolveRaw(
        dnsPayload: ByteArray,
        domain: String,
        resolutionContext: DnsResolutionContext
    ): DnsUpstreamResponse {
        return transportManager.resolve(dnsPayload, domain, resolutionContext)
    }

    /**
     * Runs one intercepted query through the pipeline.
     *
     * @param quicDowngradeEnabled true when DPI bypass is active and QUIC blocking is enabled.
     */
    fun process(
        query: ParsedDnsQuery,
        policy: NetworkPolicy,
        policyContext: PolicyContext,
        resolutionContext: DnsResolutionContext,
        quicDowngradeEnabled: Boolean
    ): DnsOutcome {
        return runCatching {
            processUnsafe(query, policy, policyContext, resolutionContext, quicDowngradeEnabled)
        }.getOrDefault(DnsOutcome.Dropped)
    }

    private fun processUnsafe(
        query: ParsedDnsQuery,
        policy: NetworkPolicy,
        policyContext: PolicyContext,
        resolutionContext: DnsResolutionContext,
        quicDowngradeEnabled: Boolean
    ): DnsOutcome {
        val decision = NetworkPolicyEngine.evaluate(
            rawDomain = query.domain,
            context = policyContext,
            policy = policy
        )

        if (decision is PolicyDecision.Block) {
            return DnsOutcome.Blocked(
                category = decision.category,
                reason = decision.reason,
                responsePacket = responder.buildBlockedDnsResponsePacket(query)
            )
        }

        val allow = decision as? PolicyDecision.Allow
        val isWhitelisted = allow?.isWhitelisted ?: false
        val isPassthrough = allow?.isPassthrough ?: false

        // Strip HTTP/3 QUIC SVCB/HTTPS records so browsers downgrade onto TLS/TCP for SNI DPI bypass.
        if (!isWhitelisted &&
            quicDowngradeEnabled &&
            DnsPacketParser.extractQueryType(query) == DnsRecordType.SVCB_HTTPS
        ) {
            return DnsOutcome.QuicDowngraded(
                responsePacket = responder.buildNoDataDnsResponsePacket(query)
            )
        }

        val safeRewrite = NetworkPolicyEngine.resolveSafeSearchRewrite(
            rawHost = query.domain,
            context = policyContext,
            policy = policy
        )

        if (safeRewrite != null) {
            val rewrittenQuery = responder.buildRewrittenQueryPayload(query, safeRewrite.aliasDomain)
            if (rewrittenQuery != null) {
                val aliasResolution = transportManager.resolve(
                    dnsPayload = rewrittenQuery,
                    domain = safeRewrite.aliasDomain,
                    context = resolutionContext
                )
                val aliasResponse = aliasResolution.payload
                if (aliasResponse != null) {
                    val rewrittenAnswer = responder.buildRewrittenAnswerPayload(query, aliasResponse)
                    if (rewrittenAnswer != null) {
                        return DnsOutcome.SafeRewrite(
                            responsePacket = responder.buildDnsResponsePacket(query, rewrittenAnswer),
                            label = safeRewrite.label,
                            aliasDomain = safeRewrite.aliasDomain,
                            antiPoisonTrips = aliasResolution.antiPoisonTrips
                        )
                    }
                }
            }
            // A failed rewrite falls through to the normal upstream path, as before.
        }

        val upstream = transportManager.resolve(
            dnsPayload = query.dnsPayload,
            domain = query.domain,
            context = resolutionContext
        )
        val upstreamPayload = upstream.payload ?: return DnsOutcome.Dropped

        return DnsOutcome.Answered(
            responsePacket = responder.buildDnsResponsePacket(query, upstreamPayload),
            upstreamPayload = upstreamPayload,
            transportLabel = upstream.label,
            isPassthrough = isPassthrough,
            antiPoisonTrips = upstream.antiPoisonTrips
        )
    }
}
