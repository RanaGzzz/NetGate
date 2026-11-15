package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppNetworkRuleEntity::class, AppTrafficUsageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NetGateDatabase : RoomDatabase() {
    abstract fun appNetworkRuleDao(): AppNetworkRuleDao
    abstract fun appTrafficUsageDao(): AppTrafficUsageDao

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

        fun getInstance(context: Context): NetGateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetGateDatabase::class.java,
                    "netgate_rules.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
