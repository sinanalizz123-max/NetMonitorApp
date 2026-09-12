package com.netmonitor.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class DashboardAdapter(
    private val items: List<DashboardMetric>
) : RecyclerView.Adapter<DashboardAdapter.MetricVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MetricVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_signal_metric, parent, false)
        return MetricVH(v)
    }

    override fun onBindViewHolder(holder: MetricVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class MetricVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label = itemView.findViewById<TextView>(R.id.txtLabel)
        private val value = itemView.findViewById<TextView>(R.id.txtValue)
        private val trend = itemView.findViewById<ImageView>(R.id.imgTrend)

        fun bind(item: DashboardMetric) {
            label.text = item.label
            value.text = item.value

            value.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    when (item.status) {
                        MetricStatus.WEAK -> R.color.status_weak
                        MetricStatus.NORMAL -> R.color.status_normal
                        MetricStatus.GOOD -> R.color.status_good
                        MetricStatus.VERY_GOOD -> R.color.status_very_good
                    }
                )
            )

            when (item.trend) {
                MetricTrend.UP -> {
                    trend.setImageResource(R.drawable.ic_trending_up)
                    trend.contentDescription =
                        itemView.context.getString(R.string.desc_trend_up)
                    trend.visibility = View.VISIBLE
                }
                MetricTrend.DOWN -> {
                    trend.setImageResource(R.drawable.ic_trending_down)
                    trend.contentDescription =
                        itemView.context.getString(R.string.desc_trend_down)
                    trend.visibility = View.VISIBLE
                }
                MetricTrend.NONE -> {
                    trend.visibility = View.GONE
                }
            }
        }
    }
}
