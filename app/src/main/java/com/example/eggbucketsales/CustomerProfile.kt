package com.example.eggbucketsales

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.eggbucketsales.Adapters.DeliveryHistory
import com.example.eggbucketsales.Adapters.DeliveryHistoryAdapter
import com.example.eggbucketsales.Models.Customer
import com.example.eggbucketsales.databinding.ActivityCustomerProfileBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class CustomerProfile : AppCompatActivity() {
    private lateinit var binding: ActivityCustomerProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.maincolor)
        // Toolbar setup with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Customer Profile"

        // Handle toolbar back button
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Retrieve customer object
        val customer = intent.getParcelableExtra<Customer>("customer")

        if (customer != null) {
            binding.customerName.text = customer.name
            binding.customerPhone.text = customer.phone
            binding.customerBusiness.text = customer.business
            binding.customerLocation.text = customer.location

            Glide.with(this)
                .load(customer.imageUrl)
                .placeholder(R.drawable.logo)
                .error(R.drawable.logo)
                .into(binding.customerProfileImage)
        } else {
            Toast.makeText(this, "Customer data not found", Toast.LENGTH_SHORT).show()
            finish() // go back if data not passed
        }

        binding.customerPhoneCall.setOnClickListener {
            val phoneNumber = binding.customerPhone.text.toString().trim()
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        }
        //photo full screen
        binding.customerProfileImage.setOnClickListener {
            val intent = Intent(this, FullScreenImage::class.java)
            intent.putExtra("image_url", customer?.imageUrl.toString())
            startActivity(intent)
        }

        fetchDeliveryHistory(customer?.uid)
    }

    private fun fetchDeliveryHistory(customerUid: String?) {
        if (customerUid == null) return

        FirebaseFirestore.getInstance().collection("customers").document(customerUid)
            .addSnapshotListener { document, error ->
                if (error != null || document == null || !document.exists()) return@addSnapshotListener

                val last8Days = document.get("last8Days") as? Map<String, Any> ?: return@addSnapshotListener
                val historyList = mutableListOf<DeliveryHistory>()

                // Sort dates in descending order
                val sortedDates = last8Days.keys.sortedDescending()

                for (date in sortedDates) {
                    val data = last8Days[date] as? Map<String, Any> ?: continue
                    historyList.add(
                        DeliveryHistory(
                            date = date,
                            status = data["status"] as? String ?: "unknown",
                            quantity = (data["quantity"] as? Long)?.toInt() ?: 0,
                            totalAmount = (data["totalAmount"] as? Long)?.toInt() ?: 0,
                            agentName = data["agentName"] as? String ?: "Unknown",
                            reason = data["reason"] as? String ?: ""
                        )
                    )
                }

                binding.rvHistory.layoutManager = LinearLayoutManager(this)
                val adapter = DeliveryHistoryAdapter()
                binding.rvHistory.adapter = adapter
                adapter.submitList(historyList)
            }
    }
}
