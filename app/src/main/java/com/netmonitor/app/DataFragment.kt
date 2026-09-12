package com.netmonitor.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator

class DataFragment : Fragment() {

    private var recycler: RecyclerView? = null
    private var txtTotalToday: TextView? = null
    private var txtCycleTotal: TextView? = null
    private var txtEmpty: TextView? = null
    private var btnGrant: Button? = null
    private var cycleProgress: LinearProgressIndicator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.frag_data, container, false)

        recycler = view.findViewById(R.id.recyclerData)
        recycler?.layoutManager = LinearLayoutManager(requireContext())

        txtTotalToday = view.findViewById(R.id.txtTotalToday)
        txtCycleTotal = view.findViewById(R.id.txtCycleTotal)
        txtEmpty = view.findViewById(R.id.txtEmpty)
        btnGrant = view.findViewById(R.id.btnGrantUsage)
        cycleProgress = view.findViewById(R.id.cycleProgress)
        btnGrant?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
            }
        }
        refresh()
        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ctx = context ?: return
        val granted = DataUsageRepo.hasUsageAccess(ctx)
        val prefs = PrefsRepo(ctx)

        if (!granted) {
            txtEmpty?.visibility = View.VISIBLE
            txtEmpty?.text = "Usage access not granted — grant access to see per-app data."
            btnGrant?.visibility = View.VISIBLE
            recycler?.visibility = View.GONE
        } else {
            btnGrant?.visibility = View.GONE
            recycler?.visibility = View.VISIBLE
        }

        val today = DataUsageRepo.todayTotals(ctx)
        val cycle = DataUsageRepo.cycleTotals(ctx, prefs.billingResetDay)
        if (prefs.dataLimitMb > 0) {
            val limitBytes = prefs.dataLimitMb * 1024L * 1024L
            val pct = if (limitBytes > 0) (cycle.total * 100 / limitBytes).toInt().coerceIn(0, 100) else 0
            cycleProgress?.visibility = View.VISIBLE
            cycleProgress?.setProgressCompat(pct, true)
        } else {
            cycleProgress?.visibility = View.GONE
        }
        if (granted) {
            txtTotalToday?.text = "Today: ${DataUsageRepo.formatBytes(today.total)}"
            var cycleLine = "Billing cycle: ${DataUsageRepo.formatBytes(cycle.total)}"
            if (prefs.dataLimitMb > 0) {
                val limitBytes = prefs.dataLimitMb * 1024L * 1024L
                if (cycle.total > limitBytes) {
                    cycleLine += " ⚠ Limit exceeded (${prefs.dataLimitMb} MB)"
                }
            }
            txtCycleTotal?.text = cycleLine
        } else {
            txtTotalToday?.text = "Since boot: ${DataUsageRepo.formatBytes(today.total)}"
            var cycleLine = "Billing cycle: — (grant access for cycle totals)"
            if (prefs.dataLimitMb > 0) {
                val limitBytes = prefs.dataLimitMb * 1024L * 1024L
                if (cycle.total > limitBytes) {
                    cycleLine += " ⚠ Limit exceeded (${prefs.dataLimitMb} MB)"
                }
            }
            txtCycleTotal?.text = cycleLine
        }

        val apps = if (granted) DataUsageRepo.topAppsByData(ctx, 10) else emptyList()
        if (apps.isEmpty() && granted) {
            txtEmpty?.visibility = View.VISIBLE
            txtEmpty?.text = "No app data yet"
        } else if (granted) {
            txtEmpty?.visibility = View.GONE
        }
        recycler?.adapter = DataAdapter(apps)
    }
}
