package com.netmonitor.app

import android.app.AppOpsManager
import android.annotation.SuppressLint
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.RemoteException
import android.telephony.TelephonyManager
import java.util.Calendar

object DataUsageRepo {

    data class Totals(val rxBytes: Long, val txBytes: Long) {
        val total: Long get() = (rxBytes.coerceAtLeast(0L)) + (txBytes.coerceAtLeast(0L))
    }

    fun hasUsageAccess(context: Context): Boolean {
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

    fun todayTotals(context: Context): Totals {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasUsageAccess(context)) {
            try {
                val now = System.currentTimeMillis()
                val start = startOfDay(now)
                val mobile = queryBucket(context, ConnectivityManager.TYPE_MOBILE, start, now)
                val wifi = queryBucket(context, ConnectivityManager.TYPE_WIFI, start, now)
                return Totals(mobile.rxBytes + wifi.rxBytes, mobile.txBytes + wifi.txBytes)
            } catch (_: Exception) {
            }
        }
        return trafficStatsTotals()
    }

    fun cycleTotals(context: Context, resetDay: Int): Totals {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasUsageAccess(context)) {
            try {
                val now = System.currentTimeMillis()
                val start = billingCycleStart(resetDay, now)
                val mobile = queryBucket(context, ConnectivityManager.TYPE_MOBILE, start, now)
                val wifi = queryBucket(context, ConnectivityManager.TYPE_WIFI, start, now)
                return Totals(mobile.rxBytes + wifi.rxBytes, mobile.txBytes + wifi.txBytes)
            } catch (_: Exception) {
            }
        }
        return trafficStatsTotals()
    }

    fun topAppsByData(context: Context, limit: Int = 10): List<AppUsage> {
        return try {
            val pm = context.packageManager
            val pkgs = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            var grandTotal = 0L
            val rows = ArrayList<Triple<String, Long, String>>(pkgs.size)
            for (app in pkgs) {
                val uid = app.uid
                val rx = try {
                    TrafficStats.getUidRxBytes(uid)
                } catch (_: Exception) {
                    TrafficStats.UNSUPPORTED.toLong()
                }
                val tx = try {
                    TrafficStats.getUidTxBytes(uid)
                } catch (_: Exception) {
                    TrafficStats.UNSUPPORTED.toLong()
                }
                val total = (if (rx > 0) rx else 0L) + (if (tx > 0) tx else 0L)
                if (total <= 0) continue
                val label = try {
                    pm.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    app.packageName
                }
                grandTotal += total
                rows.add(Triple(label, total, app.packageName))
            }
            rows.sortByDescending { it.second }
            rows.take(limit).map { (label, total, _) ->
                val pct = if (grandTotal > 0) (total * 100 / grandTotal).toInt() else 0
                AppUsage(label, "$pct%", formatBytes(total))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "—"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.0f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun billingCycleStart(resetDay: Int, now: Long = System.currentTimeMillis()): Long {
        val day = resetDay.coerceIn(1, 28)
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.get(Calendar.DAY_OF_MONTH) < day) {
            cal.add(Calendar.MONTH, -1)
        }
        cal.set(Calendar.DAY_OF_MONTH, day)
        return cal.timeInMillis
    }

    private fun startOfDay(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun trafficStatsTotals(): Totals {
        return try {
            Totals(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes())
        } catch (_: Exception) {
            Totals(-1L, -1L)
        }
    }

    private fun queryBucket(
        context: Context,
        networkType: Int,
        start: Long,
        end: Long
    ): Totals {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return Totals(0L, 0L)
        return try {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val subId = if (networkType == ConnectivityManager.TYPE_MOBILE) {
                mobileSubscriberId(context)
            } else {
                ""
            }
            val bucket = nsm.querySummaryForDevice(networkType, subId, start, end)
            Totals(bucket.rxBytes.coerceAtLeast(0L), bucket.txBytes.coerceAtLeast(0L))
        } catch (_: SecurityException) {
            Totals(0L, 0L)
        } catch (_: RemoteException) {
            Totals(0L, 0L)
        } catch (_: Exception) {
            Totals(0L, 0L)
        }
    }

    @SuppressLint("MissingPermission")
    private fun mobileSubscriberId(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            try {
                tm.subscriberId ?: ""
            } catch (_: SecurityException) {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
