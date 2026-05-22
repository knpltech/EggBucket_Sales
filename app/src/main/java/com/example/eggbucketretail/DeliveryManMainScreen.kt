package com.example.eggbucketretail

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eggbucketretail.databinding.ActivityDeliveryManMainScreenBinding
import com.google.firebase.auth.FirebaseAuth

class DeliveryManMainScreen : AppCompatActivity() {
    private var searchQueryListener: SearchView.OnQueryTextListener? = null
    private var isLoggingOut = false

    private lateinit var binding:ActivityDeliveryManMainScreenBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityDeliveryManMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.maincolor)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //toolbar setup
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarfordelivery)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        if (savedInstanceState == null) {
            binding.salesToolbarTitlefordelivery.text = "CUSTOMER MAP"
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_containerfordelivery, CustomerMapForDelivery())
                .commit()
        }

        fetchAndCacheAgentInfo()
    }

    private fun fetchAndCacheAgentInfo() {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("DeliveryMan")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Unknown"
                val prefs = getSharedPreferences("EggBucketPrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("agent_name", name).apply()
            }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.sales_toolbar_menu, menu)

        // Search setup
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = "Search customer or business"

        searchQueryListener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                if (isLoggingOut) return false // Skip if logging out

                newText?.let {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_containerfordelivery)
                    if (currentFragment is CustomerMapForDelivery && currentFragment.isAdded && !isFinishing && !isDestroyed) {
                        currentFragment.searchCustomer(it)
                    }
                }
                return true
            }

        }
        searchView?.setOnQueryTextListener(searchQueryListener)


        // Refresh button setup
        val refreshItem = menu.findItem(R.id.action_refresh)
        val refreshActionView = refreshItem.actionView
        val refreshIcon = refreshActionView?.findViewById<ImageView>(R.id.refresh_icon)

        refreshActionView?.setOnClickListener {
            // Start rotation animation
            val rotate = RotateAnimation(
                0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 500
                repeatCount = Animation.INFINITE
            }
            refreshIcon?.startAnimation(rotate)

            // Call fragment refresh with callback to stop animation
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_containerfordelivery)
            if (currentFragment is CustomerMapForDelivery) {
                currentFragment.refreshCustomerMap {
                    refreshIcon?.clearAnimation()
                }
            } else {
                refreshIcon?.clearAnimation()
            }
        }

        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_logout -> {
                AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes") { _, _ ->
                        isLoggingOut = true // 🔴 Set flag immediately

                        // Remove SearchView listener and clear focus
                        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarfordelivery)
                        val searchItem = toolbar.menu.findItem(R.id.action_search)
                        val searchView = searchItem?.actionView as? SearchView
                        searchView?.setOnQueryTextListener(null)
                        searchView?.clearFocus()

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
    override fun onDestroy() {
        super.onDestroy()
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarfordelivery)
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(null)
    }




}