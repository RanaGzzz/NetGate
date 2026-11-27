package com.aistudio.netgate.service

import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.data.model.DpiBypassMode
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class ProxyFilterDecision(
    val allow: Boolean,
    val reason: String,
    val isAdBlock: Boolean = false,
    val filterListName: String = "",
    val blockReason: AdBlockReason = AdBlockReason.NONE,
    val fullUrl: String = "",
    val latencyMicros: Long = 0L,
    val appPackage: String = "",
    val appName: String = ""
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
    /** Optional per-client policy hook (hotspot per-device profiles). */
    private val evaluateRequestForClient:
        ((clientIp: String, host: String, pathAndQuery: String?) -> ProxyFilterDecision)? = null,
    /** Per-client bandwidth cap in bytes/second; 0 means unlimited. */
    private val clientThrottleBytesPerSec: (clientIp: String) -> Long = { 0L },
    /** Serves the hotspot proxy auto-config file when the client asks for /proxy.pac. */
    private val pacFileProvider: ((localAddress: String) -> String)? = null,
    private val resolveTargetHost: (host: String) -> String = { it },
    private val resolveTargetAddresses: (host: String) -> List<InetAddress> = { host ->
        defaultResolveAddressesIpv4First(host)
    },
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
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    private val resolveClientApp: ((Socket) -> Pair<String, String>?)? = null
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var workerExecutor: ExecutorService? = null

    /** Client sockets of in-flight connections, closed on stop so worker threads cannot linger. */
    private val activeClients: MutableSet<Socket> =
        Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    @Volatile
    var boundPort: Int = port
        private set

    val isRunning: Boolean get() = running.get()

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            val bindAddr = InetAddress.getByName(bindHost)
            val server = ServerSocket().apply {
                reuseAddress = true
            }
            try {
                server.bind(InetSocketAddress(bindAddr, port), 128)
            } catch (bindErr: Exception) {
                if (port != 0 && bindHost == "127.0.0.1") {
                    // Fallback to an ephemeral loopback port if the default port is held in TIME_WAIT
                    server.bind(InetSocketAddress(bindAddr, 0), 128)
                } else {
                    throw bindErr
                }
            }
            boundPort = server.localPort
            serverSocket = server
            acceptExecutor = Executors.newSingleThreadExecutor()
            workerExecutor = Executors.newCachedThreadPool()

            acceptExecutor?.execute {
                while (running.get() && !server.isClosed) {
                    try {
                        val client = server.accept()
                        val worker = workerExecutor
                        if (worker == null) {
                            runCatching { client.close() }
                        } else {
                            activeClients.add(client)
                            val submitted = runCatching { worker.execute { handleClient(client) } }.isSuccess
                            if (!submitted) {
                                activeClients.remove(client)
                                runCatching { client.close() }
                            }
                        }
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
        boundPort = port

        // Close every in-flight client so relay loops blocked in read() return immediately.
        for (client in activeClients.toList()) {
            runCatching { client.close() }
        }
        activeClients.clear()

        val accept = acceptExecutor
        acceptExecutor = null
        val workers = workerExecutor
        workerExecutor = null

        runCatching { accept?.shutdownNow() }
        runCatching { workers?.shutdownNow() }
        runCatching { accept?.awaitTermination(SHUTDOWN_GRACE_MS, TimeUnit.MILLISECONDS) }
        runCatching { workers?.awaitTermination(SHUTDOWN_GRACE_MS, TimeUnit.MILLISECONDS) }
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
        } finally {
            activeClients.remove(client)
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

        val initialDecision = decisionFor(clientIp, host, null)
        val connectFullUrl = "https://$host${if (targetPort != 443) ":$targetPort" else ""}"
        if (!initialDecision.allow) {
            val logged = enrichDecisionForLog(client, initialDecision, connectFullUrl)
            onRequestLogged(host, logged)
            onTrafficRelayed(host, targetPort, "HTTPS", null, authority.length.toLong(), 0L, logged)
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

            // Read complete initial payload (including full multi-KB post-quantum TLS ClientHello)
            val initialPayload = readInitialConnectPayload(clientIn)
            val peekLen = initialPayload.size
            if (peekLen > 0) {
                val isClientHello = isTlsClientHello(initialPayload, peekLen)
                val sniHost = if (isClientHello) extractTlsSniHostname(initialPayload, peekLen) else null
                if (!sniHost.isNullOrEmpty() && !sniHost.equals(host, ignoreCase = true)) {
                    val sniDecision = decisionFor(clientIp, sniHost, null)
                    val sniFullUrl = "https://$sniHost${if (targetPort != 443) ":$targetPort" else ""}"
                    if (!sniDecision.allow) {
                        val logged = enrichDecisionForLog(client, sniDecision, sniFullUrl)
                        onRequestLogged(sniHost, logged)
                        onTrafficRelayed(sniHost, targetPort, "HTTPS", remoteIp, peekLen.toLong(), 0L, logged)
                        if (clientIp.isNotEmpty()) {
                            onClientTraffic(clientIp, sniHost, peekLen.toLong(), 0L, false)
                        }
                        runCatching { upstream.close() }
                        runCatching { client.close() }
                        return
                    }
                    activeHost = sniHost
                }
                val activeUrl = "https://$activeHost${if (targetPort != 443) ":$targetPort" else ""}"
                onRequestLogged(activeHost, enrichDecisionForLog(client, initialDecision, activeUrl))

                if (!isClientHello) {
                    // Non-TLS CONNECT tunnel (e.g. WebSocket/raw TCP): forward intact without splitting
                    upstream.getOutputStream().write(initialPayload, 0, peekLen)
                    upstream.getOutputStream().flush()
                    initialBytesSent = peekLen.toLong()
                } else {
                    var activeStrategy = DpiBypassEngine.resolveStrategy(activeHost, dpiConfig)
                    var handshakeSent = false

                    if (dpiConfig.mode == DpiBypassMode.AUTOPILOT) {
                        val firstResp = writeAndProbeTlsServerHello(
                            upstream = upstream,
                            packet = initialPayload,
                            length = peekLen,
                            strategy = activeStrategy,
                            chunkSize = dpiConfig.chunkSize,
                            fragmentDelayMs = dpiConfig.fragmentDelayMs
                        )

                        if (firstResp != null) {
                            clientOut.write(firstResp)
                            clientOut.flush()
                            initialBytesReceived = firstResp.size.toLong()
                            handshakeSent = true
                            DpiBypassEngine.recordSuccess(activeHost, activeStrategy)
                            onDpiBypassApplied(activeHost, activeStrategy)
                        } else {
                            // Strategy timed out or received DPI RST/EOF: try escalated strategy, then DIRECT fallback
                            runCatching { upstream.close() }
                            val escalated = if (activeStrategy != DpiStrategy.MICRO_CHUNKS) {
                                DpiBypassEngine.escalateOnFailure(activeHost, activeStrategy) ?: DpiStrategy.MICRO_CHUNKS
                            } else {
                                null
                            }

                            var recovered = false
                            if (escalated != null) {
                                val retrySocket = openUpstreamConnectSocket(
                                    connectHost = connectHost,
                                    targetPort = targetPort,
                                    upstreamProxy = upstreamProxy,
                                    dpiConfig = dpiConfig,
                                    clientOut = null
                                )
                                if (retrySocket != null) {
                                    upstream = retrySocket
                                    remoteIp = upstream.inetAddress?.hostAddress
                                    val escalatedResp = writeAndProbeTlsServerHello(
                                        upstream = upstream,
                                        packet = initialPayload,
                                        length = peekLen,
                                        strategy = escalated,
                                        chunkSize = dpiConfig.chunkSize,
                                        fragmentDelayMs = dpiConfig.fragmentDelayMs
                                    )
                                    if (escalatedResp != null) {
                                        clientOut.write(escalatedResp)
                                        clientOut.flush()
                                        initialBytesReceived = escalatedResp.size.toLong()
                                        handshakeSent = true
                                        recovered = true
                                        DpiBypassEngine.recordSuccess(activeHost, escalated)
                                        onDpiBypassApplied(activeHost, escalated)
                                    } else {
                                        runCatching { upstream.close() }
                                    }
                                }
                            }

                            if (!recovered) {
                                // Final DIRECT fallback so strict servers/CDNs that reject TCP splitting still load
                                val directSocket = openUpstreamConnectSocket(
                                    connectHost = connectHost,
                                    targetPort = targetPort,
                                    upstreamProxy = upstreamProxy,
                                    dpiConfig = dpiConfig,
                                    clientOut = null
                                ) ?: run {
                                    runCatching { client.close() }
                                    return
                                }
                                upstream = directSocket
                                upstream.soTimeout = 0
                                remoteIp = upstream.inetAddress?.hostAddress
                                activeStrategy = DpiStrategy.DIRECT
                            }
                        }
                    }

                    if (!handshakeSent) {
                        DpiBypassEngine.writeTlsClientHello(
                            output = upstream.getOutputStream(),
                            packet = initialPayload,
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
                }
            } else {
                onRequestLogged(host, enrichDecisionForLog(client, initialDecision, connectFullUrl))
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

    private fun readInitialConnectPayload(clientIn: BufferedInputStream): ByteArray {
        val firstChunk = ByteArray(4096)
        val firstLen = runCatching { clientIn.read(firstChunk) }.getOrDefault(-1)
        if (firstLen <= 0) return ByteArray(0)

        // If this is a TLS Handshake record (0x16 0x03 ...), assemble the complete ClientHello record
        if (firstLen >= 5 &&
            (firstChunk[0].toInt() and 0xFF) == 0x16 &&
            (firstChunk[1].toInt() and 0xFF) == 0x03
        ) {
            val recordPayloadLen = ((firstChunk[3].toInt() and 0xFF) shl 8) or (firstChunk[4].toInt() and 0xFF)
            val totalExpected = (5 + recordPayloadLen).coerceIn(5, 16_384)
            if (firstLen >= totalExpected) {
                return firstChunk.copyOf(firstLen)
            }
            val assembled = ByteArrayOutputStream(totalExpected)
            assembled.write(firstChunk, 0, firstLen)
            val extraBuf = ByteArray(2048)
            while (assembled.size() < totalExpected) {
                val want = minOf(extraBuf.size, totalExpected - assembled.size())
                val readMore = runCatching { clientIn.read(extraBuf, 0, want) }.getOrDefault(-1)
                if (readMore <= 0) break
                assembled.write(extraBuf, 0, readMore)
            }
            return assembled.toByteArray()
        }

        return firstChunk.copyOf(firstLen)
    }

    private fun isTlsClientHello(packet: ByteArray, length: Int): Boolean {
        return length >= 43 &&
            (packet[0].toInt() and 0xFF) == 0x16 &&
            (packet[5].toInt() and 0xFF) == 0x01
    }

    private fun writeAndProbeTlsServerHello(
        upstream: Socket,
        packet: ByteArray,
        length: Int,
        strategy: DpiStrategy,
        chunkSize: Int,
        fragmentDelayMs: Int
    ): ByteArray? {
        return runCatching {
            DpiBypassEngine.writeTlsClientHello(
                output = upstream.getOutputStream(),
                packet = packet,
                length = length,
                strategy = strategy,
                chunkSize = chunkSize,
                fragmentDelayMs = fragmentDelayMs
            )
            upstream.soTimeout = 1_600
            val serverProbe = ByteArray(4096)
            val probeLen = upstream.getInputStream().read(serverProbe)
            upstream.soTimeout = 0
            if (probeLen > 0) serverProbe.copyOf(probeLen) else null
        }.getOrNull()
    }

    private fun openUpstreamConnectSocket(
        connectHost: String,
        targetPort: Int,
        upstreamProxy: Pair<String, Int>?,
        dpiConfig: DpiBypassConfig,
        clientOut: OutputStream?
    ): Socket? {
        if (upstreamProxy != null) {
            val (proxyHost, proxyPort) = upstreamProxy
            val proxyCandidates = resolveCandidateAddresses(proxyHost)

            for (addr in proxyCandidates) {
                val candidate = Socket()
                if (!protectSocket(candidate)) {
                    runCatching { candidate.close() }
                    continue
                }
                candidate.tcpNoDelay = dpiConfig.mode != DpiBypassMode.OFF
                candidate.keepAlive = dpiConfig.idleKeepAliveEnabled
                val connectedOk = runCatching {
                    candidate.connect(InetSocketAddress(addr, proxyPort), 4_500)
                }.isSuccess
                if (!connectedOk) {
                    runCatching { candidate.close() }
                    continue
                }

                val handshakeOk = runCatching {
                    val upstreamOut = candidate.getOutputStream()
                    val connectReq = "CONNECT $connectHost:$targetPort HTTP/1.1\r\n" +
                        "Host: $connectHost:$targetPort\r\n" +
                        "Proxy-Connection: keep-alive\r\n\r\n"
                    upstreamOut.write(connectReq.toByteArray(Charsets.ISO_8859_1))
                    upstreamOut.flush()

                    val proxyRespBytes = readHttpHeaderBlock(candidate.getInputStream())
                    val statusLine = String(proxyRespBytes, Charsets.ISO_8859_1)
                        .substringBefore("\r\n")
                    if (!statusLine.contains(" 200")) {
                        if (clientOut != null) {
                            writeForbiddenResponse(clientOut, "Upstream SNI proxy rejected CONNECT")
                        }
                        runCatching { candidate.close() }
                        return null
                    }
                    true
                }.getOrDefault(false)

                if (handshakeOk) {
                    return candidate
                }
                runCatching { candidate.close() }
            }

            // Fallback to hostname-based connect if IP candidates were empty or unreachable
            val fallbackProxySocket = Socket()
            return try {
                if (!protectSocket(fallbackProxySocket)) {
                    runCatching { fallbackProxySocket.close() }
                    return null
                }
                fallbackProxySocket.tcpNoDelay = dpiConfig.mode != DpiBypassMode.OFF
                fallbackProxySocket.keepAlive = dpiConfig.idleKeepAliveEnabled
                fallbackProxySocket.connect(InetSocketAddress(proxyHost, proxyPort), 5_500)
                val upstreamOut = fallbackProxySocket.getOutputStream()
                val connectReq = "CONNECT $connectHost:$targetPort HTTP/1.1\r\n" +
                    "Host: $connectHost:$targetPort\r\n" +
                    "Proxy-Connection: keep-alive\r\n\r\n"
                upstreamOut.write(connectReq.toByteArray(Charsets.ISO_8859_1))
                upstreamOut.flush()

                val proxyRespBytes = readHttpHeaderBlock(fallbackProxySocket.getInputStream())
                val statusLine = String(proxyRespBytes, Charsets.ISO_8859_1)
                    .substringBefore("\r\n")
                if (!statusLine.contains(" 200")) {
                    if (clientOut != null) {
                        writeForbiddenResponse(clientOut, "Upstream SNI proxy rejected CONNECT")
                    }
                    runCatching { fallbackProxySocket.close() }
                    return null
                }
                fallbackProxySocket
            } catch (_: Exception) {
                runCatching { fallbackProxySocket.close() }
                null
            }
        }

        val candidates = resolveCandidateAddresses(connectHost)
        for (addr in candidates) {
            val socket = Socket()
            if (!protectSocket(socket)) {
                runCatching { socket.close() }
                continue
            }
            socket.tcpNoDelay = dpiConfig.mode != DpiBypassMode.OFF
            socket.keepAlive = dpiConfig.idleKeepAliveEnabled
            val ok = runCatching {
                socket.connect(InetSocketAddress(addr, targetPort), 4_500)
            }.isSuccess
            if (ok) {
                return socket
            }
            runCatching { socket.close() }
        }

        // Last-resort direct hostname connect if custom resolution returned nothing
        val fallback = Socket()
        return try {
            if (!protectSocket(fallback)) {
                runCatching { fallback.close() }
                return null
            }
            fallback.tcpNoDelay = dpiConfig.mode != DpiBypassMode.OFF
            fallback.keepAlive = dpiConfig.idleKeepAliveEnabled
            fallback.connect(InetSocketAddress(connectHost, targetPort), 5_500)
            fallback
        } catch (_: Exception) {
            runCatching { fallback.close() }
            null
        }
    }

    private fun decisionFor(
        clientIp: String,
        host: String,
        pathAndQuery: String?
    ): ProxyFilterDecision =
        evaluateRequestForClient?.invoke(clientIp, host, pathAndQuery)
            ?: evaluateRequest(host, pathAndQuery)

    private fun enrichDecisionForLog(
        client: Socket,
        decision: ProxyFilterDecision,
        fullUrl: String
    ): ProxyFilterDecision {
        val owner = runCatching { resolveClientApp?.invoke(client) }.getOrNull()
        return decision.copy(
            fullUrl = fullUrl,
            appPackage = owner?.first ?: decision.appPackage,
            appName = owner?.second ?: decision.appName
        )
    }

    private fun writePacResponse(out: OutputStream, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/x-ns-proxy-autoconfig\r\n" +
            "Cache-Control: no-store\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            out.write(header.toByteArray(Charsets.ISO_8859_1))
            out.write(bodyBytes)
            out.flush()
        }
    }

    private fun resolveCandidateAddresses(host: String): List<InetAddress> {
        val fromProvider = runCatching { resolveTargetAddresses(host) }.getOrNull().orEmpty()
        if (fromProvider.isNotEmpty()) {
            return sortIpv4First(fromProvider)
        }
        return defaultResolveAddressesIpv4First(host)
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

        val pacProvider = pacFileProvider
        if (pacProvider != null && method.equals("GET", ignoreCase = true) &&
            target.substringBefore('?').trimEnd('/').endsWith("/proxy.pac")
        ) {
            val localIp = client.localAddress?.hostAddress.orEmpty()
            writePacResponse(clientOut, pacProvider.invoke(localIp))
            runCatching { client.close() }
            return
        }

        val decision = decisionFor(clientIp, host, pathAndQuery)
        val fullUrl = "http://$host${if (targetPort != 80) ":$targetPort" else ""}$cleanedTarget"
        val enrichedDecision = enrichDecisionForLog(client, decision, fullUrl)
        onRequestLogged(host, enrichedDecision)
        if (!decision.allow) {
            onTrafficRelayed(host, targetPort, "HTTP", null, cleanedTarget.length.toLong(), 0L, enrichedDecision)
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
        val upstream = openUpstreamConnectSocket(
            connectHost = connectHost,
            targetPort = targetPort,
            upstreamProxy = null,
            dpiConfig = dpiConfig,
            clientOut = clientOut
        ) ?: run {
            runCatching { client.close() }
            return
        }
        try {
            upstream.soTimeout = 0
            client.keepAlive = dpiConfig.idleKeepAliveEnabled
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
        val sentCounter = AtomicLong(initialBytesSent)
        val recvCounter = AtomicLong(initialBytesReceived)
        val abortedByPolicy = AtomicBoolean(false)
        val throttleBytesPerSec = runCatching { clientThrottleBytesPerSec(clientIp) }
            .getOrDefault(0L)
            .coerceAtLeast(0L)
        val relayDoneLatch = CountDownLatch(2)

        fun enforceClientQuotaOrBan(): Boolean {
            if (clientIp.isNotEmpty() && clientBlockReason(clientIp) != null) {
                abortedByPolicy.set(true)
                runCatching { upstream.close() }
                runCatching { client.close() }
                return true
            }
            return false
        }

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
        if (enforceClientQuotaOrBan()) return

        // The worker pool is nulled when the proxy stops. Handing the relay to a null executor used
        // to leave both sockets open forever, so fail closed and release them here.
        val executor = workerExecutor
        if (executor == null) {
            runCatching { client.close() }
            runCatching { upstream.close() }
            return
        }

        executor.execute {
            try {
                copyStreamWithProgress(clientIn, upstreamOut, abortedByPolicy, throttleBytesPerSec) { chunkBytes ->
                    val pending = sentCounter.addAndGet(chunkBytes)
                    if (pending >= 32_768L) {
                        val flushed = sentCounter.getAndSet(0L)
                        if (flushed > 0L) {
                            onTrafficRelayed(host, port, protocol, remoteIp, flushed, 0L, decision)
                            if (clientIp.isNotEmpty()) {
                                onClientTraffic(clientIp, host, flushed, 0L, true)
                            }
                        }
                        enforceClientQuotaOrBan()
                    }
                }
                runCatching { upstream.shutdownOutput() }
            } finally {
                relayDoneLatch.countDown()
            }
        }

        executor.execute {
            try {
                copyStreamWithProgress(upstreamIn, clientOut, abortedByPolicy, throttleBytesPerSec) { chunkBytes ->
                    val pending = recvCounter.addAndGet(chunkBytes)
                    if (pending >= 32_768L) {
                        val flushed = recvCounter.getAndSet(0L)
                        if (flushed > 0L) {
                            onTrafficRelayed(host, port, protocol, remoteIp, 0L, flushed, decision)
                            if (clientIp.isNotEmpty()) {
                                onClientTraffic(clientIp, host, 0L, flushed, true)
                            }
                        }
                        enforceClientQuotaOrBan()
                    }
                }
                runCatching { client.shutdownOutput() }
            } finally {
                relayDoneLatch.countDown()
            }
        }

        executor.execute {
            try {
                relayDoneLatch.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
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
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        aborted: AtomicBoolean,
        bytesPerSec: Long = 0L,
        onBytes: (Long) -> Unit
    ) {
        val buf = ByteArray(16_384)
        val startedAt = System.currentTimeMillis()
        var totalBytes = 0L
        try {
            while (running.get() && !aborted.get()) {
                val read = input.read(buf)
                if (read < 0) break
                output.write(buf, 0, read)
                output.flush()
                if (read > 0) {
                    totalBytes += read
                    onBytes(read.toLong())
                    // Token-bucket style pacing for the per-device speed limiter.
                    if (bytesPerSec > 0L) {
                        val expectedMs = (totalBytes * 1000L) / bytesPerSec
                        val elapsedMs = System.currentTimeMillis() - startedAt
                        val sleepMs = (expectedMs - elapsedMs).coerceIn(0L, 250L)
                        if (sleepMs > 0L) {
                            runCatching { Thread.sleep(sleepMs) }
                        }
                    }
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
        /** Upper bound for executor termination on stop; never blocks the caller for long. */
        private const val SHUTDOWN_GRACE_MS = 500L

        fun sortIpv4First(addresses: List<InetAddress>): List<InetAddress> {
            if (addresses.size <= 1) return addresses
            val v4 = addresses.filterIsInstance<Inet4Address>()
            val other = addresses.filter { it !is Inet4Address }
            return (v4 + other).distinct()
        }

        fun defaultResolveAddressesIpv4First(host: String): List<InetAddress> {
            return runCatching {
                sortIpv4First(InetAddress.getAllByName(host).toList())
            }.getOrDefault(emptyList())
        }

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
