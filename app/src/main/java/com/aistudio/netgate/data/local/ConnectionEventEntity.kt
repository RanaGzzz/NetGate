package com.aistudio.netgate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "connection_events",
    indices = [
        Index(value = ["domain", "remoteIp", "appPackage", "actionStatus"], unique = true),
        Index(value = ["lastSeenAt"]),
        Index(value = ["countryCode"])
    ]
)
data class ConnectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val domain: String,
    val remoteIp: String,
    val remotePort: Int,
    val protocol: String,
    val appPackage: String,
    val appName: String,
    val countryCode: String,
    val countryName: String,
    val regionName: String,
    val ispOrOrg: String,
    val bytesSent: Long,
    val bytesReceived: Long,
    val actionStatus: String,
    val riskLevel: String,
    val riskReason: String,
    val hitCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)
