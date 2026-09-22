package com.example.eggbucketsales

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.appcompat.widget.SearchView
import com.example.eggbucketsales.databinding.ActivitySalesManMainScreenBinding
import com.google.firebase.auth.FirebaseAuth

class SalesManMainScreen : AppCompatActivity() {

    private var isAddCustomerFragment = false
    private lateinit var binding: ActivitySalesManMainScreenBinding
    private var countListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesManMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.maincolor)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finishAffinity()
                } else {
                    Toast.makeText(this@SalesManMainScreen, "Click back again to exit the app", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // handling toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        listenTodayAddedCustomers()
        cacheSalesmanName()

        val bottomNavigation = binding.bottomNavigation
        bottomNavigation.itemIconTintList = resources.getColorStateList(R.color.bottom_nav_item_color, theme)
        bottomNavigation.itemTextColor = resources.getColorStateList(R.color.bottom_nav_item_color, theme)
        // handling click on items in bottom menu
        bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_map -> {
                    binding.salesToolbarTitle.text = "CUSTOMER MAP"
                    isAddCustomerFragment = false
                    CustomerMap() // customer map fragment
                }
                R.id.nav_add_customer -> {
                    binding.salesToolbarTitle.text = "ADD CUSTOMER"
                    isAddCustomerFragment = true
                    AddCustomer() //add customer fragment
                }
                R.id.nav_customer_list -> {
                    binding.salesToolbarTitle.text = "CUSTOMER LIST"
                    isAddCustomerFragment = false
                    CustomerList() // customer list fragment
                }
                else -> return@setOnItemSelectedListener false
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            invalidateOptionsMenu()

            true
        }

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_map
            binding.salesToolbarTitle.text = "CUSTOMER MAP"
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CustomerMap())
                .commit()
        }
    }

    private fun listenTodayAddedCustomers() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        countListener?.remove()
        countListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("customers")
            .whereEqualTo("createdby", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val startOfDayMillis = calendar.timeInMillis

                    val countToday = snapshot.documents.count { doc ->
                        val createdAt = doc.getLong("createdAt") ?: 0L
                        createdAt >= startOfDayMillis
                    }
                    binding.todayAddedCounter.text = "Today: $countToday"
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countListener?.remove()
    }

    // initalizing toolbar's menu
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_search)?.isVisible = !isAddCustomerFragment
        return super.onPrepareOptionsMenu(menu)
    }

    // creating search and logout menu in ttolbar
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.sales_toolbar_menu, menu)
        //hiding refresh button from sales toolbar
        val refreshItem = menu.findItem(R.id.action_refresh)
        refreshItem?.isVisible = false
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = "Search customer or business"

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true // We handle it in onQueryTextChange instead
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    when (currentFragment) {
                        is CustomerMap -> currentFragment.searchCustomer(it)
                        is CustomerList -> currentFragment.searchCustomer(it)
                    }
                }
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, SalesmanProfileActivity::class.java))
                true
            }
            R.id.action_logout -> {
                AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes") { _, _ ->
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this, LoginSelection::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .setNegativeButton("No", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun cacheSalesmanName() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
        val email = currentUser.email ?: ""

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("Salesman").document(uid).get()
            .addOnSuccessListener { doc ->
                var name = doc.getString("name") ?: doc.getString("salesman_name") ?: doc.getString("agent_name") ?: ""
                if (name.isBlank()) {
                    name = if (email.contains("@")) {
                        email.substringBefore("@").replace(".", " ")
                    } else {
                        "Sales Agent"
                    }
                }
                getSharedPreferences("EggBucketPrefs", MODE_PRIVATE)
                    .edit()
                    .putString("agent_name", name)
                    .apply()
            }
    }
}
