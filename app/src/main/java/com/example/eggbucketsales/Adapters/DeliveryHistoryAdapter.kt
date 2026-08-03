package com.example.eggbucketsales.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.eggbucketsales.R
import java.util.Locale

data class DeliveryHistory(
    val date: String,
    val status: String,
    val quantity: Int = 0,
    val totalAmount: Int = 0,
    val agentName: String = "",
    val reason: String = ""
)

class DeliveryHistoryAdapter :
    ListAdapter<DeliveryHistory, DeliveryHistoryAdapter.ViewHolder>(DeliveryHistoryDiffCallback()) {

    class DeliveryHistoryDiffCallback : DiffUtil.ItemCallback<DeliveryHistory>() {
        override fun areItemsTheSame(oldItem: DeliveryHistory, newItem: DeliveryHistory): Boolean {
            return oldItem.date == newItem.date && oldItem.status == newItem.status
        }

        override fun areContentsTheSame(oldItem: DeliveryHistory, newItem: DeliveryHistory): Boolean {
            return oldItem == newItem
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvAgent: TextView = view.findViewById(R.id.tvAgent)
        val detailsLayout: View = view.findViewById(R.id.detailsLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delivery_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvDate.text = item.date
        holder.tvStatus.text = item.status.uppercase()
        holder.tvAgent.text = "By: ${item.agentName}"

        when (item.status) {
            "delivered" -> {
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_edittext_bg)
                holder.tvStatus.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.green)
                holder.detailsLayout.visibility = View.VISIBLE
                holder.tvQuantity.text = if (item.quantity == 1) "${item.quantity} Tray" else "${item.quantity} Trays"
                holder.tvAmount.text = "₹${item.totalAmount}"
            }
            "reached", "shop_closed", "stock_available", "other_vendor" -> {
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_edittext_bg)
                holder.tvStatus.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.maincolor)
                holder.detailsLayout.visibility = View.VISIBLE
                val reasonText = when(item.reason) {
                    "shop_closed" -> "Shop Closed"
                    "stock_available" -> "Stock Available"
                    "other_vendor" -> "Other Vendor"
                    else -> if (item.reason.isEmpty()) "Reached" else item.reason.replace("_", " ").lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
                }
                holder.tvQuantity.text = "Reason: $reasonText"
                holder.tvAmount.text = ""
                holder.tvStatus.text = "REACHED"
            }
            else -> {
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_edittext_bg)
                holder.tvStatus.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.red)
                holder.detailsLayout.visibility = View.GONE
            }
        }
    }
}
