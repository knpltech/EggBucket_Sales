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
import com.example.eggbucketsales.Repository.CustomerRepository

class CustomerList : Fragment() {

    private lateinit var adapter: CustomerAdapter
    private lateinit var customerRecyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val customerRepository = CustomerRepository()
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

        customerRepository.getSalesCustomers(
            onSuccess = { filteredList ->
                customers.clear()
                customers.addAll(filteredList)
                adapter.submitList(customers.toList())
                swipeRefresh.isRefreshing = false
            },
            onFailure = { e ->
                context?.let {
                    Toast.makeText(it, "Failed to load customers: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                swipeRefresh.isRefreshing = false
            }
        )
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
