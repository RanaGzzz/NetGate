package com.example.service

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
 * NetGate's Website Rules & Ad Filter Engine without breaking TLS certificate pinning.
 */
class LocalFilteringProxyServer(
    private val port: Int,
    private val stripTrackingParams: Boolean,
    private val protectSocket: (Socket) -> Boolean,
    private val evaluateRequest: (host: String, pathAndQuery: String?) -> ProxyFilterDecision,
    private val resolveTargetHost: (host: String) -> String = { it },
    private val upstreamProxyProvider: () -> Pair<String, Int>? = { null },
    private val onRequestLogged: (host: String, decision: ProxyFilterDecision) -> Unit,
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
            val server = ServerSocket(port, 64, InetAddress.getByName("127.0.0.1")).apply {
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

            if (method == "CONNECT") {
                handleHttpsConnectTunnel(
                    client = client,
                    clientIn = clientIn,
                    clientOut = clientOut,
                    authority = target
                )
            } else {
                handlePlainHttpRequest(
                    client = client,
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
            writeForbiddenResponse(clientOut, initialDecision.reason)
            runCatching { client.close() }
            return
        }

        val connectHost = resolveTargetHost(host).ifBlank { host }
        val upstreamProxy = upstreamProxyProvider()

        val upstream = Socket()
        try {
            if (!protectSocket(upstream)) {
                runCatching { upstream.close() }
                runCatching { client.close() }
                return
            }
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
                    writeForbiddenResponse(clientOut, "Upstream SNI proxy rejected CONNECT")
                    runCatching { upstream.close() }
                    runCatching { client.close() }
                    return
                }
            } else {
                upstream.connect(InetSocketAddress(connectHost, targetPort), 8_000)
            }
            upstream.soTimeout = 0
            client.soTimeout = 0

            clientOut.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: NetGate-SNI-Proxy\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            clientOut.flush()

            val remoteIp = upstream.inetAddress?.hostAddress
            var activeHost = host
            var initialBytesSent = 0L

            // Peek first TLS ClientHello packet to verify SNI matches allowed rules
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
                upstream.getOutputStream().write(peekBuf, 0, peekLen)
                upstream.getOutputStream().flush()
                initialBytesSent = peekLen.toLong()
            } else {
                onRequestLogged(host, initialDecision)
            }

            relayBidirectional(
                client = client,
                clientIn = clientIn,
                clientOut = clientOut,
                upstream = upstream,
                host = activeHost,
                port = targetPort,
                protocol = "HTTPS",
                remoteIp = remoteIp,
                initialBytesSent = initialBytesSent,
                decision = initialDecision
            )
        } catch (_: Exception) {
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun handlePlainHttpRequest(
        client: Socket,
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
            writeForbiddenResponse(clientOut, decision.reason)
            runCatching { client.close() }
            return
        }

        val connectHost = resolveTargetHost(host).ifBlank { host }
        val upstream = Socket()
        try {
            if (!protectSocket(upstream)) {
                runCatching { upstream.close() }
                runCatching { client.close() }
                return
            }
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

            val headerOutBytes = rebuiltHeaders.toString().toByteArray(Charsets.ISO_8859_1)
            val upstreamOut = upstream.getOutputStream()
            upstreamOut.write(headerOutBytes)
            upstreamOut.flush()

            relayBidirectional(
                client = client,
                clientIn = clientIn,
                clientOut = clientOut,
                upstream = upstream,
                host = host,
                port = targetPort,
                protocol = "HTTP",
                remoteIp = upstream.inetAddress?.hostAddress,
                initialBytesSent = headerOutBytes.size.toLong(),
                decision = decision
            )
        } catch (_: Exception) {
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun relayBidirectional(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        upstream: Socket,
        host: String,
        port: Int,
        protocol: String,
        remoteIp: String?,
        initialBytesSent: Long,
        decision: ProxyFilterDecision
    ) {
        val upstreamIn = upstream.getInputStream()
        val upstreamOut = upstream.getOutputStream()
        val sentCounter = java.util.concurrent.atomic.AtomicLong(initialBytesSent)
        val recvCounter = java.util.concurrent.atomic.AtomicLong(0L)

        // Emit initial connection registration immediately for live UI visibility
        if (initialBytesSent > 0L) {
            val initialFlush = sentCounter.getAndSet(0L)
            if (initialFlush > 0L) {
                onTrafficRelayed(host, port, protocol, remoteIp, initialFlush, 0L, decision)
            }
        }

        val c2u = workerExecutor?.submit {
            copyStreamWithProgress(clientIn, upstreamOut) { chunkBytes ->
                val pending = sentCounter.addAndGet(chunkBytes)
                if (pending >= 32_768L) {
                    val flushed = sentCounter.getAndSet(0L)
                    if (flushed > 0L) {
                        onTrafficRelayed(host, port, protocol, remoteIp, flushed, 0L, decision)
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
