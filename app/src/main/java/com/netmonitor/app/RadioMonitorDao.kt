package com.netmonitor.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioMonitorDao {
    @Insert
    suspend fun insert(data: RadioMonitorData)

    @Query("SELECT * FROM radio_monitor_data ORDER BY timestamp DESC LIMIT 100")
    fun getLatestRadioMonitorData(): Flow<List<RadioMonitorData>>

    @Query("SELECT * FROM radio_monitor_data WHERE simId = :simId ORDER BY timestamp DESC LIMIT 2880")
    fun get24HourRadioMonitorData(simId: Int): Flow<List<RadioMonitorData>>

    @Query("DELETE FROM radio_monitor_data WHERE timestamp < :timestampCutoff")
    suspend fun deleteOldData(timestampCutoff: Long)
}
