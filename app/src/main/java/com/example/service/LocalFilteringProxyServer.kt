package com.example.service

import com.example.data.model.DpiBypassMode
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class ProxyFilterDecision(
    val allow: Boolean,
    val reason: String,
    val isAdBlock: Boolean = false
)

/**
 * Root-free local HTTP/CONNECT & TLS SNI filtering proxy bound strictly to 127.0.0.1.
 * Evaluates CONNECT hostnames, HTTP Host/URL paths, and TLS ClientHello SNI against
 * NetGate's Website Rules & Ad Filter Engine without breaking TLS certificate pinning,
 * and applies stateless DPI bypass segmentation when enabled.
 */
class LocalFilteringProxyServer(
    private val port: Int,
    private val bindHost: String = "127.0.0.1",
    private val stripTrackingParams: Boolean,
    private val protectSocket: (Socket) -> Boolean,
    private val isClientBlocked: (Socket) -> Boolean = { false },
    private val clientBlockReason: (clientIp: String) -> String? = { null },
    private val evaluateRequest: (host: String, pathAndQuery: String?) -> ProxyFilterDecision,
    private val resolveTargetHost: (host: String) -> String = { it },
    private val upstreamProxyProvider: () -> Pair<String, Int>? = { null },
    private val dpiBypassConfigProvider: () -> DpiBypassConfig = { DpiBypassConfig() },
    private val onDpiBypassApplied: (host: String, strategy: DpiStrategy) -> Unit = { _, _ -> },
    private val onRequestLogged: (host: String, decision: ProxyFilterDecision) -> Unit,
    private val onClientTraffic: (
        clientIp: String,
        host: String,
        bytesSent: Long,
        bytesReceived: Long,
        allowed: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    private val onTrafficRelayed: (
        host: String,
        port: Int,
        protocol: String,
        remoteIp: String?,
        bytesSent: Long,
        bytesReceived: Long,
        decision: ProxyFilterDecision
    ) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var workerExecutor: ExecutorService? = null

    val isRunning: Boolean get() = running.get()

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            val server = ServerSocket(port, 64, InetAddress.getByName(bindHost)).apply {
                reuseAddress = true
            }
            serverSocket = server
            acceptExecutor = Executors.newSingleThreadExecutor()
            workerExecutor = Executors.newCachedThreadPool()

            acceptExecutor?.execute {
                while (running.get() && !server.isClosed) {
                    try {
                        val client = server.accept()
                        workerExecutor?.execute { handleClient(client) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }
            true
        } catch (_: Exception) {
            running.set(false)
            stop()
            false
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { acceptExecutor?.shutdownNow() }
        acceptExecutor = null
        runCatching { workerExecutor?.shutdownNow() }
        workerExecutor = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 15_000
            val clientIn = BufferedInputStream(client.getInputStream(), 8192)
            val clientOut = client.getOutputStream()

            val headerBytes = readHttpHeaderBlock(clientIn)
            if (headerBytes.isEmpty()) {
                runCatching { client.close() }
                return
            }

            val headerText = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty().trim()
            val parts = requestLine.split(' ')
            if (parts.size < 3) {
                runCatching { client.close() }
                return
            }

            val method = parts[0].uppercase()
            val target = parts[1]
            val version = parts[2]
            val clientIp = client.inetAddress?.hostAddress.orEmpty()

            val ipBlockReason = if (clientIp.isNotEmpty()) clientBlockReason(clientIp) else null
            if (isClientBlocked(client) || ipBlockReason != null) {
                val blockedHost = parseAuthority(target, if (method == "CONNECT") 443 else 80).first
                    .ifBlank { target }
                val reason = ipBlockReason ?: "App Firewall"
                val decision = ProxyFilterDecision(allow = false, reason = reason, isAdBlock = false)
                onRequestLogged(blockedHost, decision)
                if (clientIp.isNotEmpty()) {
                    onClientTraffic(clientIp, blockedHost, 0L, 0L, false)
                }
                writeForbiddenResponse(clientOut, decision.reason)
                runCatching { client.close() }
                return
            }

            if (method == "CONNECT") {
                handleHttpsConnectTunnel(
                    client = client,
                    clientIp = clientIp,
                    clientIn = clientIn,
                    clientOut = clientOut,
                    authority = target
                )
            } else {
                handlePlainHttpRequest(
                    client = client,
                    clientIp = clientIp,
                    clientIn = clientIn,
                    clientOut = clientOut,
                    method = method,
                    target = target,
                    version = version,
                    headerLines = lines.drop(1)
                )
            }
        } catch (_: Exception) {
            runCatching { client.close() }
        }
    }

    private fun handleHttpsConnectTunnel(
        client: Socket,
        clientIp: String,
        clientIn: BufferedInputStream,
        clientOut: OutputStream,
        authority: String
    ) {
        val (host, targetPort) = parseAuthority(authority, defaultPort = 443)
        if (host.isEmpty()) {
            writeForbiddenResponse(clientOut, "Invalid target host")
            runCatching { client.close() }
            return
        }

        val initialDecision = evaluateRequest(host, null)
        if (!initialDecision.allow) {
            onRequestLogged(host, initialDecision)
            onTrafficRelayed(host, targetPort, "HTTPS", null, authority.length.toLong(), 0L, initialDecision)
            if (clientIp.isNotEmpty()) {
                onClientTraffic(clientIp, host, authority.length.toLong(), 0L, false)
            }
            writeForbiddenResponse(clientOut, initialDecision.reason)
            runCatching { client.close() }
            return
        }

        val connectHost = resolveTargetHost(host).ifBlank { host }
        val upstreamProxy = upstreamProxyProvider()
        val dpiConfig = dpiBypassConfigProvider()

        var upstream: Socket = openUpstreamConnectSocket(
            connectHost = connectHost,
            targetPort = targetPort,
            upstreamProxy = upstreamProxy,
            dpiConfig = dpiConfig,
            clientOut = clientOut
        ) ?: run {
            runCatching { client.close() }
            return
        }

        try {
            upstream.soTimeout = 0
            client.soTimeout = 0
            client.keepAlive = dpiConfig.idleKeepAliveEnabled

            clientOut.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: NetGate-SNI-Proxy\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            clientOut.flush()

            var remoteIp = upstream.inetAddress?.hostAddress
            var activeHost = host
            var initialBytesSent = 0L
            var initialBytesReceived = 0L

            // Peek first TLS ClientHello packet to verify SNI matches allowed rules & apply DPI reshaping
            clientIn.mark(4096)
            val peekBuf = ByteArray(2048)
            val peekLen = runCatching { clientIn.read(peekBuf) }.getOrDefault(-1)
            if (peekLen > 0) {
                val sniHost = extractTlsSniHostname(peekBuf, peekLen)
                if (!sniHost.isNullOrEmpty() && !sniHost.equals(host, ignoreCase = true)) {
                    val sniDecision = evaluateRequest(sniHost, null)
                    if (!sniDecision.allow) {
                        onRequestLogged(sniHost, sniDecision)
                        onTrafficRelayed(sniHost, targetPort, "HTTPS", remoteIp, peekLen.toLong(), 0L, sniDecision)
                        runCatching { upstream.close() }
                        runCatching { client.close() }
                        return
                    }
                    activeHost = sniHost
                }
                onRequestLogged(activeHost, initialDecision)

                var activeStrategy = DpiBypassEngine.resolveStrategy(activeHost, dpiConfig)
                var handshakeSent = false

                if (dpiConfig.mode == DpiBypassMode.AUTOPILOT && activeStrategy != DpiStrategy.MICRO_CHUNKS) {
                    val probeResult = runCatching {
                        DpiBypassEngine.writeTlsClientHello(
                            output = upstream.getOutputStream(),
                            packet = peekBuf,
                            length = peekLen,
                            strategy = activeStrategy,
                            chunkSize = dpiConfig.chunkSize,
                            fragmentDelayMs = dpiConfig.fragmentDelayMs
                        )
                        upstream.soTimeout = 3_500
                        val serverProbe = ByteArray(4096)
                        val probeLen = upstream.getInputStream().read(serverProbe)
                        upstream.soTimeout = 0
                        if (probeLen > 0) {
                            clientOut.write(serverProbe, 0, probeLen)
                            clientOut.flush()
                            initialBytesReceived = probeLen.toLong()
                            true
                        } else {
                            false
                        }
                    }.getOrDefault(false)

                    if (probeResult) {
                        handshakeSent = true
                        DpiBypassEngine.recordSuccess(activeHost, activeStrategy)
                        onDpiBypassApplied(activeHost, activeStrategy)
                    } else {
                        // Stateless DPI RST/EOF detected; escalate strategy & reconnect cleanly
                        runCatching { upstream.close() }
                        val escalated = DpiBypassEngine.escalateOnFailure(activeHost, activeStrategy)
                            ?: DpiStrategy.MICRO_CHUNKS
                        activeStrategy = escalated
                        val retrySocket = openUpstreamConnectSocket(
                            connectHost = connectHost,
                            targetPort = targetPort,
                            upstreamProxy = upstreamProxy,
                            dpiConfig = dpiConfig,
                            clientOut = null
                        )
                        if (retrySocket == null) {
                            runCatching { client.close() }
                            return
                        }
                        upstream = retrySocket
                        upstream.soTimeout = 0
                        remoteIp = upstream.inetAddress?.hostAddress
                    }
                }

                if (!handshakeSent) {
                    DpiBypassEngine.writeTlsClientHello(
                        output = upstream.getOutputStream(),
                        packet = peekBuf,
                        length = peekLen,
                        strategy = activeStrategy,
                        chunkSize = dpiConfig.chunkSize,
                        fragmentDelayMs = dpiConfig.fragmentDelayMs
                    )
                    if (activeStrategy != DpiStrategy.DIRECT) {
                        DpiBypassEngine.recordSuccess(activeHost, activeStrategy)
                        onDpiBypassApplied(activeHost, activeStrategy)
                    }
                }
                initialBytesSent = peekLen.toLong()
            } else {
                onRequestLogged(host, initialDecision)
            }

            relayBidirectional(
                client = client,
                clientIp = clientIp,
                clientIn = clientIn,
                clientOut = clientOut,
                upstream = upstream,
                host = activeHost,
                port = targetPort,
                protocol = "HTTPS",
                remoteIp = remoteIp,
                initialBytesSent = initialBytesSent,
                initialBytesReceived = initialBytesReceived,
                decision = initialDecision
            )
        } catch (_: Exception) {
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun openUpstreamConnectSocket(
        connectHost: String,
        targetPort: Int,
        upstreamProxy: Pair<String, Int>?,
        dpiConfig: DpiBypassConfig,
        clientOut: OutputStream?
    ): Socket? {
        val upstream = Socket()
        return try {
            if (!protectSocket(upstream)) {
                runCatching { upstream.close() }
                return null
            }
            upstream.tcpNoDelay = dpiConfig.mode != DpiBypassMode.OFF
            upstream.keepAlive = dpiConfig.idleKeepAliveEnabled
            if (upstreamProxy != null) {
                val (proxyHost, proxyPort) = upstreamProxy
                upstream.connect(InetSocketAddress(proxyHost, proxyPort), 8_000)
                val upstreamOut = upstream.getOutputStream()
                val connectReq = "CONNECT $connectHost:$targetPort HTTP/1.1\r\n" +
                    "Host: $connectHost:$targetPort\r\n" +
                    "Proxy-Connection: keep-alive\r\n\r\n"
                upstreamOut.write(connectReq.toByteArray(Charsets.ISO_8859_1))
                upstreamOut.flush()

                val proxyRespBytes = readHttpHeaderBlock(upstream.getInputStream())
                val statusLine = String(proxyRespBytes, Charsets.ISO_8859_1)
                    .substringBefore("\r\n")
                if (!statusLine.contains(" 200")) {
                    if (clientOut != null) {
                        writeForbiddenResponse(clientOut, "Upstream SNI proxy rejected CONNECT")
                    }
                    runCatching { upstream.close() }
                    return null
                }
            } else {
                upstream.connect(InetSocketAddress(connectHost, targetPort), 8_000)
            }
            upstream
        } catch (_: Exception) {
            runCatching { upstream.close() }
            null
        }
    }

    private fun handlePlainHttpRequest(
        client: Socket,
        clientIp: String,
        clientIn: BufferedInputStream,
        clientOut: OutputStream,
        method: String,
        target: String,
        version: String,
        headerLines: List<String>
    ) {
        val hostHeader = headerLines.firstOrNull {
            it.startsWith("Host:", ignoreCase = true)
        }?.substringAfter(':')?.trim().orEmpty()

        val cleanedTarget: String
        val authority: String
        val pathAndQuery: String

        if (target.startsWith("http://", ignoreCase = true)) {
            val withoutScheme = target.substring(7)
            val slashIdx = withoutScheme.indexOf('/')
            authority = if (slashIdx >= 0) withoutScheme.substring(0, slashIdx) else withoutScheme
            val rawPath = if (slashIdx >= 0) withoutScheme.substring(slashIdx) else "/"
            pathAndQuery = if (stripTrackingParams) {
                AdBlockFilterEngine.stripTrackingQueryParams(rawPath)
            } else {
                rawPath
            }
            cleanedTarget = pathAndQuery
        } else {
            authority = hostHeader
            pathAndQuery = if (stripTrackingParams) {
                AdBlockFilterEngine.stripTrackingQueryParams(target)
            } else {
                target
            }
            cleanedTarget = pathAndQuery
        }

        val (host, targetPort) = parseAuthority(authority, defaultPort = 80)
        if (host.isEmpty()) {
            writeForbiddenResponse(clientOut, "Missing Host header")
            runCatching { client.close() }
            return
        }

        val decision = evaluateRequest(host, pathAndQuery)
        onRequestLogged(host, decision)
        if (!decision.allow) {
            onTrafficRelayed(host, targetPort, "HTTP", null, cleanedTarget.length.toLong(), 0L, decision)
            if (clientIp.isNotEmpty()) {
                onClientTraffic(clientIp, host, cleanedTarget.length.toLong(), 0L, false)
            }
            writeForbiddenResponse(clientOut, decision.reason)
            runCatching { client.close() }
            return
        }

        val connectHost = resolveTargetHost(host).ifBlank { host }
        val dpiConfig = dpiBypassConfigProvider()
        val strategy = DpiBypassEngine.resolveStrategy(host, dpiConfig)
        val upstream = Socket()
        try {
            if (!protectSocket(upstream)) {
                runCatching { upstream.close() }
                runCatching { client.close() }
                return
            }
            upstream.tcpNoDelay = dpiConfig.mode != DpiBypassMode.OFF
            upstream.keepAlive = dpiConfig.idleKeepAliveEnabled
            client.keepAlive = dpiConfig.idleKeepAliveEnabled
            upstream.connect(InetSocketAddress(connectHost, targetPort), 8_000)
            upstream.soTimeout = 0
            client.soTimeout = 0

            val rebuiltHeaders = StringBuilder()
            rebuiltHeaders.append("$method $cleanedTarget $version\r\n")
            var hasHost = false
            for (line in headerLines) {
                if (line.isBlank()) continue
                if (line.startsWith("Proxy-Connection:", ignoreCase = true)) continue
                if (line.startsWith("Host:", ignoreCase = true)) hasHost = true
                rebuiltHeaders.append(line).append("\r\n")
            }
            if (!hasHost) {
                rebuiltHeaders.append("Host: $authority\r\n")
            }
            rebuiltHeaders.append("\r\n")

            val finalHeaderText = DpiBypassEngine.transformPlainHttpHeaders(rebuiltHeaders.toString(), strategy)
            val headerOutBytes = finalHeaderText.toByteArray(Charsets.ISO_8859_1)
            val upstreamOut = upstream.getOutputStream()
            DpiBypassEngine.writeHttpRequest(
                output = upstreamOut,
                headerBytes = headerOutBytes,
                strategy = strategy,
                chunkSize = dpiConfig.chunkSize,
                fragmentDelayMs = dpiConfig.fragmentDelayMs
            )
            if (strategy != DpiStrategy.DIRECT) {
                DpiBypassEngine.recordSuccess(host, strategy)
                onDpiBypassApplied(host, strategy)
            }

            relayBidirectional(
                client = client,
                clientIp = clientIp,
                clientIn = clientIn,
                clientOut = clientOut,
                upstream = upstream,
                host = host,
                port = targetPort,
                protocol = "HTTP",
                remoteIp = upstream.inetAddress?.hostAddress,
                initialBytesSent = headerOutBytes.size.toLong(),
                initialBytesReceived = 0L,
                decision = decision
            )
        } catch (_: Exception) {
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun relayBidirectional(
        client: Socket,
        clientIp: String,
        clientIn: InputStream,
        clientOut: OutputStream,
        upstream: Socket,
        host: String,
        port: Int,
        protocol: String,
        remoteIp: String?,
        initialBytesSent: Long,
        initialBytesReceived: Long,
        decision: ProxyFilterDecision
    ) {
        val upstreamIn = upstream.getInputStream()
        val upstreamOut = upstream.getOutputStream()
        val sentCounter = java.util.concurrent.atomic.AtomicLong(initialBytesSent)
        val recvCounter = java.util.concurrent.atomic.AtomicLong(initialBytesReceived)

        // Emit initial connection registration immediately for live UI visibility
        if (initialBytesSent > 0L || initialBytesReceived > 0L) {
            val initialFlushSent = sentCounter.getAndSet(0L)
            val initialFlushRecv = recvCounter.getAndSet(0L)
            if (initialFlushSent > 0L || initialFlushRecv > 0L) {
                onTrafficRelayed(host, port, protocol, remoteIp, initialFlushSent, initialFlushRecv, decision)
                if (clientIp.isNotEmpty()) {
                    onClientTraffic(clientIp, host, initialFlushSent, initialFlushRecv, true)
                }
            }
        }

        val c2u = workerExecutor?.submit {
            copyStreamWithProgress(clientIn, upstreamOut) { chunkBytes ->
                val pending = sentCounter.addAndGet(chunkBytes)
                if (pending >= 32_768L) {
                    val flushed = sentCounter.getAndSet(0L)
                    if (flushed > 0L) {
                        onTrafficRelayed(host, port, protocol, remoteIp, flushed, 0L, decision)
                        if (clientIp.isNotEmpty()) {
                            onClientTraffic(clientIp, host, flushed, 0L, true)
                        }
                    }
                }
            }
            runCatching { upstream.shutdownOutput() }
        }
        try {
            copyStreamWithProgress(upstreamIn, clientOut) { chunkBytes ->
                val pending = recvCounter.addAndGet(chunkBytes)
                if (pending >= 32_768L) {
                    val flushed = recvCounter.getAndSet(0L)
                    if (flushed > 0L) {
                        onTrafficRelayed(host, port, protocol, remoteIp, 0L, flushed, decision)
                        if (clientIp.isNotEmpty()) {
                            onClientTraffic(clientIp, host, 0L, flushed, true)
                        }
                    }
                }
            }
            runCatching { client.shutdownOutput() }
        } finally {
            runCatching { c2u?.get() }
            val remSent = sentCounter.getAndSet(0L)
            val remRecv = recvCounter.getAndSet(0L)
            if (remSent > 0L || remRecv > 0L) {
                onTrafficRelayed(host, port, protocol, remoteIp, remSent, remRecv, decision)
                if (clientIp.isNotEmpty()) {
                    onClientTraffic(clientIp, host, remSent, remRecv, true)
                }
            }
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        onBytes: (Long) -> Unit
    ) {
        val buf = ByteArray(16_384)
        try {
            while (running.get()) {
                val read = input.read(buf)
                if (read < 0) break
                output.write(buf, 0, read)
                output.flush()
                if (read > 0) {
                    onBytes(read.toLong())
                }
            }
        } catch (_: Exception) {
            // Stream closed normally
        }
    }

    private fun readHttpHeaderBlock(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream(1024)
        var prev3 = 0
        var prev2 = 0
        var prev1 = 0
        var count = 0
        while (count < 16_384) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            count++
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                break
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        return out.toByteArray()
    }

    private fun writeForbiddenResponse(out: OutputStream, reason: String) {
        val body = "Blocked by NetGate HTTPS Filtering Proxy ($reason)"
        val resp = "HTTP/1.1 403 Forbidden\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n" +
            "Connection: close\r\n\r\n$body"
        runCatching {
            out.write(resp.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    companion object {
        fun parseAuthority(authority: String, defaultPort: Int): Pair<String, Int> {
            val trimmed = authority.trim()
            if (trimmed.isEmpty()) return "" to defaultPort
            if (trimmed.startsWith("[")) {
                val endBracket = trimmed.indexOf(']')
                if (endBracket > 0) {
                    val host = trimmed.substring(1, endBracket)
                    val portPart = trimmed.substring(endBracket + 1).removePrefix(":")
                    val port = portPart.toIntOrNull() ?: defaultPort
                    return host to port
                }
            }
            val colonIdx = trimmed.lastIndexOf(':')
            return if (colonIdx > 0) {
                val host = trimmed.substring(0, colonIdx).trim()
                val port = trimmed.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
                host to port
            } else {
                trimmed to defaultPort
            }
        }

        /**
         * Parses an unencrypted TLS ClientHello packet (record type 0x16, handshake type 0x01)
         * to extract the SNI server_name hostname without decrypting or MITM-ing TLS payloads.
         */
        fun extractTlsSniHostname(packet: ByteArray, length: Int): String? {
            if (length < 43) return null
            if ((packet[0].toInt() and 0xFF) != 0x16) return null // TLS Handshake
            if ((packet[5].toInt() and 0xFF) != 0x01) return null // ClientHello

            var pos = 5 + 4 + 2 + 32 // Record(5) + HandshakeHdr(4) + Version(2) + Random(32)
            if (pos >= length) return null

            val sessionIdLen = packet[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos + 2 > length) return null

            val cipherSuitesLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherSuitesLen
            if (pos + 1 > length) return null

            val compressionLen = packet[pos].toInt() and 0xFF
            pos += 1 + compressionLen
            if (pos + 2 > length) return null

            val extensionsLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            pos += 2
            val extensionsEnd = (pos + extensionsLen).coerceAtMost(length)

            while (pos + 4 <= extensionsEnd) {
                val extType = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                val extLen = ((packet[pos + 2].toInt() and 0xFF) shl 8) or (packet[pos + 3].toInt() and 0xFF)
                pos += 4
                if (pos + extLen > extensionsEnd) break

                if (extType == 0x0000 && extLen >= 5) { // server_name extension
                    val sniListLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                    var sniPos = pos + 2
                    val sniEnd = (sniPos + sniListLen).coerceAtMost(pos + extLen)
                    while (sniPos + 3 <= sniEnd) {
                        val nameType = packet[sniPos].toInt() and 0xFF
                        val nameLen = ((packet[sniPos + 1].toInt() and 0xFF) shl 8) or (packet[sniPos + 2].toInt() and 0xFF)
                        sniPos += 3
                        if (sniPos + nameLen > sniEnd) break
                        if (nameType == 0x00 && nameLen in 1..253) {
                            return String(packet, sniPos, nameLen, Charsets.US_ASCII).lowercase()
                        }
                        sniPos += nameLen
                    }
                }
                pos += extLen
            }
            return null
        }
    }
}
