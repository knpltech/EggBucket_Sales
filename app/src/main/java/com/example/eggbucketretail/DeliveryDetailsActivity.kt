package com.example.eggbucketretail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.eggbucketretail.Models.Customer
import com.example.eggbucketretail.databinding.ActivityDeliveryDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class DeliveryDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeliveryDetailsBinding
    private var customer: Customer? = null
    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeliveryDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customer = intent.getParcelableExtra("customer")
        
        setupUI()
        setupListeners()
        updateTotal()
        
        // Start party popper animation placeholder
        startAnimation()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        customer?.let {
            binding.customerName.text = it.name
            binding.customerAddress.text = it.location
            
            Glide.with(this)
                .load(it.imageUrl)
                .placeholder(R.drawable.logo)
                .into(binding.customerImage)
        }
        
        binding.tvQuantity.text = "$quantity Tray"

        // Hide banner by default
        binding.completionBanner.visibility = View.GONE
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddQuantity.setOnClickListener {
            quantity++
            binding.tvQuantity.text = if (quantity == 1) "$quantity Tray" else "$quantity Trays"
        }

        binding.btnMinusQuantity.setOnClickListener {
            if (quantity > 1) {
                quantity--
                binding.tvQuantity.text = if (quantity == 1) "$quantity Tray" else "$quantity Trays"
            }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTotal()
            }
        }

        binding.etCashAmount.addTextChangedListener(textWatcher)
        binding.etUpiAmount.addTextChangedListener(textWatcher)

        binding.etCashAmount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.cashContainer.setBackgroundResource(R.drawable.selected_payment_bg)
                binding.cashContainer.backgroundTintList = null
            } else {
                binding.cashContainer.setBackgroundResource(R.drawable.rounded_edittext_bg)
                binding.cashContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F8F9FA"))
            }
        }

        binding.etUpiAmount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.upiContainer.setBackgroundResource(R.drawable.selected_payment_bg)
                binding.upiContainer.backgroundTintList = null
            } else {
                binding.upiContainer.setBackgroundResource(R.drawable.rounded_edittext_bg)
                binding.upiContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F8F9FA"))
            }
        }

        binding.cashContainer.setOnClickListener {
            binding.etCashAmount.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etCashAmount, 0)
        }

        binding.upiContainer.setOnClickListener {
            binding.etUpiAmount.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etUpiAmount, 0)
        }

        binding.btnSubmit.setOnClickListener {
            submitDelivery()
        }
    }

    private fun updateTotal() {
        val cashStr = binding.etCashAmount.text.toString()
        val upiStr = binding.etUpiAmount.text.toString()
        
        val cash = if (cashStr.isEmpty()) 0 else cashStr.toIntOrNull() ?: 0
        val upi = if (upiStr.isEmpty()) 0 else upiStr.toIntOrNull() ?: 0

        val total = cash + upi
        binding.tvTotalAmount.text = "TOTAL: ₹$total"
    }

    private fun startAnimation() {
        // Animation logic removed as partyPopper view is not present in layout
    }

    private fun submitDelivery() {
        val cashStr = binding.etCashAmount.text.toString()
        val upiStr = binding.etUpiAmount.text.toString()

        val cash = if (cashStr.isEmpty()) 0 else cashStr.toIntOrNull() ?: 0
        val upi = if (upiStr.isEmpty()) 0 else upiStr.toIntOrNull() ?: 0
        
        if (cash + upi <= 0) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val uid = customer?.uid ?: return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        FirebaseFirestore.getInstance().collection("DeliveryMan").document(currentUserId).get()
            .addOnSuccessListener { agentDoc ->
                val agentName = agentDoc.getString("name") ?: "Unknown"

                FirebaseFirestore.getInstance().collection("customers").document(uid).get()
                    .addOnSuccessListener { customerDoc ->
                        val last8Days = customerDoc.get("last8Days") as? Map<String, Any> ?: emptyMap()
                        val updateData = hashMapOf<String, Any>()

                        // Cleanup logic: keep only dates from the last 8 days
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_YEAR, -8)
                        val thresholdDate = calendar.time

                        last8Days.keys.forEach { dateKey ->
                            try {
                                val entryDate = sdf.parse(dateKey)
                                if (entryDate != null && entryDate.before(thresholdDate)) {
                                    updateData["last8Days.$dateKey"] = FieldValue.delete()
                                }
                            } catch (e: Exception) {
                                // Ignore unparseable dates
                            }
                        }

                        // Add today's delivery data
                        updateData["last8Days.$todayDate"] = hashMapOf(
                            "agentId" to currentUserId,
                            "agentName" to agentName,
                            "status" to "delivered",
                            "time" to FieldValue.serverTimestamp(),
                            "quantity" to quantity,
                            "cashAmount" to cash,
                            "upiAmount" to upi,
                            "totalAmount" to (cash + upi)
                        )

                        FirebaseFirestore.getInstance()
                            .collection("customers")
                            .document(uid)
                            .update(updateData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Delivery updated successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                binding.progressBar.visibility = View.GONE
                                binding.btnSubmit.isEnabled = true
                                Toast.makeText(this, "Failed to update customer: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        binding.btnSubmit.isEnabled = true
                        Toast.makeText(this, "Failed to fetch customer: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
                Toast.makeText(this, "Failed to fetch agent info", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
                Toast.makeText(this, "Failed to fetch agent info", Toast.LENGTH_SHORT).show()
            }
    }
}
