package com.example.eggbucketsales

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
import com.example.eggbucketsales.Models.Customer
import com.example.eggbucketsales.databinding.ActivityDeliveryDetailsBinding
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
        
        if (customer?.status?.lowercase() == "delivered") {
            binding.btnSubmit.isEnabled = false
            binding.btnSubmit.text = "ALREADY DELIVERED"
            binding.completionBanner.visibility = View.VISIBLE
            Toast.makeText(this, "This customer is already delivered for today", Toast.LENGTH_SHORT).show()
        } else {
            binding.completionBanner.visibility = View.GONE
        }
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
    }

    private fun submitDelivery() {
        if (customer?.status?.lowercase() == "delivered") {
            Toast.makeText(this, "Customer is already delivered for today", Toast.LENGTH_SHORT).show()
            return
        }

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

        val customerLatLng = parseLatLng(customer?.location ?: "")
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)

        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && customerLatLng != null) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        loc.latitude, loc.longitude,
                        customerLatLng.latitude, customerLatLng.longitude,
                        results
                    )
                    val distanceInMeters = results[0]
                    if (distanceInMeters > 50f) {
                        Toast.makeText(this, "You must be within 50m of customer location to submit delivery. Current distance: ${distanceInMeters.toInt()}m", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                }
                processSubmission(uid, cash, upi)
            }.addOnFailureListener {
                processSubmission(uid, cash, upi)
            }
        } else {
            processSubmission(uid, cash, upi)
        }
    }

    private fun processSubmission(uid: String, cash: Int, upi: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val todayDate = sdf.format(Date())

        resolveAgentName(currentUserId) { resolvedAgentName ->
            val db = FirebaseFirestore.getInstance()
            val customerRef = db.collection("customers").document(uid)

            customerRef.get(Source.CACHE).addOnCompleteListener { task ->
                val updateData = hashMapOf<String, Any>()
                val newEntry = hashMapOf(
                    "agentId" to currentUserId,
                    "agentName" to resolvedAgentName,
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

                // Clean up subcollection older than 30 days
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

    private fun resolveAgentName(currentUserId: String, onResolved: (String) -> Unit) {
        val prefs = getSharedPreferences("EggBucketPrefs", Context.MODE_PRIVATE)
        val cachedName = prefs.getString("agent_name", "") ?: ""
        if (cachedName.isNotBlank() && cachedName != "Unknown") {
            onResolved(cachedName)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val fallbackName = if (email.contains("@")) {
            email.substringBefore("@").replace(".", " ")
        } else {
            "Sales Agent"
        }

        if (currentUserId.isBlank()) {
            onResolved(fallbackName)
            return
        }

        db.collection("Salesman").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                var name = doc.getString("name") ?: doc.getString("salesman_name") ?: doc.getString("agent_name") ?: ""
                if (name.isBlank()) {
                    db.collection("DeliveryMan").document(currentUserId).get()
                        .addOnSuccessListener { dDoc ->
                            var dName = dDoc.getString("name") ?: dDoc.getString("deliveryman_name") ?: dDoc.getString("agent_name") ?: ""
                            if (dName.isBlank()) dName = fallbackName
                            prefs.edit().putString("agent_name", dName).apply()
                            onResolved(dName)
                        }
                        .addOnFailureListener {
                            prefs.edit().putString("agent_name", fallbackName).apply()
                            onResolved(fallbackName)
                        }
                } else {
                    prefs.edit().putString("agent_name", name).apply()
                    onResolved(name)
                }
            }
            .addOnFailureListener {
                prefs.edit().putString("agent_name", fallbackName).apply()
                onResolved(fallbackName)
            }
    }

    private fun isOlderThan30Days(dateStr: String, todayStr: String): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return try {
            val date = sdf.parse(dateStr) ?: return false
            val today = sdf.parse(todayStr) ?: return false
            val diffInMillies = Math.abs(today.time - date.time)
            val diffInDays = diffInMillies / (1000 * 60 * 60 * 24)
            diffInDays > 30
        } catch (e: Exception) {
            false
        }
    }

    private fun parseLatLng(location: String): com.google.android.gms.maps.model.LatLng? {
        return try {
            val pattern = Regex("-?\\d+\\.\\d+")
            val matches = pattern.findAll(location).map { it.value.toDoubleOrNull() }.toList()
            if (matches.size >= 2 && matches[0] != null && matches[1] != null) {
                com.google.android.gms.maps.model.LatLng(matches[0]!!, matches[1]!!)
            } else {
                val parts = location.split(",").map { it.replace("[^0-9.-]".toRegex(), "").toDoubleOrNull() }
                if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
                    com.google.android.gms.maps.model.LatLng(parts[0]!!, parts[1]!!)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
