package com.netmonitor.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

object AppUsageRepo {

    data class AppFg(
        val packageName: String,
        val label: String,
        val foregroundMillis: Long
    )

    fun hasPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun queryDaily(context: Context, limit: Int = 50): Pair<List<AppFg>, Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return emptyList<AppFg>() to true
        }
        if (!hasPermission(context)) return emptyList<AppFg>() to false
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val start = startOfDay(now)
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, now
            ) ?: return emptyList<AppFg>() to true
            val pm = context.packageManager
            val list = stats
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(limit)
                .map {
                    val label = try {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(it.packageName, 0)
                        ).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        it.packageName
                    }
                    AppFg(it.packageName, label, it.totalTimeInForeground)
                }
            list to true
        } catch (_: SecurityException) {
            emptyList<AppFg>() to false
        } catch (_: Exception) {
            emptyList<AppFg>() to true
        }
    }

    private fun startOfDay(now: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
