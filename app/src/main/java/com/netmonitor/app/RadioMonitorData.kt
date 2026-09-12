package com.netmonitor.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_monitor_data")
data class RadioMonitorData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val simId: Int,
    val carrierName: String,
    val rsrp: Int,
    val rsrq: Int,
    val snr: Int,
    val pci: Int,
    val band: Int,
    val nrState: String
)
