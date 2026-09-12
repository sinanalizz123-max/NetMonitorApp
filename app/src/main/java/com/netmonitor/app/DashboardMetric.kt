package com.netmonitor.app

data class DashboardMetric(
    val label: String,
    val value: String,
    val status: MetricStatus,
    val trend: MetricTrend = MetricTrend.NONE
)

enum class MetricStatus {
    WEAK,
    NORMAL,
    GOOD,
    VERY_GOOD
}
