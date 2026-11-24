package com.aistudio.netgate.service.dns

import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.service.policy.PolicyDecision
import java.net.DatagramSocket
import java.net.Socket

/**
 * Immutable model of one intercepted UDP/53 DNS query.
 *
 * [dnsPayload] is the raw DNS message (starting at the 12-byte header), and
 * [questionEndOffset] points just past the QTYPE/QCLASS pair of the first question.
 */
data class ParsedDnsQuery(
    val domain: String,
    val isIpv6: Boolean,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayload: ByteArray,
    val questionEndOffset: Int
)

/**
 * SafeSearch / YouTube Restricted Mode alias metadata produced by policy evaluation.
 */
data class SafeRewriteResult(
    val aliasDomain: String,
    val label: String
)

/**
 * Result of one upstream resolution attempt.
 *
 * [payload] is null when the transport chain finished without a usable answer
 * (for example a dropped poisoned UDP answer). [label] is the user-facing transport
 * label recorded in DNS logs, and [antiPoisonTrips] counts forged responses discarded
 * while producing this result.
 */
data class DnsUpstreamResponse(
    val payload: ByteArray?,
    val label: String,
    val antiPoisonTrips: Int = 0
)

/**
 * Timeouts and fallback endpoints used by the DNS transport chain.
 *
 * These values intentionally match the pre-refactor implementation: DoH 1600/1600 ms,
 * DoTCP connect 1500 ms + read 1800 ms, UDP 1600 ms.
 */
data class DnsTransportConfig(
    val dohConnectTimeoutMs: Int = 1600,
    val dohReadTimeoutMs: Int = 1600,
    val tcpConnectTimeoutMs: Int = 1500,
    val tcpReadTimeoutMs: Int = 1800,
    val udpTimeoutMs: Int = 1600,
    val fallbackUdpServers: List<String> = DEFAULT_UPSTREAM_DNS_SERVERS,
    val backupDohEndpoints: List<Pair<String, String>> = BACKUP_DOH_ENDPOINTS
)

/** Socket protection hooks supplied by the VpnService that owns the TUN interface. */
data class DnsSocketPolicy(
    val protectSocket: (Socket) -> Boolean,
    val protectDatagramSocket: (DatagramSocket) -> Boolean,
    val selfDisallowedOnTunnel: Boolean
) {
    /**
     * Mirrors the original guard: a socket that cannot be protected is still usable when
     * NetGate itself is excluded from the tunnel (no packet loop can occur).
     */
    fun isUsable(protected: Boolean): Boolean = protected || selfDisallowedOnTunnel
}

/** Per-query resolution settings derived from live preferences. */
data class DnsResolutionContext(
    val transportMode: DnsTransportMode,
    val useDohUpstream: Boolean,
    val dohUrl: String,
    val upstreamTitle: String,
    val udpDnsServers: List<String>,
    val antiDnsPoisonEnabled: Boolean,
    val socketPolicy: DnsSocketPolicy,
    val config: DnsTransportConfig = DnsTransportConfig()
) {
    val effectiveUdpServers: List<String>
        get() = udpDnsServers.ifEmpty { config.fallbackUdpServers }
}

/**
 * Outcome of the DNS pipeline for a single query.
 *
 * The engine decides *what* happens; the owning service applies counters, logging,
 * connection monitoring and TUN writes so those side effects keep their original order.
 */
sealed interface DnsOutcome {
    /** Policy blocked the query and [responsePacket] (NXDOMAIN) should be returned. */
    data class Blocked(
        val category: PolicyDecision.BlockCategory,
        val reason: String,
        val responsePacket: ByteArray
    ) : DnsOutcome

    /** QTYPE 65 (SVCB/HTTPS) was answered with NODATA to force QUIC downgrade. */
    data class QuicDowngraded(
        val responsePacket: ByteArray
    ) : DnsOutcome

    /** SafeSearch / YouTube restriction alias was resolved and rebound to the original name. */
    data class SafeRewrite(
        val responsePacket: ByteArray,
        val label: String,
        val aliasDomain: String,
        val antiPoisonTrips: Int = 0
    ) : DnsOutcome

    /** Query was resolved upstream and [responsePacket] carries the upstream answer. */
    data class Answered(
        val responsePacket: ByteArray,
        val upstreamPayload: ByteArray,
        val transportLabel: String,
        val isPassthrough: Boolean,
        val antiPoisonTrips: Int = 0
    ) : DnsOutcome

    /** No transport produced an answer; nothing is written back (original behaviour). */
    data object Dropped : DnsOutcome
}

/** DNS record types relevant to the pipeline. */
object DnsRecordType {
    const val A = 1
    const val AAAA = 28
    const val SVCB_HTTPS = 65
}

/** Fallback plain UDP resolvers (used when no upstream list is configured). */
val DEFAULT_UPSTREAM_DNS_SERVERS: List<String> = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

/** Backup DoH endpoints tried when the configured endpoint fails. */
val BACKUP_DOH_ENDPOINTS: List<Pair<String, String>> = listOf(
    "Google" to "https://dns.google/dns-query",
    "Quad9" to "https://dns.quad9.net/dns-query"
)
