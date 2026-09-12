package com.netmonitor.app

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RadioMonitorData::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioMonitorDao(): RadioMonitorDao
}
