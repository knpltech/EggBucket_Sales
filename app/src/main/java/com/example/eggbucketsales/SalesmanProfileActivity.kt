package com.example.eggbucketsales

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.eggbucketsales.Adapters.AddedCustomerAdapter
import com.example.eggbucketsales.Models.Customer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar

class SalesmanProfileActivity : AppCompatActivity() {

    private lateinit var profileInitialText: TextView
    private lateinit var profileSalesmanName: TextView
    private lateinit var profileSalesmanRole: TextView
    private lateinit var totalCustomersCount: TextView
    private lateinit var addedCustomersRecyclerView: RecyclerView
    private lateinit var emptyCustomersTextView: TextView
    private lateinit var profileProgressBar: ProgressBar

    // Stats card views
    private lateinit var tvLoadingCount: TextView
    private lateinit var tvReturnCount: TextView
    private lateinit var tvTraysSold: TextView
    private lateinit var tvCashHandover: TextView
    private lateinit var tvUpiHandover: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvUpiBalance: TextView

    private var salesmanRoute: String? = null
    private var salesmanOutlet: String? = null
    private var salesmanOutletId: String? = null

    private lateinit var adapter: AddedCustomerAdapter
    private var customerListener: ListenerRegistration? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_salesman_profile)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.profileToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val redirectToMap = {
            val intent = android.content.Intent(this, SalesManMainScreen::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        toolbar.setNavigationOnClickListener { redirectToMap() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                redirectToMap()
            }
        })

        profileInitialText = findViewById(R.id.profileInitialText)
        profileSalesmanName = findViewById(R.id.profileSalesmanName)
        profileSalesmanRole = findViewById(R.id.profileSalesmanRole)
        totalCustomersCount = findViewById(R.id.totalCustomersCount)
        addedCustomersRecyclerView = findViewById(R.id.addedCustomersRecyclerView)
        emptyCustomersTextView = findViewById(R.id.emptyCustomersTextView)
        profileProgressBar = findViewById(R.id.profileProgressBar)

        // Initialize Stats views
        tvLoadingCount = findViewById(R.id.tv_loading_count)
        tvReturnCount = findViewById(R.id.tv_return_count)
        tvTraysSold = findViewById(R.id.tv_trays_sold)
        tvCashHandover = findViewById(R.id.tv_cash_handover)
        tvUpiHandover = findViewById(R.id.tv_upi_handover)
        tvBalance = findViewById(R.id.tv_balance)
        tvUpiBalance = findViewById(R.id.tv_upi_balance)

        adapter = AddedCustomerAdapter()
        addedCustomersRecyclerView.layoutManager = LinearLayoutManager(this)
        addedCustomersRecyclerView.adapter = adapter

        fetchSalesmanProfile()
        listenAddedCustomers()
    }

    override fun onSupportNavigateUp(): Boolean {
        val intent = android.content.Intent(this, SalesManMainScreen::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
        return true
    }

    private fun fetchSalesmanProfile() {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: return
        val email = currentUser.email ?: ""

        firestore.collection("Salesman").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                var name = doc.getString("name") ?: doc.getString("salesman_name") ?: ""
                salesmanRoute = doc.getString("route")
                salesmanOutlet = doc.getString("outlet")
                salesmanOutletId = doc.get("outletId")?.toString()

                if (name.isBlank()) {
                    // Fallback to DeliveryMan doc
                    firestore.collection("DeliveryMan").document(uid)
                        .get()
                        .addOnSuccessListener { dDoc ->
                            val dName = dDoc.getString("name") ?: dDoc.getString("deliveryman_name") ?: dDoc.getString("agent_name") ?: ""
                            salesmanRoute = dDoc.getString("route")
                            salesmanOutlet = dDoc.getString("outlet")
                            salesmanOutletId = dDoc.get("outletId")?.toString()

                            val resolvedName = if (dName.isNotBlank()) dName else {
                                if (email.contains("@")) {
                                    email.substringBefore("@").replace(".", " ").capitalizeWords()
                                } else {
                                    "Sales Agent"
                                }
                            }
                            updateProfileHeader(resolvedName)
                            loadProfileStats(resolvedName)
                        }
                        .addOnFailureListener {
                            val fallbackName = if (email.contains("@")) {
                                email.substringBefore("@").replace(".", " ").capitalizeWords()
                            } else {
                                "Sales Agent"
                            }
                            updateProfileHeader(fallbackName)
                            loadProfileStats(fallbackName)
                        }
                } else {
                    updateProfileHeader(name)
                    loadProfileStats(name)
                }
            }
            .addOnFailureListener {
                // Fallback to DeliveryMan
                firestore.collection("DeliveryMan").document(uid)
                    .get()
                    .addOnSuccessListener { dDoc ->
                        val dName = dDoc.getString("name") ?: dDoc.getString("deliveryman_name") ?: dDoc.getString("agent_name") ?: ""
                        salesmanRoute = dDoc.getString("route")
                        salesmanOutlet = dDoc.getString("outlet")
                        salesmanOutletId = dDoc.get("outletId")?.toString()

                        val resolvedName = if (dName.isNotBlank()) dName else {
                            if (email.contains("@")) {
                                email.substringBefore("@").replace(".", " ").capitalizeWords()
                            } else {
                                "Sales Agent"
                            }
                        }
                        updateProfileHeader(resolvedName)
                        loadProfileStats(resolvedName)
                    }
                    .addOnFailureListener {
                        val fallbackName = if (email.contains("@")) {
                            email.substringBefore("@").replace(".", " ").capitalizeWords()
                        } else {
                            "Sales Agent"
                        }
                        updateProfileHeader(fallbackName)
                        loadProfileStats(fallbackName)
                    }
            }
    }

    private fun updateProfileHeader(name: String) {
        profileSalesmanName.text = name
        profileSalesmanRole.text = "Sales Executive"
        val initial = name.trim().take(1).uppercase()
        profileInitialText.text = if (initial.isNotEmpty()) initial else "S"
    }

    private fun loadProfileStats(agentName: String) {
        fetchStats(Source.CACHE, agentName) {
            fetchStats(Source.SERVER, agentName) {}
        }
    }

    private fun getSecondaryDb(): FirebaseFirestore? {
        return try {
            val secondaryApp = FirebaseApp.getApps(this).find { it.name == "inventoryApp" }
                ?: run {
                    val options = FirebaseOptions.Builder()
                        .setProjectId("inventory-management-4dd5e")
                        .setApplicationId("1:64621620700:web:f8f7e3787bc01f7fd53319")
                        .setApiKey("AIzaSyCG1I28G97S-rdVnOxgp7qmEInBgVcACPE")
                        .build()
                    FirebaseApp.initializeApp(this, options, "inventoryApp")
                }
            FirebaseFirestore.getInstance(secondaryApp)
        } catch (e: Exception) {
            null
        }
    }

    private fun matchesAgentRecord(
        document: com.google.firebase.firestore.DocumentSnapshot,
        currentUserId: String,
        agentNames: List<String>
    ): Boolean {
        val storedIds = listOf("agentId", "supervisorId", "agentUID", "agentUid", "uid")
            .mapNotNull { field -> document.get(field)?.toString()?.trim() }
            .filter { it.isNotEmpty() }

        if (currentUserId in storedIds) return true

        val storedNames = listOf(
            "agentName", "supervisorName", "deliveryManName", "deliverymanName",
            "deliveryBoyName", "agent_name"
        ).mapNotNull { field -> document.getString(field)?.trim() }
            .filter { it.isNotEmpty() }

        return storedNames.any { storedName ->
            agentNames.any { agentName -> namesReferToSameAgent(storedName, agentName) }
        }
    }

    private fun namesReferToSameAgent(first: String, second: String): Boolean {
        fun normalized(value: String) = value.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), "")

        val normalizedFirst = normalized(first)
        val normalizedSecond = normalized(second)
        if (normalizedFirst.isEmpty() || normalizedSecond.isEmpty()) return false
        if (normalizedFirst == normalizedSecond ||
            normalizedFirst.contains(normalizedSecond) ||
            normalizedSecond.contains(normalizedFirst)
        ) return true

        val firstWord = first.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase(Locale.ROOT)
        val secondWord = second.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase(Locale.ROOT)
        return !firstWord.isNullOrEmpty() && firstWord == secondWord
    }

    private fun matchesOutletRecord(
        document: com.google.firebase.firestore.DocumentSnapshot,
        agentOutlet: String?,
        agentOutletId: String?,
        matchesCurrentAgent: (com.google.firebase.firestore.DocumentSnapshot) -> Boolean
    ): Boolean {
        val expectedOutlet = normalizeOutlet(agentOutlet)
        val storedOutletIds = listOf("outletId", "outletID")
            .mapNotNull { field -> document.get(field)?.toString()?.trim() }

        if (!agentOutletId.isNullOrBlank() && agentOutletId in storedOutletIds) return true

        val storedOutlets = listOf("outletName", "outlet")
            .mapNotNull { field -> document.getString(field) }
        if (expectedOutlet.isNotEmpty() && storedOutlets.any { normalizeOutlet(it) == expectedOutlet }) {
            return true
        }

        return matchesCurrentAgent(document)
    }

    private fun normalizeOutlet(outlet: String?): String = outlet.orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace("^eggbucket\\s+".toRegex(), "")
        .replace("\\s+".toRegex(), " ")

    private fun fetchStats(source: Source, agentName: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val agentNames = listOfNotNull(agentName, salesmanRoute)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val todayDate = sdf.format(Date())

        firestore.collection("customers").get(source)
            .addOnCompleteListener { customersTask ->
                var totalTrays = 0
                var totalUpi = 0
                var totalCash = 0

                val snapshot = if (customersTask.isSuccessful) customersTask.result else null
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val last8Days = doc.get("last8Days") as? Map<*, *>
                        val todayData = last8Days?.get(todayDate) as? Map<*, *>
                        val agentId = todayData?.get("agentId") as? String
                        val status = todayData?.get("status") as? String

                        if (status == "delivered" && agentId == currentUserId) {
                            val qty = (todayData["quantity"] as? Long)?.toInt()
                                ?: (todayData["quantity"] as? Double)?.toInt() ?: 0
                            val upi = (todayData["upiAmount"] as? Long)?.toInt()
                                ?: (todayData["upiAmount"] as? Double)?.toInt() ?: 0
                            val cash = (todayData["cashAmount"] as? Long)?.toInt()
                                ?: (todayData["cashAmount"] as? Double)?.toInt() ?: 0

                            totalTrays += qty
                            totalUpi += upi
                            totalCash += cash
                        }
                    }
                }

                tvTraysSold.text = totalTrays.toString()

                val secondaryDb = getSecondaryDb()
                if (secondaryDb != null) {
                    val matchesAgent = { doc: com.google.firebase.firestore.DocumentSnapshot ->
                        matchesAgentRecord(doc, currentUserId, agentNames)
                    }
                    val matchesOutlet = { doc: com.google.firebase.firestore.DocumentSnapshot ->
                        matchesOutletRecord(doc, salesmanOutlet, salesmanOutletId, matchesAgent)
                    }

                    secondaryDb.collection("cash_handover_entries")
                        .whereEqualTo("dateKey", todayDate)
                        .get(source)
                        .addOnCompleteListener { cashHandoverTask ->
                            var totalCashHandover = 0
                            val cashHandoverSnap = if (cashHandoverTask.isSuccessful) cashHandoverTask.result else null
                            if (cashHandoverSnap != null) {
                                for (doc in cashHandoverSnap.documents) {
                                    if (matchesAgent(doc)) {
                                        val valCash = doc.get("Cash") ?: doc.get("cash")
                                        val cashVal = when (valCash) {
                                            is Long -> valCash.toInt()
                                            is Double -> valCash.toInt()
                                            is Number -> valCash.toInt()
                                            is String -> valCash.toDoubleOrNull()?.toInt() ?: 0
                                            else -> 0
                                        }
                                        totalCashHandover += cashVal
                                    }
                                }
                            }

                            secondaryDb.collection("food_allowance_entries")
                                .whereEqualTo("dateKey", todayDate)
                                .get(source)
                                .addOnCompleteListener { foodAllowanceTask ->
                                    var totalFoodAllowance = 0
                                    val foodAllowanceSnap = if (foodAllowanceTask.isSuccessful) foodAllowanceTask.result else null
                                    if (foodAllowanceSnap != null) {
                                        for (doc in foodAllowanceSnap.documents) {
                                            if (matchesAgent(doc)) {
                                                val valCash = doc.get("Cash") ?: doc.get("cash")
                                                val cashVal = when (valCash) {
                                                    is Long -> valCash.toInt()
                                                    is Double -> valCash.toInt()
                                                    is Number -> valCash.toInt()
                                                    is String -> valCash.toDoubleOrNull()?.toInt() ?: 0
                                                    else -> 0
                                                }
                                                totalFoodAllowance += cashVal
                                            }
                                        }
                                    }

                                    secondaryDb.collection("loading_entries")
                                        .whereEqualTo("dateKey", todayDate)
                                        .get(source)
                                        .addOnCompleteListener { loadingTask ->
                                            var totalLoading = 0
                                            val loadingSnap = if (loadingTask.isSuccessful) loadingTask.result else null
                                            if (loadingSnap != null) {
                                                for (doc in loadingSnap.documents) {
                                                    if (matchesOutlet(doc)) {
                                                        val valQty = doc.get("quantity")
                                                        val qtyVal = when (valQty) {
                                                            is Long -> valQty.toInt()
                                                            is Double -> valQty.toInt()
                                                            is Number -> valQty.toInt()
                                                            is String -> valQty.toDoubleOrNull()?.toInt() ?: 0
                                                            else -> 0
                                                        }
                                                        totalLoading += qtyVal
                                                    }
                                                }
                                            }

                                            secondaryDb.collection("return_load_entries")
                                                .whereEqualTo("dateKey", todayDate)
                                                .get(source)
                                                .addOnCompleteListener { returnLoadTask ->
                                                    var totalReturns = 0
                                                    val returnLoadSnap = if (returnLoadTask.isSuccessful) returnLoadTask.result else null
                                                    if (returnLoadSnap != null) {
                                                        for (doc in returnLoadSnap.documents) {
                                                            if (matchesOutlet(doc)) {
                                                                val valQty = doc.get("quantity")
                                                                val qtyVal = when (valQty) {
                                                                    is Long -> valQty.toInt()
                                                                    is Double -> valQty.toInt()
                                                                    is Number -> valQty.toInt()
                                                                    is String -> valQty.toDoubleOrNull()?.toInt() ?: 0
                                                                    else -> 0
                                                                }
                                                                totalReturns += qtyVal
                                                            }
                                                        }
                                                    }

                                                    secondaryDb.collection("upi_handover_entries")
                                                        .whereEqualTo("dateKey", todayDate)
                                                        .get(source)
                                                        .addOnCompleteListener { upiHandoverTask ->
                                                            var totalUpiHandover = 0
                                                            var latestDocTimestamp = -1L
                                                            var hasMatchingDoc = false
                                                            val upiHandoverSnap = if (upiHandoverTask.isSuccessful) upiHandoverTask.result else null
                                                            if (upiHandoverSnap != null) {
                                                                for (doc in upiHandoverSnap.documents) {
                                                                    if (matchesAgent(doc)) {
                                                                        val valCash = doc.get("Cash") ?: doc.get("cash") ?: doc.get("amount") ?: doc.get("upi")
                                                                        val cashVal = when (valCash) {
                                                                            is Long -> valCash.toInt()
                                                                            is Double -> valCash.toInt()
                                                                            is Number -> valCash.toInt()
                                                                            is String -> valCash.toDoubleOrNull()?.toInt() ?: 0
                                                                            else -> 0
                                                                        }

                                                                        var docTime = -1L
                                                                        val fields = listOf("updatedAt", "createdAt", "timestamp", "time", "created_at", "updated_at")
                                                                        for (field in fields) {
                                                                            val valTime = doc.get(field)
                                                                            when (valTime) {
                                                                                is com.google.firebase.Timestamp -> { docTime = valTime.toDate().time; break }
                                                                                is java.util.Date -> { docTime = valTime.time; break }
                                                                                is Long -> { docTime = valTime; break }
                                                                                is Double -> { docTime = valTime.toLong(); break }
                                                                                is String -> {
                                                                                    val parsed = valTime.toLongOrNull()
                                                                                    if (parsed != null) { docTime = parsed; break }
                                                                                }
                                                                            }
                                                                        }

                                                                        if (!hasMatchingDoc || docTime > latestDocTimestamp || (docTime == latestDocTimestamp && docTime != -1L)) {
                                                                            totalUpiHandover = cashVal
                                                                            latestDocTimestamp = docTime
                                                                            hasMatchingDoc = true
                                                                        } else if (latestDocTimestamp == -1L && docTime == -1L) {
                                                                            totalUpiHandover = cashVal
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            val balance = totalCash - totalCashHandover - totalFoodAllowance
                                                            val upiBalance = totalUpi - totalUpiHandover

                                                            tvCashHandover.text = "₹$totalCashHandover"
                                                            tvUpiHandover.text = "₹$totalUpiHandover"
                                                            tvBalance.text = "₹$balance"
                                                            tvUpiBalance.text = "₹$upiBalance"
                                                            tvLoadingCount.text = totalLoading.toString()
                                                            tvReturnCount.text = totalReturns.toString()

                                                            val success = customersTask.isSuccessful
                                                            onComplete(success)
                                                        }
                                                }
                                        }
                                }
                        }
                } else {
                    tvCashHandover.text = "₹0"
                    tvUpiHandover.text = "₹0"
                    tvBalance.text = "₹$totalCash"
                    tvUpiBalance.text = "₹$totalUpi"
                    tvLoadingCount.text = "0"
                    tvReturnCount.text = "0"

                    val success = customersTask.isSuccessful
                    onComplete(success)
                }
            }
    }

    private fun listenAddedCustomers() {
        val currentUid = auth.currentUser?.uid ?: return
        profileProgressBar.visibility = View.VISIBLE

        customerListener?.remove()
        customerListener = firestore.collection("customers")
            .whereEqualTo("createdby", currentUid)
            .addSnapshotListener { snapshot, error ->
                profileProgressBar.visibility = View.GONE
                if (snapshot != null) {
                    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -45) // Last 45 Days
                    val last45DaysMillis = calendar.timeInMillis

                    val addedCustomerList = snapshot.documents.mapNotNull { doc ->
                        val customer = doc.toObject(Customer::class.java)
                        customer?.copy(uid = doc.id)
                    }.filter { customer ->
                        customer.createdAt >= last45DaysMillis
                    }.sortedByDescending { it.createdAt }

                    adapter.submitList(addedCustomerList)
                    totalCustomersCount.text = "Last 45 Days: ${addedCustomerList.size}"

                    if (addedCustomerList.isEmpty()) {
                        emptyCustomersTextView.visibility = View.VISIBLE
                        addedCustomersRecyclerView.visibility = View.GONE
                    } else {
                        emptyCustomersTextView.visibility = View.GONE
                        addedCustomersRecyclerView.visibility = View.VISIBLE
                    }
                } else {
                    emptyCustomersTextView.visibility = View.VISIBLE
                    addedCustomersRecyclerView.visibility = View.GONE
                }
            }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        customerListener?.remove()
    }
}
