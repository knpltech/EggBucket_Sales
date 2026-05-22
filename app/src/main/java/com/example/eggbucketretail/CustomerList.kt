package com.example.eggbucketretail

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
import com.example.eggbucketretail.Adapters.CustomerAdapter
import com.example.eggbucketretail.Models.Customer
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
        adapter = CustomerAdapter(customers) { customer ->
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

        firestore.collection("customers")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val list = querySnapshot.documents.mapNotNull { doc ->
                    val customer = doc.toObject(Customer::class.java)
                    customer?.copy(uid = doc.id)
                }
                customers.clear()
                customers.addAll(list)
                adapter.updateList(customers)
            }
            .addOnFailureListener {
                // handle failure if needed
            }
            .addOnCompleteListener {
                swipeRefresh.isRefreshing = false
            }
    }

    fun searchCustomer(query: String) {
        val filtered = customers.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.business.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true)
        }

        adapter.updateList(filtered)
    }


}
