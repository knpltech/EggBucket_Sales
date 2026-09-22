package com.example.eggbucketsales

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.eggbucketsales.Adapters.CustomerAdapter
import com.example.eggbucketsales.Models.Customer
import com.google.firebase.firestore.FirebaseFirestore

class CustomerList : Fragment() {

    private lateinit var adapter: CustomerAdapter
    private lateinit var customerRecyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val firestore = FirebaseFirestore.getInstance()
    private val customers = mutableListOf<Customer>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_customer_list, container, false)

        customerRecyclerView = view.findViewById(R.id.customerRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefreshForCustomerList)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CustomerAdapter { customer ->
            val intent = Intent(requireContext(), CustomerProfile::class.java)
            intent.putExtra("customer", customer)
            startActivity(intent)
        }


        customerRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        customerRecyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            loadCustomers()
        }

        loadCustomers()
    }

    private fun loadCustomers() {
        swipeRefresh.isRefreshing = true

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val dateList = mutableListOf<String>()
        for (i in 0..7) {
            val dCal = cal.clone() as java.util.Calendar
            dCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            dateList.add(sdf.format(dCal.time))
        }

        firestore.collection("customers")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val list = querySnapshot.documents.mapNotNull { doc ->
                    val customer = doc.toObject(Customer::class.java) ?: return@mapNotNull null
                    val last8Days = doc.get("last8Days") as? Map<*, *>
                    val explicitCat = doc.getString("category") ?: doc.getString("Peak_Frequency") ?: doc.getString("peakFrequency")

                    var deliveredCountLast7Days = 0
                    var deliveredCountLast8Days = 0

                    if (last8Days != null) {
                        for (i in 1..7) {
                            val dateKey = dateList[i]
                            val entry = last8Days[dateKey] as? Map<*, *>
                            if ((entry?.get("status") as? String)?.lowercase() == "delivered") {
                                deliveredCountLast7Days++
                            }
                        }
                        for (i in 0..7) {
                            val dateKey = dateList[i]
                            val entry = last8Days[dateKey] as? Map<*, *>
                            if ((entry?.get("status") as? String)?.lowercase() == "delivered") {
                                deliveredCountLast8Days++
                            }
                        }
                    }

                    val computedCategory = "D$deliveredCountLast7Days"
                    val isExplicitD0D1 = explicitCat?.equals("D0", ignoreCase = true) == true ||
                            explicitCat?.equals("D1", ignoreCase = true) == true
                    val isD0OrD1 = isExplicitD0D1 || (deliveredCountLast7Days == 0) || (deliveredCountLast8Days == 0) || (last8Days == null || last8Days.isEmpty())

                    var latestDate: String? = null
                    var latestQty = 0
                    var latestAmount = 0
                    var latestAgent: String? = null

                    if (last8Days != null && last8Days.isNotEmpty()) {
                        val deliveredEntries = mutableListOf<Pair<String, Map<*, *>>>()
                        for ((key, value) in last8Days) {
                            val dateStr = key as? String ?: continue
                            val entryMap = value as? Map<*, *> ?: continue
                            if ((entryMap["status"] as? String)?.lowercase() == "delivered") {
                                deliveredEntries.add(Pair(dateStr, entryMap))
                            }
                        }
                        if (deliveredEntries.isNotEmpty()) {
                            deliveredEntries.sortByDescending { it.first }
                            val mostRecent = deliveredEntries.first()
                            latestDate = mostRecent.first
                            val entryMap = mostRecent.second

                            latestQty = (entryMap["quantity"] as? Number)?.toInt()
                                ?: (entryMap["quantity"] as? String)?.toIntOrNull() ?: 0

                            val total = (entryMap["totalAmount"] as? Number)?.toInt()
                                ?: (entryMap["totalAmount"] as? String)?.toIntOrNull()
                            if (total != null && total > 0) {
                                latestAmount = total
                            } else {
                                val cash = (entryMap["cashAmount"] as? Number)?.toInt()
                                    ?: (entryMap["cashAmount"] as? String)?.toIntOrNull() ?: 0
                                val upi = (entryMap["upiAmount"] as? Number)?.toInt()
                                    ?: (entryMap["upiAmount"] as? String)?.toIntOrNull() ?: 0
                                latestAmount = cash + upi
                            }
                            latestAgent = entryMap["agentName"] as? String
                        }
                    }

                    customer.copy(
                        uid = doc.id,
                        lastDeliveredDate = latestDate,
                        lastDeliveredQty = latestQty,
                        lastDeliveredAmount = latestAmount,
                        lastDeliveredAgent = latestAgent,
                        category = if (explicitCat?.isNotBlank() == true) explicitCat else computedCategory,
                        isD0OrD1 = isD0OrD1
                    )
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                customers.clear()
                customers.addAll(list)
                adapter.submitList(customers.toList()) // Pass a copy
            }
            .addOnFailureListener {
                // handle failure if needed
            }
            .addOnCompleteListener {
                swipeRefresh.isRefreshing = false
            }
    }

    fun searchCustomer(query: String) {
        val filtered = if (query.isEmpty()) {
            customers
        } else {
            customers.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.business.contains(query, ignoreCase = true) ||
                        it.phone.contains(query, ignoreCase = true)
            }
        }

        adapter.submitList(filtered.toList())
    }


}
