package com.aistudio.netgate.service.hotspot

/**
 * Builds the `proxy.pac` body served by the hotpot proxy listener.
 *
 * Clients that support automatic proxy configuration can point at
 * `http://<hotspot-ip>:<port>/proxy.pac` instead of typing host and port by hand.
 */
object HotspotPacFile {

    const val PATH = "/proxy.pac"

    fun build(proxyHost: String, proxyPort: Int): String {
        val host = proxyHost.trim().ifEmpty { "192.168.43.1" }
        val port = proxyPort.coerceIn(1, 65535)
        return """
            |// NetGate Hotspot Proxy — generated automatically
            |function FindProxyForURL(url, host) {
            |  if (isPlainHostName(host) ||
            |      shExpMatch(host, "*.local") ||
            |      isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0") ||
            |      isInNet(dnsResolve(host), "172.16.0.0", "255.240.0.0") ||
            |      isInNet(dnsResolve(host), "192.168.0.0", "255.255.0.0") ||
            |      isInNet(dnsResolve(host), "127.0.0.0", "255.0.0.0")) {
            |    return "DIRECT";
            |  }
            |  return "PROXY $host:$port; DIRECT";
            |}
        """.trimMargin()
    }
}
