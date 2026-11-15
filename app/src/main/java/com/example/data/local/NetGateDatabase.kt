package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppNetworkRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NetGateDatabase : RoomDatabase() {
    abstract fun appNetworkRuleDao(): AppNetworkRuleDao

    companion object {
        @Volatile
        private var INSTANCE: NetGateDatabase? = null

        fun getInstance(context: Context): NetGateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetGateDatabase::class.java,
                    "netgate_rules.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
