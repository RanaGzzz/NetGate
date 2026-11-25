package com.aistudio.netgate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppNetworkRuleEntity::class,
        AppTrafficUsageEntity::class,
        WebsiteRuleEntity::class,
        AppTrafficHistoryEntity::class,
        AppTimerEntity::class,
        AdFilterListEntity::class,
        ConnectionEventEntity::class,
        VpnGuardProfileEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class NetGateDatabase : RoomDatabase() {
    abstract fun appNetworkRuleDao(): AppNetworkRuleDao
    abstract fun appTrafficUsageDao(): AppTrafficUsageDao
    abstract fun websiteRuleDao(): WebsiteRuleDao
    abstract fun appTimerDao(): AppTimerDao
    abstract fun adFilterListDao(): AdFilterListDao
    abstract fun connectionHistoryDao(): ConnectionHistoryDao
    abstract fun vpnGuardProfileDao(): VpnGuardProfileDao

    companion object {
        @Volatile
        private var INSTANCE: NetGateDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_traffic_usage` (
                        `packageName` TEXT NOT NULL,
                        `wifiBytes` INTEGER NOT NULL,
                        `mobileBytes` INTEGER NOT NULL,
                        `periodDays` INTEGER NOT NULL,
                        `syncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `website_rules` (
                        `domain` TEXT NOT NULL,
                        `ruleType` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`domain`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_traffic_usage` ADD COLUMN `wifiRxBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_traffic_usage` ADD COLUMN `wifiTxBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_traffic_usage` ADD COLUMN `mobileRxBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_traffic_usage` ADD COLUMN `mobileTxBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_traffic_history` (
                        `dateEpochDay` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `wifiRxBytes` INTEGER NOT NULL,
                        `wifiTxBytes` INTEGER NOT NULL,
                        `mobileRxBytes` INTEGER NOT NULL,
                        `mobileTxBytes` INTEGER NOT NULL,
                        `recordedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateEpochDay`, `packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_network_timers` (
                        `packageName` TEXT NOT NULL,
                        `blockWifiOnExpire` INTEGER NOT NULL,
                        `blockMobileDataOnExpire` INTEGER NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `expiresAtMillis` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ad_filter_lists` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `domainCount` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `connection_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `remoteIp` TEXT NOT NULL,
                        `remotePort` INTEGER NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `appPackage` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `countryCode` TEXT NOT NULL,
                        `countryName` TEXT NOT NULL,
                        `regionName` TEXT NOT NULL,
                        `ispOrOrg` TEXT NOT NULL,
                        `bytesSent` INTEGER NOT NULL,
                        `bytesReceived` INTEGER NOT NULL,
                        `actionStatus` TEXT NOT NULL,
                        `riskLevel` TEXT NOT NULL,
                        `riskReason` TEXT NOT NULL,
                        `hitCount` INTEGER NOT NULL,
                        `firstSeenAt` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_connection_events_domain_remoteIp_appPackage_actionStatus` " +
                        "ON `connection_events` (`domain`, `remoteIp`, `appPackage`, `actionStatus`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_connection_events_lastSeenAt` ON `connection_events` (`lastSeenAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_connection_events_countryCode` ON `connection_events` (`countryCode`)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vpn_guard_profiles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `providerType` TEXT NOT NULL,
                        `rawConfig` TEXT NOT NULL,
                        `endpointHost` TEXT NOT NULL,
                        `interfaceAddresses` TEXT NOT NULL,
                        `dnsServers` TEXT NOT NULL,
                        `mtu` INTEGER NOT NULL,
                        `routingMode` TEXT NOT NULL,
                        `targetPackagesCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastConnectedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Connection tags for the live App-to-Domain inspector.
                db.execSQL("ALTER TABLE `connection_events` ADD COLUMN `isAdBlocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `connection_events` ADD COLUMN `dpiBypassed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): NetGateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetGateDatabase::class.java,
                    "netgate_rules.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
