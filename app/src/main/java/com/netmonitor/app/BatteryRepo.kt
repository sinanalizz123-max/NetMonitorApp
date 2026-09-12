package com.netmonitor.app

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build

object BatteryRepo {

    data class BatteryInfo(
        val levelPct: Int,
        val statusText: String,
        val tempC: Float?,
        val voltageMv: Int?,
        val currentMaNow: Float?,
        val capacityPct: Int?,
        val isCharging: Boolean
    )

    fun fromIntent(intent: Intent?): BatteryInfo {
        if (intent == null) return BatteryInfo(-1, "—", null, null, null, null, false)
        return try {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "—"
            }
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val temp = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else null
            val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
            BatteryInfo(pct, statusText, temp, volt, null, null, isCharging)
        } catch (_: Exception) {
            BatteryInfo(-1, "—", null, null, null, null, false)
        }
    }

    fun enrichWithManager(context: Context, base: BatteryInfo): BatteryInfo {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val cap = try {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .takeIf { it in 0..100 }
            } catch (_: Exception) {
                null
            }
            val currentMa = currentNowMa(context)
            base.copy(
                levelPct = if (base.levelPct >= 0) base.levelPct else (cap ?: -1),
                capacityPct = cap,
                currentMaNow = currentMa
            )
        } catch (_: Exception) {
            base
        }
    }

    fun currentNowMa(context: Context): Float? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val ua = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (ua == Long.MIN_VALUE) null else ua / 1000f
        } catch (_: Exception) {
            null
        }
    }

    fun statusLine(info: BatteryInfo): String {
        val current = info.currentMaNow?.let {
            val ma = kotlin.math.abs(it) / 1000f
            String.format(java.util.Locale.US, "%.1fA", ma)
        } ?: "—"
        return if (info.statusText == "—") "—" else "${info.statusText} • $current"
    }

    fun estimatePerAppDrain(context: Context, currentMaNow: Float?): List<BatteryApp> {
        return try {
            val (apps, granted) = AppUsageRepo.queryDaily(context, 10)
            if (!granted || apps.isEmpty()) return emptyList()
            val totalFg = apps.sumOf { it.foregroundMillis }.coerceAtLeast(1L)
            val ma = kotlin.math.abs(currentMaNow ?: 0f)
            apps.take(5).map {
                val share = (it.foregroundMillis * 100 / totalFg).toInt()
                val drain = if (ma > 0) {
                    val appMa = ma * it.foregroundMillis / totalFg
                    "${appMa.toInt()} mA"
                } else {
                    "—"
                }
                BatteryApp(it.label, "$share%", drain)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
