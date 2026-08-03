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

class SalesmanProfileActivity : AppCompatActivity() {

    private lateinit var profileInitialText: TextView
    private lateinit var profileSalesmanName: TextView
    private lateinit var profileSalesmanRole: TextView
    private lateinit var totalCustomersCount: TextView
    private lateinit var addedCustomersRecyclerView: RecyclerView
    private lateinit var emptyCustomersTextView: TextView
    private lateinit var profileProgressBar: ProgressBar

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

        adapter = AddedCustomerAdapter()
        addedCustomersRecyclerView.layoutManager = LinearLayoutManager(this)
        addedCustomersRecyclerView.adapter = adapter

        fetchSalesmanProfile()
        listenAddedCustomers()
    }

    private fun fetchSalesmanProfile() {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: return
        val email = currentUser.email ?: ""

        firestore.collection("Salesman").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                var name = doc.getString("name") ?: doc.getString("salesman_name") ?: ""
                if (name.isBlank()) {
                    name = if (email.contains("@")) {
                        email.substringBefore("@").replace(".", " ").capitalizeWords()
                    } else {
                        "Sales Agent"
                    }
                }
                updateProfileHeader(name)
            }
            .addOnFailureListener {
                val fallbackName = if (email.contains("@")) {
                    email.substringBefore("@").replace(".", " ").capitalizeWords()
                } else {
                    "Sales Agent"
                }
                updateProfileHeader(fallbackName)
            }
    }

    private fun updateProfileHeader(name: String) {
        profileSalesmanName.text = name
        profileSalesmanRole.text = "Sales Executive"
        val initial = name.trim().take(1).uppercase()
        profileInitialText.text = if (initial.isNotEmpty()) initial else "S"
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
                    val customerList = snapshot.documents.mapNotNull { doc ->
                        val customer = doc.toObject(Customer::class.java)
                        customer?.copy(uid = doc.id)
                    }.sortedByDescending { it.createdAt }

                    adapter.submitList(customerList)
                    totalCustomersCount.text = "Total: ${customerList.size}"

                    if (customerList.isEmpty()) {
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
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        customerListener?.remove()
    }
}
