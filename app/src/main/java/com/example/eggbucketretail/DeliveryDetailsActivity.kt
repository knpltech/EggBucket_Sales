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
import com.google.firebase.firestore.Source
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
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
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

        val uid = customer?.uid ?: run {
            Toast.makeText(this, "Error: Customer data missing", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val todayDate = sdf.format(Date())

        // Fetch Agent Name from local cache (Instantly available)
        val prefs = getSharedPreferences("EggBucketPrefs", Context.MODE_PRIVATE)
        val agentName = prefs.getString("agent_name", "Unknown") ?: "Unknown"

        val db = FirebaseFirestore.getInstance()
        val customerRef = db.collection("customers").document(uid)

        // UPDATE DATA DIRECTLY: Read from cache, perform pruning, and save both to the map and subcollection
        customerRef.get(Source.CACHE).addOnCompleteListener { task ->
            val updateData = hashMapOf<String, Any>()
            val newEntry = hashMapOf(
                "agentId" to currentUserId,
                "agentName" to agentName,
                "status" to "delivered",
                "time" to FieldValue.serverTimestamp(),
                "quantity" to quantity,
                "cashAmount" to cash,
                "upiAmount" to upi,
                "totalAmount" to (cash + upi)
            )

            if (task.isSuccessful && task.result != null) {
                val document = task.result
                val last8Days = document.get("last8Days") as? Map<String, Any>
                val updatedLast8Days = mutableMapOf<String, Any>()

                if (last8Days != null) {
                    for ((dateKey, value) in last8Days) {
                        if (!isOlderThan30Days(dateKey, todayDate) && dateKey != todayDate) {
                            updatedLast8Days[dateKey] = value
                        }
                    }
                }
                updatedLast8Days[todayDate] = newEntry
                updateData["last8Days"] = updatedLast8Days
            } else {
                updateData["last8Days.$todayDate"] = newEntry
            }

            updateData["todayOverride.status"] = "delivered"

            customerRef.update(updateData)

            // Sync with deliveries subcollection
            val deliveriesCollectionRef = customerRef.collection("deliveries")
            deliveriesCollectionRef.document(todayDate).set(newEntry)

            // Clean up the subcollection older than 30 days
            deliveriesCollectionRef.get(Source.CACHE).addOnCompleteListener { subcollectionTask ->
                if (subcollectionTask.isSuccessful && subcollectionTask.result != null) {
                    for (doc in subcollectionTask.result.documents) {
                        val docId = doc.id
                        if (isOlderThan30Days(docId, todayDate)) {
                            deliveriesCollectionRef.document(docId).delete()
                        }
                    }
                }
            }
        }

        Toast.makeText(this, "Delivery submitted! Syncing in background.", Toast.LENGTH_SHORT).show()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 500)
    }
}
