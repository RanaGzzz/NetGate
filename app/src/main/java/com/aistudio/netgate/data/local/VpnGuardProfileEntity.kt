package com.aistudio.netgate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardProviderType
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.aistudio.netgate.service.security.KeystoreSecretBox

@Entity(tableName = "vpn_guard_profiles")
data class VpnGuardProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val providerType: String,
    val rawConfig: String,
    val endpointHost: String,
    val interfaceAddresses: String,
    val dnsServers: String,
    val mtu: Int,
    val routingMode: String,
    val targetPackagesCsv: String,
    val createdAt: Long,
    val lastConnectedAt: Long
) {
    fun toModel(): VpnGuardProfile {
        val provider = VpnGuardProviderType.entries.firstOrNull { it.name == providerType }
            ?: VpnGuardProviderType.CUSTOM_WIREGUARD
        val mode = VpnGuardRoutingMode.entries.firstOrNull { it.name == routingMode }
            ?: VpnGuardRoutingMode.WHOLE_SYSTEM
        val packages = targetPackagesCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return VpnGuardProfile(
            id = id,
            name = name,
            providerType = provider,
            // Profiles hold WireGuard private keys / OpenVPN credentials: open them on read and
            // seal them again on write, so the database file never contains usable secrets.
            rawConfig = KeystoreSecretBox.open(rawConfig),
            endpointHost = endpointHost,
            interfaceAddresses = interfaceAddresses,
            dnsServers = dnsServers,
            mtu = mtu,
            routingMode = mode,
            targetPackages = packages,
            createdAt = createdAt,
            lastConnectedAt = lastConnectedAt
        )
    }

    companion object {
        fun fromModel(model: VpnGuardProfile): VpnGuardProfileEntity = VpnGuardProfileEntity(
            id = model.id,
            name = model.name,
            providerType = model.providerType.name,
            rawConfig = KeystoreSecretBox.seal(model.rawConfig),
            endpointHost = model.endpointHost,
            interfaceAddresses = model.interfaceAddresses,
            dnsServers = model.dnsServers,
            mtu = model.mtu,
            routingMode = model.routingMode.name,
            targetPackagesCsv = model.targetPackages.sorted().joinToString(","),
            createdAt = model.createdAt,
            lastConnectedAt = model.lastConnectedAt
        )
    }
}
