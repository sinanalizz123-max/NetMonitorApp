package com.netmonitor.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DashboardFragment : Fragment() {

    private var monitor: NetworkMonitor? = null
    private var sim1Adapter: DashboardAdapter? = null
    private var sim2Adapter: DashboardAdapter? = null
    private var sim1Carrier: TextView? = null
    private var sim2Carrier: TextView? = null
    private var sim1Name: TextView? = null
    private var sim2Name: TextView? = null
    private var sim1Band: TextView? = null
    private var sim2Band: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.frag_dashboard, container, false)

        DashboardDataProvider.init(requireContext().applicationContext)

        val showSpeed = PrefsRepo(requireContext()).showSpeed
        val sim1Recycler = view.findViewById<RecyclerView>(R.id.recyclerSim1)
        sim1Recycler.layoutManager = LinearLayoutManager(requireContext())
        sim1Adapter = DashboardAdapter(DashboardDataProvider.getSim1Metrics(showSpeed))
        sim1Recycler.adapter = sim1Adapter

        val sim2Recycler = view.findViewById<RecyclerView>(R.id.recyclerSim2)
        sim2Recycler.layoutManager = LinearLayoutManager(requireContext())
        sim2Adapter = DashboardAdapter(DashboardDataProvider.getSim2Metrics(showSpeed))
        sim2Recycler.adapter = sim2Adapter

        val sim1Header = view.findViewById<View>(R.id.sim1Header)
        sim1Carrier = sim1Header?.findViewById(R.id.txtCarrier)
        sim1Name = sim1Header?.findViewById(R.id.txtSimName)
        sim1Band = sim1Header?.findViewById(R.id.txtBand)
        val sim2Header = view.findViewById<View>(R.id.sim2Header)
        sim2Carrier = sim2Header?.findViewById(R.id.txtCarrier)
        sim2Name = sim2Header?.findViewById(R.id.txtSimName)
        sim2Band = sim2Header?.findViewById(R.id.txtBand)

        refreshHeaders()

        return view
    }

    override fun onResume() {
        super.onResume()
        DashboardDataProvider.init(requireContext().applicationContext)
        monitor = DashboardDataProvider.monitor()
        monitor?.register { _ ->
            if (!isAdded) return@register
            view?.post { refresh() }
        }
        refresh()
    }

    override fun onPause() {
        monitor?.unregister()
        super.onPause()
    }

    private fun refresh() {
        if (!isAdded) return
        val showSpeed = try {
            PrefsRepo(requireContext()).showSpeed
        } catch (_: Exception) {
            true
        }
        sim1Adapter = DashboardAdapter(DashboardDataProvider.getSim1Metrics(showSpeed))
        view?.findViewById<RecyclerView>(R.id.recyclerSim1)?.adapter = sim1Adapter
        sim2Adapter = DashboardAdapter(DashboardDataProvider.getSim2Metrics(showSpeed))
        view?.findViewById<RecyclerView>(R.id.recyclerSim2)?.adapter = sim2Adapter
        refreshHeaders()
    }

    private fun refreshHeaders() {
        val mon = monitor ?: DashboardDataProvider.monitor()
        val c1 = mon?.slotCarrier(0) ?: "—"
        val c2 = mon?.slotCarrier(1) ?: "—"
        sim1Name?.text = "SIM 1"
        sim2Name?.text = "SIM 2"
        sim1Carrier?.text = c1
        sim2Carrier?.text = c2
        sim1Band?.text = DashboardDataProvider.bandTextForSlot(0)
        sim2Band?.text = DashboardDataProvider.bandTextForSlot(1)
    }
}
