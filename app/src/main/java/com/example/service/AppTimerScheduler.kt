package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.local.AppNetworkRuleDao
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.AppTimerDao
import com.example.data.local.NetGateDatabase

object AppTimerScheduler {
    const val ACTION_CHECK_TIMERS = "com.example.service.ACTION_CHECK_APP_TIMERS"
    private const val ALARM_REQUEST_CODE = 7042

    suspend fun processDueTimersWithDaos(
        ruleDao: AppNetworkRuleDao,
        timerDao: AppTimerDao,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> {
        val dueTimers = timerDao.getDueTimersOnce(nowMillis)
        if (dueTimers.isEmpty()) return emptyList()

        val existingMap = ruleDao.getAllRulesOnce().associateBy { it.packageName }
        val ruleUpdates = dueTimers.map { timer ->
            val current = existingMap[timer.packageName]
            AppNetworkRuleEntity(
                packageName = timer.packageName,
                blockWifi = (current?.blockWifi == true) || timer.blockWifiOnExpire,
                blockMobileData = (current?.blockMobileData == true) || timer.blockMobileDataOnExpire,
                updatedAt = nowMillis
            )
        }
        ruleDao.upsertRules(ruleUpdates)
        val expiredPackages = dueTimers.map { it.packageName }
        timerDao.deleteTimers(expiredPackages)
        return expiredPackages
    }

    suspend fun processDueTimers(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> {
        val db = NetGateDatabase.getInstance(context)
        val expired = processDueTimersWithDaos(
            ruleDao = db.appNetworkRuleDao(),
            timerDao = db.appTimerDao(),
            nowMillis = nowMillis
        )
        scheduleNextAlarm(context)
        return expired
    }

    suspend fun scheduleNextAlarm(context: Context) {
        val db = NetGateDatabase.getInstance(context)
        val timers = db.appTimerDao().getAllTimersOnce()
        val earliest = timers.minByOrNull { it.expiresAtMillis }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AppTimerReceiver::class.java).apply {
            action = ACTION_CHECK_TIMERS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (earliest == null) {
            runCatching { alarmManager.cancel(pendingIntent) }
            return
        }

        val triggerAt = earliest.expiresAtMillis.coerceAtLeast(System.currentTimeMillis() + 250L)
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }
}
