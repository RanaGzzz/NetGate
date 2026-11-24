package com.aistudio.netgate.data.model

enum class VpnDpiLogCategory(val label: String) {
    ALL("All"),
    VPN_GUARD("VPN Guard"),
    DPI_BYPASS("DPI Bypass"),
    VPN_TUNNEL("Firewall TUN")
}

enum class VpnDpiLogSeverity(val label: String) {
    INFO("INFO"),
    SUCCESS("OK"),
    WARNING("WARN"),
    ERROR("ERR")
}

data class VpnDpiLogEntry(
    val id: Long,
    val timestampMs: Long,
    val category: VpnDpiLogCategory,
    val severity: VpnDpiLogSeverity,
    val title: String,
    val detail: String
)
