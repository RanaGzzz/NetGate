package com.aistudio.netgate.service.dns

import com.aistudio.netgate.data.model.DnsTransportMode

/**
 * Selects and sequences DNS upstream transports for the active [DnsTransportMode].
 *
 * This is a faithful extraction of the previous `NetGateVpnService.resolveDnsUpstream` switch,
 * including the exact fallback order, transport labels and anti-poison escalations:
 *
 * - `DOH_WITH_FALLBACK`: primary DoH → validated DoTCP → validated UDP (anti-poison) → backup DoH.
 * - `DNS_OVER_TCP`:      DoTCP → DoH (primary + backups) → validated UDP.
 * - `UDP_THEN_TCP`:      UDP → DoH (poison guard) → DoTCP.
 */
class DnsTransportManager(
    private val resolver: DnsUpstreamResolver = DnsUpstreamResolver()
) {

    fun resolve(
        dnsPayload: ByteArray,
        domain: String,
        context: DnsResolutionContext
    ): DnsUpstreamResponse {
        val antiPoison = context.antiDnsPoisonEnabled

        return when (context.transportMode) {
            DnsTransportMode.DNS_OVER_TCP -> {
                val tcp = resolver.forwardTcp(dnsPayload, context)
                if (tcp?.payload != null && !resolver.isPoisoned(domain, tcp.payload, antiPoison)) {
                    tcp
                } else {
                    resolver.forwardDohWithBackup(dnsPayload, context)
                        ?: resolver.forwardValidatedUdp(dnsPayload, domain, context)
                }
            }

            DnsTransportMode.UDP_THEN_TCP -> {
                val udp = resolver.forwardUdp(dnsPayload, context)
                if (udp?.payload != null && !resolver.isPoisoned(domain, udp.payload, antiPoison)) {
                    udp
                } else {
                    val poisonedUdp = udp?.payload != null
                    val doh = resolver.forwardDohWithBackup(dnsPayload, context)
                    if (doh != null) {
                        doh.copy(
                            label = "${doh.label} (Poison Guard)",
                            antiPoisonTrips = if (poisonedUdp) 1 else 0
                        )
                    } else {
                        resolver.forwardTcp(dnsPayload, context)
                            ?: DnsUpstreamResponse(
                                payload = null,
                                label = "${context.upstreamTitle} DoTCP",
                                antiPoisonTrips = if (poisonedUdp) 1 else 0
                            )
                    }
                }
            }

            DnsTransportMode.DOH_WITH_FALLBACK -> {
                if (context.useDohUpstream) {
                    resolver.forwardDoh(dnsPayload, context)?.let { return it }
                }

                val tcp = resolver.forwardTcp(dnsPayload, context)
                if (tcp?.payload != null && !resolver.isPoisoned(domain, tcp.payload, antiPoison)) {
                    tcp
                } else {
                    val validatedUdp = resolver.forwardValidatedUdp(dnsPayload, domain, context)
                    if (validatedUdp.payload != null) {
                        validatedUdp
                    } else {
                        resolver.forwardBackupDoh(dnsPayload, context) ?: validatedUdp
                    }
                }
            }
        }
    }
}
