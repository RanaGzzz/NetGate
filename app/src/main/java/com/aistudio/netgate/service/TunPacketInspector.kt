package com.aistudio.netgate.service

import java.net.InetAddress

data class ParsedIpPacket(
    val version: Int,
    val protocol: Int,
    val protocolName: String,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcIpText: String,
    val dstIpText: String,
    val srcPort: Int,
    val dstPort: Int,
    val isTcpSyn: Boolean,
    val totalLength: Int
)

/**
 * Pure IPv4 / IPv6 packet header inspector for packets read from [android.net.VpnService]'s TUN
 * interface descriptor. Enables immediate TCP RST rejection, DNS NXDOMAIN synthesis, and per-app
 * firewall telemetry without corrupting packet framing.
 */
object TunPacketInspector {

    const val IPPROTO_ICMP = 1
    const val IPPROTO_TCP = 6
    const val IPPROTO_UDP = 17
    const val IPPROTO_ICMPV6 = 58

    fun parseIpPacket(packet: ByteArray, length: Int): ParsedIpPacket? {
        if (length < 20 || packet.size < length) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F

        val ipHeaderLen: Int
        val totalLen: Int
        val protocol: Int
        val srcIp: ByteArray
        val dstIp: ByteArray

        when (version) {
            4 -> {
                val ihl = (packet[0].toInt() and 0x0F) * 4
                if (ihl < 20 || ihl > length) return null
                totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                if (totalLen < ihl || totalLen > length) return null
                protocol = packet[9].toInt() and 0xFF
                ipHeaderLen = ihl
                srcIp = packet.copyOfRange(12, 16)
                dstIp = packet.copyOfRange(16, 20)
            }
            6 -> {
                if (length < 40) return null
                ipHeaderLen = 40
                val payloadLen = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                totalLen = ipHeaderLen + payloadLen
                if (payloadLen < 0 || totalLen > length) return null
                protocol = packet[6].toInt() and 0xFF
                srcIp = packet.copyOfRange(8, 24)
                dstIp = packet.copyOfRange(24, 40)
            }
            else -> return null
        }

        var srcPort = 0
        var dstPort = 0
        var isTcpSyn = false

        when (protocol) {
            IPPROTO_TCP -> {
                if (ipHeaderLen + 14 <= totalLen) {
                    srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or
                        (packet[ipHeaderLen + 1].toInt() and 0xFF)
                    dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                        (packet[ipHeaderLen + 3].toInt() and 0xFF)
                    val tcpFlags = packet[ipHeaderLen + 13].toInt() and 0xFF
                    isTcpSyn = (tcpFlags and 0x02) != 0 && (tcpFlags and 0x04) == 0
                }
            }
            IPPROTO_UDP -> {
                if (ipHeaderLen + 8 <= totalLen) {
                    srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or
                        (packet[ipHeaderLen + 1].toInt() and 0xFF)
                    dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                        (packet[ipHeaderLen + 3].toInt() and 0xFF)
                }
            }
        }

        val protocolName = when (protocol) {
            IPPROTO_TCP -> "TCP"
            IPPROTO_UDP -> "UDP"
            IPPROTO_ICMP -> "ICMP"
            IPPROTO_ICMPV6 -> "ICMPv6"
            else -> "IP-$protocol"
        }

        val srcIpText = runCatching { InetAddress.getByAddress(srcIp).hostAddress }.getOrNull().orEmpty()
        val dstIpText = runCatching { InetAddress.getByAddress(dstIp).hostAddress }.getOrNull().orEmpty()

        return ParsedIpPacket(
            version = version,
            protocol = protocol,
            protocolName = protocolName,
            srcIp = srcIp,
            dstIp = dstIp,
            srcIpText = srcIpText,
            dstIpText = dstIpText,
            srcPort = srcPort,
            dstPort = dstPort,
            isTcpSyn = isTcpSyn,
            totalLength = totalLen
        )
    }
}
