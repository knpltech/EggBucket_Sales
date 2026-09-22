package com.example.eggbucketsales.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.example.eggbucketsales.Models.Customer
import com.example.eggbucketsales.R

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class CustomerAdapter(
    private val onItemClick: (Customer) -> Unit
) : ListAdapter<Customer, CustomerAdapter.CustomerViewHolder>(CustomerDiffCallback()) {

    class CustomerDiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem == newItem
        }
    }

    inner class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val customerName: TextView = itemView.findViewById(R.id.customerName)
        private val businessName: TextView = itemView.findViewById(R.id.businessName)
        private val customerPhone: TextView = itemView.findViewById(R.id.customerPhone)
        private val customerImage: ImageView = itemView.findViewById(R.id.customerImage)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.customerimageLoadingProgress)
        private val categoryBadge: TextView = itemView.findViewById(R.id.customerCategoryBadge)
        private val lastDeliveryText: TextView = itemView.findViewById(R.id.customerLastDeliveryText)

        fun bind(customer: Customer) {
            customerName.text = customer.name
            businessName.text = customer.business
            customerPhone.text = customer.phone

            val isNew = isCustomerNewlyAdded(customer)
            if (isNew) {
                categoryBadge.visibility = View.VISIBLE
                categoryBadge.text = "NEW"
                categoryBadge.setBackgroundResource(R.drawable.badge_new_bg)
            } else if (customer.category.isNotBlank() || customer.isD0OrD1) {
                categoryBadge.visibility = View.VISIBLE
                categoryBadge.text = if (customer.category.isNotBlank()) customer.category else "D0"
                if (customer.isD0OrD1) {
                    categoryBadge.setBackgroundResource(R.drawable.badge_d0_bg)
                } else {
                    categoryBadge.setBackgroundResource(R.drawable.badge_bg)
                }
            } else {
                categoryBadge.visibility = View.GONE
            }

            if (!customer.lastDeliveredDate.isNullOrBlank()) {
                lastDeliveryText.visibility = View.VISIBLE
                val dateFmt = formatDeliveryDate(customer.lastDeliveredDate)
                val qtyStr = if (customer.lastDeliveredQty > 0) " • ${customer.lastDeliveredQty} trays" else ""
                val amtStr = if (customer.lastDeliveredAmount > 0) " (₹${customer.lastDeliveredAmount})" else ""
                lastDeliveryText.text = "Last: $dateFmt$qtyStr$amtStr"
            } else if (customer.isD0OrD1 && !isNew) {
                lastDeliveryText.visibility = View.VISIBLE
                lastDeliveryText.text = "No orders in last 8 days"
            } else {
                lastDeliveryText.visibility = View.GONE
            }

            progressBar.visibility = View.VISIBLE
            Glide.with(itemView.context)
                .load(customer.imageUrl)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        return false // Glide will still handle the error placeholder
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        return false // Glide will handle setting the image
                    }
                })
                .error(R.drawable.logo) // Optional fallback
                .into(customerImage)

            itemView.setOnClickListener {
                onItemClick(customer)
            }
        }

        private fun isCustomerNewlyAdded(customer: Customer?): Boolean {
            if (customer == null || customer.createdAt <= 0L) return false
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -45)
            val last45DaysMillis = calendar.timeInMillis
            return customer.createdAt >= last45DaysMillis
        }

        private fun formatDeliveryDate(dateStr: String?): String {
            if (dateStr.isNullOrBlank()) return ""
            return try {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val formatter = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
                val d = parser.parse(dateStr)
                if (d != null) formatter.format(d) else dateStr
            } catch (e: Exception) {
                dateStr
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.customer_item, parent, false)
        return CustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemCount(): Int = currentList.size
}
