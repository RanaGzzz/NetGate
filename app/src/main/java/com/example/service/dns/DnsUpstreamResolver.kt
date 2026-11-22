package com.example.service.dns

import com.example.service.AdBlockFilterEngine
import com.example.service.DpiBypassEngine
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** DNS-over-HTTPS transport (RFC 8484) — single implementation, reused from AdBlockFilterEngine. */
interface DnsDohClient {
    fun forward(payload: ByteArray, endpointUrl: String): ByteArray?
}

/** DNS-over-TCP transport (RFC 1035 2-byte length framing). */
interface DnsTcpQueryClient {
    fun query(payload: ByteArray, serverIp: String, policy: DnsSocketPolicy, config: DnsTransportConfig): ByteArray?
}

/** Plain UDP/53 transport. */
interface DnsUdpQueryClient {
    fun query(payload: ByteArray, serverIp: String, policy: DnsSocketPolicy, config: DnsTransportConfig): ByteArray?
}

/** Default DoH client: delegates to the existing, already-tested HTTPS implementation. */
object DefaultDnsDohClient : DnsDohClient {
    override fun forward(payload: ByteArray, endpointUrl: String): ByteArray? {
        return AdBlockFilterEngine.forwardDnsOverHttps(payload, endpointUrl)
    }
}

/** Default DoTCP client, preserving the original 1500 ms connect / 1800 ms read timeouts. */
object SocketDnsTcpClient : DnsTcpQueryClient {
    override fun query(
        payload: ByteArray,
        serverIp: String,
        policy: DnsSocketPolicy,
        config: DnsTransportConfig
    ): ByteArray? {
        if (payload.isEmpty() || payload.size > 65535) return null
        var socket: Socket? = null
        return try {
            socket = Socket()
            if (!policy.isUsable(policy.protectSocket(socket))) {
                socket.close()
                return null
            }
            socket.tcpNoDelay = true
            socket.soTimeout = config.tcpReadTimeoutMs
            socket.connect(
                InetSocketAddress(InetAddress.getByName(serverIp), 53),
                config.tcpConnectTimeoutMs
            )
            val out = socket.getOutputStream()
            val frame = ByteArray(2 + payload.size)
            frame[0] = ((payload.size ushr 8) and 0xFF).toByte()
            frame[1] = (payload.size and 0xFF).toByte()
            System.arraycopy(payload, 0, frame, 2, payload.size)
            out.write(frame)
            out.flush()

            val dataIn = DataInputStream(socket.getInputStream())
            val respLen = dataIn.readUnsignedShort()
            if (respLen in 12..65535) {
                val resp = ByteArray(respLen)
                dataIn.readFully(resp)
                resp
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }
}

/** Default UDP/53 client, preserving the original 1600 ms socket timeout. */
object DatagramDnsUdpClient : DnsUdpQueryClient {
    override fun query(
        payload: ByteArray,
        serverIp: String,
        policy: DnsSocketPolicy,
        config: DnsTransportConfig
    ): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!policy.isUsable(policy.protectDatagramSocket(socket))) {
                socket.close()
                return null
            }
            socket.soTimeout = config.udpTimeoutMs
            val upstreamAddr = InetAddress.getByName(serverIp)
            val outPacket = DatagramPacket(payload, payload.size, upstreamAddr, 53)
            socket.send(outPacket)

            val recvBuf = ByteArray(4096)
            val inPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(inPacket)
            recvBuf.copyOf(inPacket.length)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }
}

/**
 * Executes upstream DNS transport attempts and validates their answers.
 *
 * Every resolver IP in [DnsResolutionContext.effectiveUdpServers] is tried in order, exactly as
 * before the refactor. Poison validation reuses [DpiBypassEngine.isLikelyForgedDnsResponse].
 */
class DnsUpstreamResolver(
    private val dohClient: DnsDohClient = DefaultDnsDohClient,
    private val tcpClient: DnsTcpQueryClient = SocketDnsTcpClient,
    private val udpClient: DnsUdpQueryClient = DatagramDnsUdpClient
) {

    /** Primary DoH attempt only. */
    fun forwardDoh(
        payload: ByteArray,
        context: DnsResolutionContext
    ): DnsUpstreamResponse? {
        val response = runCatching { dohClient.forward(payload, context.dohUrl) }.getOrNull() ?: return null
        return DnsUpstreamResponse(
            payload = response,
            label = "${context.upstreamTitle} DoH"
        )
    }

    /** Primary DoH, then each backup endpoint that differs from the configured URL. */
    fun forwardDohWithBackup(
        payload: ByteArray,
        context: DnsResolutionContext
    ): DnsUpstreamResponse? {
        forwardDoh(payload, context)?.let { return it }
        return forwardBackupDoh(payload, context)
    }

    /** Backup DoH endpoints only (skips an endpoint identical to the active one). */
    fun forwardBackupDoh(
        payload: ByteArray,
        context: DnsResolutionContext
    ): DnsUpstreamResponse? {
        for ((backupLabel, backupUrl) in context.config.backupDohEndpoints) {
            if (backupUrl.equals(context.dohUrl, ignoreCase = true)) continue
            val response = runCatching { dohClient.forward(payload, backupUrl) }.getOrNull() ?: continue
            return DnsUpstreamResponse(payload = response, label = "$backupLabel DoH")
        }
        return null
    }

    /** DNS over TCP across all configured resolvers. */
    fun forwardTcp(
        payload: ByteArray,
        context: DnsResolutionContext
    ): DnsUpstreamResponse? {
        for (serverIp in context.effectiveUdpServers) {
            val response = runCatching {
                tcpClient.query(payload, serverIp, context.socketPolicy, context.config)
            }.getOrNull() ?: continue
            return DnsUpstreamResponse(
                payload = response,
                label = "${context.upstreamTitle} DoTCP"
            )
        }
        return null
    }

    /** Plain UDP across all configured resolvers. */
    fun forwardUdp(
        payload: ByteArray,
        context: DnsResolutionContext
    ): DnsUpstreamResponse? {
        for (serverIp in context.effectiveUdpServers) {
            val response = runCatching {
                udpClient.query(payload, serverIp, context.socketPolicy, context.config)
            }.getOrNull() ?: continue
            return DnsUpstreamResponse(
                payload = response,
                label = "${context.upstreamTitle} UDP"
            )
        }
        return null
    }

    /**
     * UDP with anti-poison validation. A forged answer is discarded, the poison counter is
     * reported, and resolution escalates to DoH (primary + backups) and then DoTCP.
     */
    fun forwardValidatedUdp(
        payload: ByteArray,
        domain: String,
        context: DnsResolutionContext
    ): DnsUpstreamResponse {
        val udp = forwardUdp(payload, context)
        if (udp?.payload != null && isPoisoned(domain, udp.payload, context.antiDnsPoisonEnabled)) {
            forwardDohWithBackup(payload, context)?.let { doh ->
                return doh.copy(label = "${doh.label} (Anti-Poison)", antiPoisonTrips = 1)
            }
            forwardTcp(payload, context)?.let { tcp ->
                return tcp.copy(antiPoisonTrips = 1)
            }
            return DnsUpstreamResponse(
                payload = null,
                label = "${context.upstreamTitle} Poison Dropped",
                antiPoisonTrips = 1
            )
        }
        return udp ?: DnsUpstreamResponse(
            payload = null,
            label = "${context.upstreamTitle} UDP"
        )
    }

    /** True when anti-poison is enabled and the answer looks forged for [domain]. */
    fun isPoisoned(domain: String, responseBytes: ByteArray, enabled: Boolean): Boolean {
        if (!enabled) return false
        val ips = DnsPacketParser.extractResolvedIpsFromDnsResponse(responseBytes)
        return DpiBypassEngine.isLikelyForgedDnsResponse(domain, ips)
    }
}
