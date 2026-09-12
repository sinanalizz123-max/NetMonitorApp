package com.netmonitor.app

import android.content.Context

class PrefsRepo(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(v) = prefs.edit().putBoolean(KEY_DARK, v).apply()

    var billingResetDay: Int
        get() = prefs.getInt(KEY_BILLING_DAY, 1).coerceIn(1, 28)
        set(v) = prefs.edit().putInt(KEY_BILLING_DAY, v.coerceIn(1, 28)).apply()

    var dataLimitMb: Long
        get() = prefs.getLong(KEY_LIMIT_MB, 0L).coerceAtLeast(0L)
        set(v) = prefs.edit().putLong(KEY_LIMIT_MB, v.coerceAtLeast(0L)).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(v) = prefs.edit().putBoolean(KEY_HAPTICS, v).apply()

    var showSpeed: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SPEED, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_SPEED, v).apply()

    companion object {
        private const val PREFS = "netmonitor_prefs"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_BILLING_DAY = "billing_reset_day"
        private const val KEY_LIMIT_MB = "data_limit_mb"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SHOW_SPEED = "show_speed"
    }
}
