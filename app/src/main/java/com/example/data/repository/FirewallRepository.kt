package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.data.local.AppNetworkRuleDao
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.FirewallPreferences
import com.example.data.model.ActiveNetworkType
import com.example.data.model.BatchAction
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FirewallRepository(
    private val context: Context,
    private val ruleDao: AppNetworkRuleDao,
    val preferences: FirewallPreferences
) {
    val rulesFlow: Flow<List<AppNetworkRuleEntity>> = ruleDao.observeAllRules()
    val preferencesFlow: Flow<FirewallPreferencesState> = preferences.preferencesFlow

    suspend fun getAllRulesOnce(): List<AppNetworkRuleEntity> = withContext(Dispatchers.IO) {
        ruleDao.getAllRulesOnce()
    }

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        resolveInfos
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val label = pm.getApplicationLabel(appInfo)?.toString()?.trim().orEmpty()
                    .ifEmpty { appInfo.packageName }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val hasInternet = hasInternetPermission(pm, appInfo.packageName)

                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSystem,
                    hasInternetPermission = hasInternet
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    suspend fun loadAppIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    private fun hasInternetPermission(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            pkgInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        }.getOrDefault(true)
    }

    suspend fun setAppRule(
        packageName: String,
        blockWifi: Boolean,
        blockMobileData: Boolean
    ) {
        if (!blockWifi && !blockMobileData) {
            ruleDao.deleteRule(packageName)
        } else {
            ruleDao.upsertRule(
                AppNetworkRuleEntity(
                    packageName = packageName,
                    blockWifi = blockWifi,
                    blockMobileData = blockMobileData,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun applyBatchAction(
        packageNames: Set<String>,
        action: BatchAction
    ) {
        if (packageNames.isEmpty()) return
        val existingMap = ruleDao.getAllRulesOnce().associateBy { it.packageName }
        val now = System.currentTimeMillis()

        when (action) {
            BatchAction.ALLOW_ALL -> {
                packageNames.forEach { ruleDao.deleteRule(it) }
            }
            BatchAction.BLOCK_BOTH -> {
                val updates = packageNames.map { pkg ->
                    AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = true,
                        blockMobileData = true,
                        updatedAt = now
                    )
                }
                ruleDao.upsertRules(updates)
            }
            BatchAction.BLOCK_WIFI -> {
                val updates = packageNames.map { pkg ->
                    val current = existingMap[pkg]
                    AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = true,
                        blockMobileData = current?.blockMobileData ?: false,
                        updatedAt = now
                    )
                }
                ruleDao.upsertRules(updates)
            }
            BatchAction.BLOCK_DATA -> {
                val updates = packageNames.map { pkg ->
                    val current = existingMap[pkg]
                    AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = current?.blockWifi ?: false,
                        blockMobileData = true,
                        updatedAt = now
                    )
                }
                ruleDao.upsertRules(updates)
            }
        }
    }

    suspend fun clearAllRules() {
        ruleDao.clearAllRules()
    }

    fun detectCurrentNetworkType(): ActiveNetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ActiveNetworkType.NONE
        val activeNetwork = cm.activeNetwork ?: return ActiveNetworkType.NONE
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return ActiveNetworkType.NONE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ActiveNetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ActiveNetworkType.MOBILE_DATA
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ActiveNetworkType.OTHER
            else -> ActiveNetworkType.NONE
        }
    }

    suspend fun isFirewallEnabledOnce(): Boolean = preferencesFlow.first().isFirewallEnabled
}
