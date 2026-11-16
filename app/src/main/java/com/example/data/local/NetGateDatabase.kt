package com.example.data.local

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
        AppTimerEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NetGateDatabase : RoomDatabase() {
    abstract fun appNetworkRuleDao(): AppNetworkRuleDao
    abstract fun appTrafficUsageDao(): AppTrafficUsageDao
    abstract fun websiteRuleDao(): WebsiteRuleDao
    abstract fun appTimerDao(): AppTimerDao

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

        fun getInstance(context: Context): NetGateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetGateDatabase::class.java,
                    "netgate_rules.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
