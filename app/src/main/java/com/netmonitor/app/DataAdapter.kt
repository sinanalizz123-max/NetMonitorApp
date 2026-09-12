package com.netmonitor.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DataAdapter(
    private val items: List<AppUsage>
) : RecyclerView.Adapter<DataAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_data_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name = v.findViewById<TextView>(R.id.txtAppName)
        private val percent = v.findViewById<TextView>(R.id.txtPercent)
        private val amount = v.findViewById<TextView>(R.id.txtAmount)

        fun bind(item: AppUsage) {
            name.text = item.name
            percent.text = item.percent
            amount.text = item.amount
        }
    }
}
