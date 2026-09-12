package com.netmonitor.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.netmonitor.app.util.CrashLogger

class NetMonitorApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "radio_monitor_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        val dark = PrefsRepo(this).darkMode
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
