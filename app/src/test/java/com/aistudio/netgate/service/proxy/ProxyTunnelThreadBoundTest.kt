package com.aistudio.netgate.service.proxy

import com.aistudio.netgate.service.LocalFilteringProxyServer
import com.aistudio.netgate.service.ProxyFilterDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Resource bounding for [LocalFilteringProxyServer]'s relay: many tunnels must be served with a
 * bounded number of worker threads, and every worker thread must be gone once the tunnels end and
 * the proxy stops. Before the relay dropped its latch task each tunnel parked a third worker thread
 * that outlived the connection, so these bounds are what keeps a connection burst from pinning
 * threads.
 */
class ProxyTunnelThreadBoundTest {

    private val workerPrefix = "netgate-proxy-worker-"

    private fun liveWorkerThreads(): List<Thread> =
        Thread.getAllStackTraces().keys.filter { it.isAlive && it.name.startsWith(workerPrefix) }

    @Test
    fun `many tunnels are relayed and every worker thread is released`() {
        val existingWorkers = liveWorkerThreads().toSet()
        val upstream = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        val served = AtomicInteger(0)
        val upstreamThread = thread(isDaemon = true) {
            while (!upstream.isClosed) {
                val conn = runCatching { upstream.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    conn.use { socket ->
                        served.incrementAndGet()
                        val buf = ByteArray(256)
                        while (true) {
                            val read = runCatching { socket.getInputStream().read(buf) }.getOrDefault(-1)
                            if (read <= 0) break
                            val out = socket.getOutputStream()
                            runCatching {
                                out.write("OK".toByteArray(Charsets.ISO_8859_1))
                                out.flush()
                            }.onFailure { break }
                        }
                    }
                }
            }
        }

        val proxy = LocalFilteringProxyServer(
            port = 0,
            stripTrackingParams = false,
            protectSocket = { true },
            evaluateRequest = { _, _ -> ProxyFilterDecision(allow = true, reason = "allowed") },
            resolveTargetAddresses = { listOf(InetAddress.getByName("127.0.0.1")) },
            onRequestLogged = { _, _ -> }
        )
        assertTrue("proxy must bind to an ephemeral loopback port", proxy.start())

        val tunnelCount = 24
        val clients = ArrayList<Socket>()
        try {
            repeat(tunnelCount) {
                val client = Socket("127.0.0.1", proxy.boundPort).apply { soTimeout = 5_000 }
                client.getOutputStream().write(
                    "CONNECT 127.0.0.1:${upstream.localPort} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                        .toByteArray(Charsets.ISO_8859_1)
                )
                client.getOutputStream().flush()

                val headerBuf = ByteArray(256)
                val headerLen = client.getInputStream().read(headerBuf)
                assertTrue(
                    "expected a 200 CONNECT reply, got ${String(headerBuf, 0, headerLen)}",
                    String(headerBuf, 0, headerLen).contains("200")
                )

                client.getOutputStream().write("ping".toByteArray(Charsets.ISO_8859_1))
                client.getOutputStream().flush()

                val reply = ByteArray(2)
                var got = 0
                while (got < reply.size) {
                    val read = client.getInputStream().read(reply, got, reply.size - got)
                    if (read <= 0) break
                    got += read
                }
                assertEquals("tunnel ${it + 1} must relay both ways", 2, got)
                clients += client
            }

            assertEquals("every tunnel must reach the upstream", tunnelCount, served.get())

            // Two relay threads per tunnel; the old latch design needed three. Client-handler
            // threads exit as soon as the relay is submitted, so a small slack is enough.
            val live = liveWorkerThreads().count { it !in existingWorkers }
            assertTrue(
                "expected at most ${tunnelCount * 2 + 8} worker threads for $tunnelCount tunnels, saw $live",
                live <= tunnelCount * 2 + 8
            )
        } finally {
            clients.forEach { runCatching { it.close() } }
            proxy.stop()
            runCatching { upstream.close() }
            upstreamThread.join(2_000)
        }

        val deadline = System.currentTimeMillis() + 5_000
        var leaked = liveWorkerThreads().filter { it !in existingWorkers }
        while (leaked.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            leaked = liveWorkerThreads().filter { it !in existingWorkers }
        }
        assertTrue(
            "worker threads outlived the tunnels: ${leaked.map { it.name }}",
            leaked.isEmpty()
        )
    }
}
