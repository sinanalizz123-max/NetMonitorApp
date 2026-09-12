package com.netmonitor.app

import android.content.Context

object DashboardDataProvider {

    private var monitor: NetworkMonitor? = null
    private val lastRsrp = mutableMapOf<Int, Int>()

    fun init(context: Context) {
        if (monitor == null) {
            monitor = NetworkMonitor(context.applicationContext)
        }
    }

    fun monitor(): NetworkMonitor? = monitor

    fun getSim1Metrics(showSpeed: Boolean = true): List<DashboardMetric> = buildForSlot(0, showSpeed)

    fun getSim2Metrics(showSpeed: Boolean = true): List<DashboardMetric> = buildForSlot(1, showSpeed)

    fun snapshot(slot: Int): LiveSignal {
        return monitor?.snapshot(slot) ?: LiveSignal.empty()
    }

    fun bandTextForSlot(slot: Int): String {
        val live = monitor?.snapshot(slot) ?: LiveSignal.empty()
        return bandText(live)
    }

    private fun buildForSlot(slot: Int, showSpeed: Boolean = true): List<DashboardMetric> {
        val live = monitor?.snapshot(slot) ?: LiveSignal.empty()
        val status = NetworkMonitor.rsrpToStatus(live.rsrpDbm)
        val trend = trendFor(slot, live.rsrpDbm)

        val rsrpText = live.rsrpDbm?.let { "$it dBm" } ?: "—"
        val rsrqText = live.rsrqDb?.let { "$it dB" } ?: "—"
        val sinrText = live.sinrDb?.let { "$it dB" } ?: "—"
        val pciText = live.pci?.toString() ?: "—"
        val bandText = bandText(live)

        val rows = arrayListOf(
            DashboardMetric(
                label = "Signal Strength (RSRP)",
                value = rsrpText,
                status = status,
                trend = trend
            ),
            DashboardMetric(
                label = "Signal Quality (RSRQ)",
                value = rsrqText,
                status = status
            ),
            DashboardMetric(
                label = "Noise Ratio (SINR)",
                value = sinrText,
                status = status
            ),
            DashboardMetric(
                label = "PCI (Cell ID)",
                value = pciText,
                status = status
            ),
            DashboardMetric(
                label = "Band / CA",
                value = bandText,
                status = status
            )
        )
        if (showSpeed) {
            rows.add(
                DashboardMetric(
                    label = "Download",
                    value = formatKbps(live.downKbps),
                    status = status
                )
            )
            rows.add(
                DashboardMetric(
                    label = "Upload",
                    value = formatKbps(live.upKbps),
                    status = status
                )
            )
        }
        return rows
    }

    private fun formatKbps(kbps: Long): String {
        if (kbps <= 0) return "—"
        return if (kbps >= 1000) {
            String.format(java.util.Locale.US, "%.1f Mbps", kbps / 1000.0)
        } else {
            "$kbps Kbps"
        }
    }

    private fun bandText(live: LiveSignal): String {
        if (live.transport == "WIFI") {
            val ssid = live.wifiSsid
            val speed = live.wifiLinkMbps
            if (ssid != null && speed != null) return "$ssid · ${speed}Mbps"
            if (ssid != null) return ssid
            if (live.wifiRssiDbm != null) return "${live.wifiRssiDbm} dBm"
            return "WIFI"
        }
        if (live.band != null && live.arfcn != null) return "B${live.band} · ${live.arfcn}"
        if (live.band != null) return "B${live.band}"
        if (live.arfcn != null) return "ARFCN ${live.arfcn}"
        return "—"
    }

    private fun trendFor(slot: Int, rsrp: Int?): MetricTrend {
        if (rsrp == null) return MetricTrend.NONE
        val prev = lastRsrp.put(slot, rsrp)
        if (prev == null) return MetricTrend.NONE
        return when {
            rsrp > prev -> MetricTrend.UP
            rsrp < prev -> MetricTrend.DOWN
            else -> MetricTrend.NONE
        }
    }
}
