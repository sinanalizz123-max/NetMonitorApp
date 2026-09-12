package com.netmonitor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class BatteryFragment : Fragment() {

    private var txtPercent: TextView? = null
    private var txtStatus: TextView? = null
    private var txtTemp: TextView? = null
    private var txtVoltage: TextView? = null
    private var txtEmpty: TextView? = null
    private var btnGrant: Button? = null
    private var recycler: RecyclerView? = null
    private var historyStrip: LinearLayout? = null
    private var txtHistoryEmpty: TextView? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            updateFromIntent(context, intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.frag_battery, container, false)

        txtPercent = view.findViewById(R.id.txtBatteryPercent)
        txtStatus = view.findViewById(R.id.txtBatteryStatus)
        txtTemp = view.findViewById(R.id.txtBatteryTemp)
        txtVoltage = view.findViewById(R.id.txtBatteryVoltage)
        txtEmpty = view.findViewById(R.id.txtBatteryEmpty)
        btnGrant = view.findViewById(R.id.btnGrantBatteryUsage)
        recycler = view.findViewById(R.id.recyclerBatteryApps)
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        recycler?.adapter = BatteryAdapter(emptyList())
        historyStrip = view.findViewById(R.id.historyStrip)
        txtHistoryEmpty = view.findViewById(R.id.txtHistoryEmpty)
        btnGrant?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
            }
        }

        try {
            val sticky = requireContext().registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            updateFromIntent(requireContext(), sticky)
        } catch (_: Exception) {
        }

        loadHistory()

        return view
    }

    override fun onResume() {
        super.onResume()
        try {
            requireContext().registerReceiver(
                batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        } catch (_: Exception) {
        }
        try {
            val sticky = requireContext().registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            updateFromIntent(requireContext(), sticky)
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        try {
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    private fun loadHistory() {
        val appCtx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val app = appCtx as? NetMonitorApp ?: return@launch
                app.db.radioMonitorDao().getLatestRadioMonitorData().collect { rows ->
                    if (!isAdded) return@collect
                    renderHistory(rows.take(24))
                }
            } catch (_: Exception) {
                renderHistory(emptyList())
            }
        }
    }

    private fun renderHistory(rows: List<RadioMonitorData>) {
        val strip = historyStrip ?: return
        strip.removeAllViews()
        if (rows.isEmpty()) {
            strip.visibility = View.GONE
            txtHistoryEmpty?.visibility = View.VISIBLE
            return
        }
        strip.visibility = View.VISIBLE
        txtHistoryEmpty?.visibility = View.GONE
        val ctx = context ?: return
        val density = ctx.resources.displayMetrics.density
        val barColor = ContextCompat.getColor(ctx, R.color.brand_green)
        for (row in rows.reversed()) {
            val rsrp = if (row.rsrp == 0) RSRP_MIN_DB else row.rsrp
            val frac = ((rsrp - RSRP_MIN_DB).toFloat() / (RSRP_MAX_DB - RSRP_MIN_DB))
                .coerceIn(0.08f, 1f)
            val bar = View(ctx)
            val heightPx = ((12 + frac * 100) * density).toInt()
            val params = LinearLayout.LayoutParams(0, heightPx, 1f)
            params.marginEnd = (2 * density).toInt()
            bar.layoutParams = params
            bar.setBackgroundColor(barColor)
            bar.contentDescription = "Signal ${row.rsrp} dBm"
            strip.addView(bar)
        }
    }

    private fun updateFromIntent(context: Context, intent: Intent?) {
        if (!isAdded) return
        var info = BatteryRepo.fromIntent(intent)
        info = BatteryRepo.enrichWithManager(context, info)
        txtPercent?.text = if (info.levelPct >= 0) "${info.levelPct}%" else "—"
        txtStatus?.text = BatteryRepo.statusLine(info)
        txtTemp?.text = info.tempC?.let {
            String.format(java.util.Locale.US, "Temp: %.1f°C", it)
        } ?: "Temp: —"
        txtVoltage?.text = info.voltageMv?.let { "Voltage: ${it} mV" } ?: "Voltage: —"
        val granted = AppUsageRepo.hasPermission(context)
        val drain = BatteryRepo.estimatePerAppDrain(context, info.currentMaNow)
        recycler?.adapter = BatteryAdapter(drain)
        if (!granted) {
            txtEmpty?.visibility = View.VISIBLE
            txtEmpty?.text = "Usage access not granted — grant access to see per-app drain."
            btnGrant?.visibility = View.VISIBLE
        } else if (drain.isEmpty()) {
            txtEmpty?.visibility = View.VISIBLE
            txtEmpty?.text = "No app drain data yet"
            btnGrant?.visibility = View.GONE
        } else {
            txtEmpty?.visibility = View.GONE
            btnGrant?.visibility = View.GONE
        }
    }

    companion object {
        private const val RSRP_MIN_DB = -130
        private const val RSRP_MAX_DB = -60
    }
}
