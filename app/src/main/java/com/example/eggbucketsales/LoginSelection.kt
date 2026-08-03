package com.example.eggbucketsales

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eggbucketsales.databinding.ActivityLoginSelectionBinding
import com.google.firebase.auth.FirebaseAuth

class LoginSelection : AppCompatActivity() {
    private lateinit var binding: ActivityLoginSelectionBinding
    private var backPressedTime: Long = 0

    //location permission setting
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
            Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show()
            // Start using location here if needed
        } else {
            // Permission denied
            Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finishAffinity()
                } else {
                    Toast.makeText(this@LoginSelection, "Click back again to exit the app", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        checkLocationPermission()  //caaling function- location permission is given or not
        val currentUser = FirebaseAuth.getInstance().currentUser

        //checking if the user is logged in or not
        if (currentUser != null) {
            binding.loadingOverlay.visibility = android.view.View.VISIBLE
            val email = currentUser.email
            if (!email.isNullOrEmpty() && email.contains("sales")) {
                startActivity(Intent(this, SalesManMainScreen::class.java))
                finish()
            } else {
                FirebaseAuth.getInstance().signOut()
                binding.loadingOverlay.visibility = android.view.View.GONE
            }
        } else {
            FirebaseAuth.getInstance().signOut()
        }


        //handling login for salesman
        binding.loginassalesman.setOnClickListener{
            salesmanSelection()
        }
    }

    private fun salesmanSelection() {
        val intent = Intent(this, LoginPage::class.java)
        intent.putExtra("logintype","salesman")
        startActivity(intent)
    }

    //check actual location permission
    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {

            }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show rationale then request permission
                Toast.makeText(this, "Location permission is needed for this app", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            else -> {
                // Directly request permission
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

}