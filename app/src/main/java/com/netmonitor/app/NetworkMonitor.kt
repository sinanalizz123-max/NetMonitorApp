package com.netmonitor.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class LiveSignal(
    val transport: String,
    val carrier: String,
    val rsrpDbm: Int?,
    val rsrqDb: Int?,
    val sinrDb: Int?,
    val pci: Int?,
    val band: Int?,
    val arfcn: Int?,
    val wifiRssiDbm: Int?,
    val wifiLinkMbps: Int?,
    val wifiSsid: String?,
    val downKbps: Long,
    val upKbps: Long
) {
    companion object {
        fun empty(carrier: String = "—", transport: String = "—") = LiveSignal(
            transport = transport,
            carrier = carrier,
            rsrpDbm = null,
            rsrqDb = null,
            sinrDb = null,
            pci = null,
            band = null,
            arfcn = null,
            wifiRssiDbm = null,
            wifiLinkMbps = null,
            wifiSsid = null,
            downKbps = 0L,
            upKbps = 0L
        )
    }
}

class NetworkMonitor(private val appContext: Context) {

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var listener: ((LiveSignal) -> Unit)? = null

    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()
    private var lastTime = System.currentTimeMillis()

    fun register(onUpdate: (LiveSignal) -> Unit) {
        listener = onUpdate
        if (callback != null) return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                pushDefault()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                pushDefault()
            }

            override fun onLost(network: Network) {
                pushDefault()
            }
        }
        callback = cb
        try {
            connectivityManager.registerNetworkCallback(req, cb)
        } catch (_: Exception) {
            pushDefault()
        }
        pushDefault()
    }

    fun unregister() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        callback = null
        listener = null
    }

    fun snapshot(slotIndex: Int = 0): LiveSignal {
        val transport = activeTransport()
        val carrier = slotCarrier(slotIndex)
        val wifi = readWifi(transport)
        val cell = readCell(slotIndex)
        val speeds = readSpeeds()
        return LiveSignal(
            transport = transport,
            carrier = carrier,
            rsrpDbm = cell?.rsrp,
            rsrqDb = cell?.rsrq,
            sinrDb = cell?.sinr,
            pci = cell?.pci,
            band = cell?.band,
            arfcn = cell?.arfcn,
            wifiRssiDbm = wifi?.rssi,
            wifiLinkMbps = wifi?.linkSpeed,
            wifiSsid = wifi?.ssid,
            downKbps = speeds.first,
            upKbps = speeds.second
        )
    }

    @SuppressLint("MissingPermission")
    fun slotCarrier(slotIndex: Int): String {
        if (hasPhoneState()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val sub = SubscriptionManager.from(appContext)
                    val infos = sub.activeSubscriptionInfoList
                    if (infos != null && slotIndex < infos.size) {
                        infos[slotIndex]?.carrierName?.toString()?.takeIf { it.isNotBlank() }
                            ?.let { return it }
                    }
                }
            } catch (_: Exception) {
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val subIds = subscriptionIds()
                    if (slotIndex < subIds.size) {
                        val tm = telephonyManager.createForSubscriptionId(subIds[slotIndex])
                        tm.networkOperatorName?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                } catch (_: Exception) {
                }
            }
            try {
                telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }?.let { return it }
            } catch (_: Exception) {
            }
        }
        return "—"
    }

    fun activeTransport(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "—"
        return try {
            val net = connectivityManager.activeNetwork ?: return "—"
            val caps = connectivityManager.getNetworkCapabilities(net) ?: return "—"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "—"
            }
        } catch (_: Exception) {
            "—"
        }
    }

    private fun pushDefault() {
        try {
            listener?.invoke(snapshot(0))
        } catch (_: Exception) {
        }
    }

    private data class WifiInfo(val rssi: Int?, val linkSpeed: Int?, val ssid: String?)
    private data class CellInfo(
        val rsrp: Int?,
        val rsrq: Int?,
        val sinr: Int?,
        val pci: Int?,
        val band: Int? = null,
        val arfcn: Int? = null
    )

    private fun readWifi(transport: String): WifiInfo? {
        if (transport != "WIFI") return null
        return try {
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo ?: return null
            var ssid: String? = null
            if (hasLocation()) {
                try {
                    info.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                        ?.let { ssid = it.trim('"') }
                } catch (_: Exception) {
                }
            }
            WifiInfo(rssi = info.rssi, linkSpeed = info.linkSpeed, ssid = ssid)
        } catch (_: Exception) {
            null
        }
    }

    private fun readCell(slotIndex: Int): CellInfo? {
        if (!hasPhoneState()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasLocation()) return null
        return try {
            val tm = tmForSlot(slotIndex)
            val all = tm.allCellInfo ?: return null
            for (ci in all) {
                if (ci == null || !ci.isRegistered) continue
                if (ci is CellInfoLte) {
                    val s = ci.cellSignalStrength
                    var rsrp: Int? = null
                    var rsrq: Int? = null
                    var sinr: Int? = null
                    var pci: Int? = null
                    var band: Int? = null
                    var arfcn: Int? = null
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val v = s.rsrp
                            if (v != android.telephony.CellInfo.UNAVAILABLE) rsrp = v
                        } else {
                            @Suppress("DEPRECATION")
                            val v = s.dbm
                            if (v != android.telephony.CellInfo.UNAVAILABLE) rsrp = v
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val v = s.rsrq
                            if (v != android.telephony.CellInfo.UNAVAILABLE) rsrq = v
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val v = s.rssnr
                            if (v != android.telephony.CellInfo.UNAVAILABLE) sinr = v
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        pci = ci.cellIdentity.pci
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val v = ci.cellIdentity.earfcn
                            if (v != android.telephony.CellInfo.UNAVAILABLE) arfcn = v
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val bands = ci.cellIdentity.bands
                            if (bands != null && bands.isNotEmpty()) band = bands[0]
                        }
                    } catch (_: Exception) {
                    }
                    return CellInfo(rsrp, rsrq, sinr, pci, band, arfcn)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ci is CellInfoNr) {
                    val s = ci.cellSignalStrength as? android.telephony.CellSignalStrengthNr
                    var rsrp: Int? = null
                    var rsrq: Int? = null
                    var sinr: Int? = null
                    var pci: Int? = null
                    var arfcn: Int? = null
                    try {
                        val v = s?.ssRsrp ?: android.telephony.CellInfo.UNAVAILABLE
                        if (v != android.telephony.CellInfo.UNAVAILABLE) rsrp = v
                    } catch (_: Exception) {
                    }
                    if (rsrp == null) {
                        try {
                            val v = s?.csiRsrp ?: android.telephony.CellInfo.UNAVAILABLE
                            if (v != android.telephony.CellInfo.UNAVAILABLE) rsrp = v
                        } catch (_: Exception) {
                        }
                    }
                    if (rsrp == null) {
                        try {
                            @Suppress("DEPRECATION")
                            val v = s?.dbm ?: android.telephony.CellInfo.UNAVAILABLE
                            if (v != android.telephony.CellInfo.UNAVAILABLE) rsrp = v
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        val v = s?.ssRsrq ?: android.telephony.CellInfo.UNAVAILABLE
                        if (v != android.telephony.CellInfo.UNAVAILABLE) rsrq = v
                    } catch (_: Exception) {
                    }
                    if (rsrq == null) {
                        try {
                            val v = s?.csiRsrq ?: android.telephony.CellInfo.UNAVAILABLE
                            if (v != android.telephony.CellInfo.UNAVAILABLE) rsrq = v
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        val v = s?.ssSinr ?: android.telephony.CellInfo.UNAVAILABLE
                        if (v != android.telephony.CellInfo.UNAVAILABLE) sinr = v
                    } catch (_: Exception) {
                    }
                    if (sinr == null) {
                        try {
                            val v = s?.csiSinr ?: android.telephony.CellInfo.UNAVAILABLE
                            if (v != android.telephony.CellInfo.UNAVAILABLE) sinr = v
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        pci = (ci.cellIdentity as? android.telephony.CellIdentityNr)?.pci
                    } catch (_: Exception) {
                    }
                    try {
                        val v = (ci.cellIdentity as? android.telephony.CellIdentityNr)?.nrarfcn
                        if (v != null && v != android.telephony.CellInfo.UNAVAILABLE) arfcn = v
                    } catch (_: Exception) {
                    }
                    return CellInfo(rsrp, rsrq, sinr, pci, null, arfcn)
                }
                if (ci is CellInfoWcdma) {
                    var dbm: Int? = null
                    var psc: Int? = null
                    var arfcn: Int? = null
                    try {
                        val v = ci.cellSignalStrength.dbm
                        if (v != android.telephony.CellInfo.UNAVAILABLE) dbm = v
                    } catch (_: Exception) {
                    }
                    try {
                        psc = ci.cellIdentity.psc
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val v = ci.cellIdentity.uarfcn
                            if (v != android.telephony.CellInfo.UNAVAILABLE) arfcn = v
                        }
                    } catch (_: Exception) {
                    }
                    return CellInfo(dbm, null, null, psc, null, arfcn)
                }
                if (ci is CellInfoGsm) {
                    var dbm: Int? = null
                    var bsic: Int? = null
                    var arfcn: Int? = null
                    try {
                        val v = ci.cellSignalStrength.dbm
                        if (v != android.telephony.CellInfo.UNAVAILABLE) dbm = v
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            bsic = ci.cellIdentity.bsic
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val v = ci.cellIdentity.arfcn
                            if (v != android.telephony.CellInfo.UNAVAILABLE) arfcn = v
                        }
                    } catch (_: Exception) {
                    }
                    return CellInfo(dbm, null, null, bsic, null, arfcn)
                }
            }
            null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun tmForSlot(slotIndex: Int): TelephonyManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hasPhoneState()) {
            try {
                val ids = subscriptionIds()
                if (slotIndex < ids.size) {
                    return telephonyManager.createForSubscriptionId(ids[slotIndex])
                }
            } catch (_: Exception) {
            }
        }
        return telephonyManager
    }

    @SuppressLint("MissingPermission")
    private fun subscriptionIds(): List<Int> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sub = SubscriptionManager.from(appContext)
                sub.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readSpeeds(): Pair<Long, Long> {
        return try {
            val now = System.currentTimeMillis()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = ((now - lastTime) / 1000.0).coerceAtLeast(1.0)
            var down = 0L
            var up = 0L
            if (rx >= 0 && lastRx >= 0 && rx >= lastRx) {
                down = (((rx - lastRx) * 8.0) / dt / 1000.0).toLong()
            }
            if (tx >= 0 && lastTx >= 0 && tx >= lastTx) {
                up = (((tx - lastTx) * 8.0) / dt / 1000.0).toLong()
            }
            lastRx = rx
            lastTx = tx
            lastTime = now
            down to up
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun hasPhoneState(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocation(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        fun rsrpToStatus(rsrpDbm: Int?): MetricStatus {
            if (rsrpDbm == null) return MetricStatus.NORMAL
            return when {
                rsrpDbm > -85 -> MetricStatus.VERY_GOOD
                rsrpDbm > -95 -> MetricStatus.GOOD
                rsrpDbm > -105 -> MetricStatus.NORMAL
                else -> MetricStatus.WEAK
            }
        }
    }
}
