package com.netmonitor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class NetworkSpeedMonitorService : Service() {

    private val channelId = "NetworkSpeedMonitorChannel"
    private val notificationId = 101
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var remoteViews: RemoteViews
    private lateinit var handler: Handler
    private lateinit var updateTask: Runnable

    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTimestamp: Long = 0
    private var tickCount = 0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        handler = Handler(Looper.getMainLooper())
        setupNotification()

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTimestamp = System.currentTimeMillis()

        updateTask = object : Runnable {
            override fun run() {
                updateNotificationWithSpeed()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, notificationBuilder.build())
        handler.removeCallbacks(updateTask)
        handler.post(updateTask)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            handler.removeCallbacks(updateTask)
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Network Speed Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupNotification() {
        remoteViews = RemoteViews(packageName, R.layout.notification_speed_monitor)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setCustomContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    private fun updateNotificationWithSpeed() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTimestamp = System.currentTimeMillis()

        val rxDelta = currentRxBytes - lastRxBytes
        val txDelta = currentTxBytes - lastTxBytes
        val timeDelta = (currentTimestamp - lastTimestamp).toFloat() / 1000

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastTimestamp = currentTimestamp

        val downloadKbps = if (timeDelta > 0) (rxDelta / 1024 / timeDelta) else 0f
        val uploadKbps = if (timeDelta > 0) (txDelta / 1024 / timeDelta) else 0f

        remoteViews.setTextViewText(
            R.id.download_speed_text,
            String.format(Locale.getDefault(), "DL: %.1f kB/s", downloadKbps)
        )
        remoteViews.setTextViewText(
            R.id.upload_speed_text,
            String.format(Locale.getDefault(), "UL: %.1f kB/s", uploadKbps)
        )
        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (_: Exception) {
        }

        tickCount++
        if (tickCount % 10 == 0) {
            logSnapshotToRoom()
        }
    }

    private fun logSnapshotToRoom() {
        serviceScope.launch {
            try {
                val app = application as? NetMonitorApp ?: return@launch
                val dao = app.db.radioMonitorDao()
                val monitor = NetworkMonitor(applicationContext)
                val snap = try {
                    monitor.snapshot(0)
                } catch (_: Exception) {
                    LiveSignal.empty()
                }
                val transport = snap.transport.ifBlank { activeTransportFallback() }
                dao.insert(
                    RadioMonitorData(
                        timestamp = System.currentTimeMillis(),
                        simId = 0,
                        carrierName = snap.carrier,
                        rsrp = snap.rsrpDbm ?: 0,
                        rsrq = snap.rsrqDb ?: 0,
                        snr = snap.sinrDb ?: 0,
                        pci = snap.pci ?: 0,
                        band = snap.band ?: snap.arfcn ?: 0,
                        nrState = transport
                    )
                )
                dao.deleteOldData(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
            } catch (_: Exception) {
            }
        }
    }

    private fun activeTransportFallback(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "—"
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return "—"
            val caps = cm.getNetworkCapabilities(net) ?: return "—"
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
}
