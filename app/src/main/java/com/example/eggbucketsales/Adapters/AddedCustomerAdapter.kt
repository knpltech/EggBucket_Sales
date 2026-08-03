package com.example.eggbucketsales.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.eggbucketsales.Models.Customer
import com.example.eggbucketsales.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddedCustomerAdapter : ListAdapter<Customer, AddedCustomerAdapter.AddedCustomerViewHolder>(CustomerDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    }

    class CustomerDiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem == newItem
        }
    }

    inner class AddedCustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val customerName: TextView = itemView.findViewById(R.id.addedCustomerName)
        private val businessName: TextView = itemView.findViewById(R.id.addedCustomerBusiness)
        private val customerDate: TextView = itemView.findViewById(R.id.addedCustomerDate)

        fun bind(customer: Customer) {
            customerName.text = customer.name
            businessName.text = customer.business
            
            if (customer.createdAt > 0) {
                customerDate.text = dateFormat.format(Date(customer.createdAt))
            } else {
                customerDate.text = "N/A"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddedCustomerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_added_customer, parent, false)
        return AddedCustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddedCustomerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
