package com.netmonitor.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BatteryAdapter(
    private val items: List<BatteryApp>
) : RecyclerView.Adapter<BatteryAdapter.AppVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_battery_app, parent, false)
        return AppVH(v)
    }

    override fun onBindViewHolder(holder: AppVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class AppVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.txtBatteryAppName)
        private val percent = itemView.findViewById<TextView>(R.id.txtBatteryPercent)
        private val drain = itemView.findViewById<TextView>(R.id.txtBatteryDrain)

        fun bind(app: BatteryApp) {
            name.text = app.name
            percent.text = app.percent
            drain.text = app.drainMa
        }
    }
}
