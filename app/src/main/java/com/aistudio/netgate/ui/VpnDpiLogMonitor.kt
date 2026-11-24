package com.aistudio.netgate.ui

import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.VpnDpiLogCategory
import com.aistudio.netgate.data.model.VpnDpiLogEntry
import com.aistudio.netgate.data.model.VpnDpiLogSeverity
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.aistudio.netgate.data.model.VpnGuardRuntimeState
import com.aistudio.netgate.service.VpnRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Passive observer that captures live VPN Guard, NetGate Firewall TUN, and DPI Bypass
 * transitions and telemetry deltas from existing runtime StateFlows without altering any
 * VPN, DPI, Hotspot, or Firewall engine internals.
 */
class VpnDpiLogMonitor {

    companion object {
        const val MAX_LOG_ENTRIES = 250
    }

    private val nextId = AtomicLong(1L)
    private val _logs = MutableStateFlow<List<VpnDpiLogEntry>>(emptyList())
    val logs: StateFlow<List<VpnDpiLogEntry>> = _logs.asStateFlow()

    private val seenDnsLogIds = LinkedHashSet<Long>()
    private var previousVpnGuard: VpnGuardRuntimeState? = null
    private var previousVpnRuntime: VpnRuntimeState? = null
    private var previousPrefs: FirewallPreferencesState? = null

    fun startObserving(
        scope: CoroutineScope,
        vpnRuntimeFlow: StateFlow<VpnRuntimeState>,
        vpnGuardRuntimeFlow: StateFlow<VpnGuardRuntimeState>,
        preferencesFlow: kotlinx.coroutines.flow.Flow<FirewallPreferencesState>
    ) {
        scope.launch {
            vpnGuardRuntimeFlow.collect { current ->
                onVpnGuardState(current)
            }
        }
        scope.launch {
            vpnRuntimeFlow.collect { current ->
                onVpnRuntimeState(current)
            }
        }
        scope.launch {
            preferencesFlow.collect { current ->
                onPreferencesState(current)
            }
        }
    }

    internal fun onVpnGuardState(current: VpnGuardRuntimeState) {
        val prev = previousVpnGuard
        previousVpnGuard = current
        if (prev == null) {
            if (current.connectionState != VpnGuardConnectionState.DISCONNECTED) {
                record(
                    category = VpnDpiLogCategory.VPN_GUARD,
                    severity = severityForVpnGuard(current.connectionState),
                    title = "VPN Guard ${current.connectionState.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    detail = buildVpnGuardDetail(current)
                )
            }
            return
        }

        if (prev.connectionState != current.connectionState || prev.activeProfileId != current.activeProfileId) {
            val title = when (current.connectionState) {
                VpnGuardConnectionState.CONNECTING -> "VPN Guard Connecting (${current.activeProfileName})"
                VpnGuardConnectionState.CONNECTED -> "VPN Guard Connected (${current.activeProfileName})"
                VpnGuardConnectionState.DISCONNECTING -> "VPN Guard Disconnecting"
                VpnGuardConnectionState.DISCONNECTED -> "VPN Guard Disconnected"
                VpnGuardConnectionState.ERROR -> "VPN Guard Tunnel Error"
            }
            record(
                category = VpnDpiLogCategory.VPN_GUARD,
                severity = severityForVpnGuard(current.connectionState),
                title = title,
                detail = buildVpnGuardDetail(current)
            )
        } else if (current.errorMessage != null && current.errorMessage != prev.errorMessage) {
            record(
                category = VpnDpiLogCategory.VPN_GUARD,
                severity = VpnDpiLogSeverity.ERROR,
                title = "VPN Guard Error",
                detail = current.errorMessage
            )
        } else if (current.connectedSinceMs > prev.connectedSinceMs && current.connectedSinceMs > 0L) {
            record(
                category = VpnDpiLogCategory.VPN_GUARD,
                severity = VpnDpiLogSeverity.SUCCESS,
                title = "${current.protocol.label} Tunnel Established",
                detail = "${current.activeProfileName} • Endpoint ${current.endpoint.ifBlank { "auto" }}"
            )
        }
    }

    internal fun onVpnRuntimeState(current: VpnRuntimeState) {
        val prev = previousVpnRuntime
        previousVpnRuntime = current

        if (prev == null) {
            if (current.isServiceRunning) {
                record(
                    category = VpnDpiLogCategory.VPN_TUNNEL,
                    severity = VpnDpiLogSeverity.SUCCESS,
                    title = "NetGate Firewall TUN Running",
                    detail = "Network: ${current.activeNetworkType.name} • DPI: ${current.dpiBypassMode.title}"
                )
            }
            seedExistingDnsEvents(current.recentDnsLogs)
            return
        }

        if (prev.isServiceRunning != current.isServiceRunning || prev.isTunnelActive != current.isTunnelActive) {
            val statusLabel = when {
                current.isServiceRunning && current.isTunnelActive -> "Firewall TUN Active"
                current.isServiceRunning -> "Firewall Service Running (TUN Standby)"
                else -> "Firewall TUN Stopped"
            }
            record(
                category = VpnDpiLogCategory.VPN_TUNNEL,
                severity = if (current.isServiceRunning) VpnDpiLogSeverity.SUCCESS else VpnDpiLogSeverity.INFO,
                title = statusLabel,
                detail = "Network: ${current.activeNetworkType.name} • Blocked apps: ${current.blockedActivePackagesCount}"
            )
        }

        if (prev.activeNetworkType != current.activeNetworkType) {
            record(
                category = VpnDpiLogCategory.VPN_TUNNEL,
                severity = VpnDpiLogSeverity.INFO,
                title = "Active Network Changed",
                detail = "${prev.activeNetworkType.name} -> ${current.activeNetworkType.name}"
            )
        }

        if (prev.isHttpsProxyActive != current.isHttpsProxyActive) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = if (current.isHttpsProxyActive) VpnDpiLogSeverity.SUCCESS else VpnDpiLogSeverity.INFO,
                title = if (current.isHttpsProxyActive) "SNI & HTTPS Proxy Active" else "SNI & HTTPS Proxy Stopped",
                detail = "Preset: ${current.activeSniProxyPreset.title}"
            )
        }

        val bypassDelta = current.dpiBypassedConnectionsCount - prev.dpiBypassedConnectionsCount
        if (bypassDelta > 0L) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.SUCCESS,
                title = "DPI Bypass Reshaped +$bypassDelta Flow${if (bypassDelta == 1L) "" else "s"}",
                detail = "Mode: ${current.dpiBypassMode.title} • Session total: ${current.dpiBypassedConnectionsCount}"
            )
        }

        val learnedDelta = current.dpiAutopilotLearnedCount - prev.dpiAutopilotLearnedCount
        if (learnedDelta > 0) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = "Autopilot Strategy Cache Updated",
                detail = "Learned +$learnedDelta host rule(s) • Cache size: ${current.dpiAutopilotLearnedCount}"
            )
        } else if (prev.dpiAutopilotLearnedCount > 0 && current.dpiAutopilotLearnedCount == 0) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.WARNING,
                title = "Autopilot Cache Flushed",
                detail = "Per-host DPI strategy cache cleared (${prev.dpiAutopilotLearnedCount} -> 0)"
            )
        }

        val poisonDelta = current.antiDnsPoisonBlockedCount - prev.antiDnsPoisonBlockedCount
        if (poisonDelta > 0L) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.WARNING,
                title = "Anti-DNS Poisoning Blocked +$poisonDelta Spoof${if (poisonDelta == 1L) "" else "s"}",
                detail = "Forged bogon/loopback DNS response rejected • Total caught: ${current.antiDnsPoisonBlockedCount}"
            )
        }

        val quicDelta = current.quicStrippedCount - prev.quicStrippedCount
        if (quicDelta > 0L) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = "QUIC HTTP/3 Downgraded (+$quicDelta)",
                detail = "Stripped SVCB/HTTPS ALPN hints for TCP SNI bypass • Total: ${current.quicStrippedCount}"
            )
        }

        if (prev.dnsTransportMode != current.dnsTransportMode) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = "Resilient DNS Transport Updated",
                detail = "${prev.dnsTransportMode.title} -> ${current.dnsTransportMode.title}"
            )
        }

        processNewDnsEvents(current.recentDnsLogs)
    }

    internal fun onPreferencesState(current: FirewallPreferencesState) {
        val prev = previousPrefs
        previousPrefs = current
        if (prev == null) return

        if (prev.dpiBypassMode != current.dpiBypassMode) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = if (current.dpiBypassMode == DpiBypassMode.OFF) VpnDpiLogSeverity.INFO else VpnDpiLogSeverity.SUCCESS,
                title = "DPI Bypass Mode: ${current.dpiBypassMode.title}",
                detail = current.dpiBypassMode.subtitle
            )
        }
        if (prev.dpiFragmentChunkSize != current.dpiFragmentChunkSize) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = "TLS Fragment Chunk Size: ${current.dpiFragmentChunkSize}B",
                detail = "Adjusted ClientHello split threshold (${prev.dpiFragmentChunkSize}B -> ${current.dpiFragmentChunkSize}B)"
            )
        }
        if (prev.blockQuicForDpiBypass != current.blockQuicForDpiBypass) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = if (current.blockQuicForDpiBypass) "QUIC Downgrade Enabled" else "QUIC Downgrade Disabled",
                detail = "UDP/443 & SVCB/HTTPS stripping toggled"
            )
        }
        if (prev.antiDnsPoisonEnabled != current.antiDnsPoisonEnabled) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = if (current.antiDnsPoisonEnabled) "Anti-DNS Poisoning Enabled" else "Anti-DNS Poisoning Disabled",
                detail = "Bogus sinkhole/loopback DNS response filter"
            )
        }
        if (prev.sniProxyPreset != current.sniProxyPreset) {
            record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = "SNI Upstream Preset: ${current.sniProxyPreset.title}",
                detail = "Active upstream forwarding mode updated"
            )
        }
    }

    private fun seedExistingDnsEvents(entries: List<DnsQueryLogEntry>) {
        entries.forEach { seenDnsLogIds.add(it.id) }
        entries.asReversed()
            .filter(::isRelevantDpiOrVpnDnsEvent)
            .takeLast(20)
            .forEach { entry ->
                record(
                    category = VpnDpiLogCategory.DPI_BYPASS,
                    severity = if (entry.status == DnsQueryStatus.BLOCKED_HTTPS) VpnDpiLogSeverity.WARNING else VpnDpiLogSeverity.INFO,
                    title = "DPI/DNS: ${entry.domain}",
                    detail = "${entry.status.label} • ${entry.reason}",
                    timestampMs = entry.timestampMs
                )
            }
    }

    private fun processNewDnsEvents(entries: List<DnsQueryLogEntry>) {
        val unseen = entries
            .filter { it.id !in seenDnsLogIds }
            .sortedBy { it.id }

        if (unseen.isEmpty()) return
        unseen.forEach { entry ->
            seenDnsLogIds.add(entry.id)
            if (isRelevantDpiOrVpnDnsEvent(entry)) {
                record(
                    category = VpnDpiLogCategory.DPI_BYPASS,
                    severity = when (entry.status) {
                        DnsQueryStatus.BLOCKED_HTTPS -> VpnDpiLogSeverity.WARNING
                        DnsQueryStatus.SAFE_REWRITE -> VpnDpiLogSeverity.INFO
                        else -> VpnDpiLogSeverity.SUCCESS
                    },
                    title = "DPI/DNS: ${entry.domain}",
                    detail = "${entry.status.label} • ${entry.reason}",
                    timestampMs = entry.timestampMs
                )
            }
        }
        while (seenDnsLogIds.size > 500) {
            val first = seenDnsLogIds.firstOrNull() ?: break
            seenDnsLogIds.remove(first)
        }
    }

    private fun isRelevantDpiOrVpnDnsEvent(entry: DnsQueryLogEntry): Boolean {
        val r = entry.reason.lowercase()
        return r.contains("dpi") ||
            r.contains("quic") ||
            r.contains("poison") ||
            r.contains("sni") ||
            r.contains("dotcp") ||
            r.contains("doh") ||
            r.contains("desync")
    }

    fun record(
        category: VpnDpiLogCategory,
        severity: VpnDpiLogSeverity,
        title: String,
        detail: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val item = VpnDpiLogEntry(
            id = nextId.getAndIncrement(),
            timestampMs = timestampMs,
            category = category,
            severity = severity,
            title = title,
            detail = detail
        )
        _logs.update { current ->
            (listOf(item) + current).take(MAX_LOG_ENTRIES)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun formatForExport(entries: List<VpnDpiLogEntry> = _logs.value): String {
        if (entries.isEmpty()) return "No VPN or DPI Bypass logs recorded yet."
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return entries.joinToString("\n") { entry ->
            val time = formatter.format(Date(entry.timestampMs))
            "[$time] [${entry.category.label}] [${entry.severity.label}] ${entry.title} — ${entry.detail}"
        }
    }

    private fun severityForVpnGuard(state: VpnGuardConnectionState): VpnDpiLogSeverity = when (state) {
        VpnGuardConnectionState.CONNECTED -> VpnDpiLogSeverity.SUCCESS
        VpnGuardConnectionState.ERROR -> VpnDpiLogSeverity.ERROR
        VpnGuardConnectionState.CONNECTING, VpnGuardConnectionState.DISCONNECTING -> VpnDpiLogSeverity.INFO
        VpnGuardConnectionState.DISCONNECTED -> VpnDpiLogSeverity.WARNING
    }

    private fun buildVpnGuardDetail(state: VpnGuardRuntimeState): String {
        if (!state.errorMessage.isNullOrBlank()) {
            return state.errorMessage
        }
        val scope = when (state.routingMode) {
            VpnGuardRoutingMode.WHOLE_SYSTEM -> "Whole System"
            VpnGuardRoutingMode.SELECTED_APPS -> "${state.routedPackagesCount} Selected App(s)"
            VpnGuardRoutingMode.EXCLUDE_APPS -> "All Except ${state.routedPackagesCount} App(s)"
        }
        val parts = buildList {
            if (state.endpoint.isNotBlank()) add("Endpoint ${state.endpoint}")
            if (state.tunnelAddresses.isNotBlank()) add("Tunnel ${state.tunnelAddresses}")
            add("Scope $scope")
            if (state.pausedLocalFirewallForVpn) add("Local FW Paused")
        }
        return parts.joinToString(" • ")
    }
}
