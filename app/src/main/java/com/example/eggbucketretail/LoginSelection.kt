package com.example.eggbucketretail

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
import com.example.eggbucketretail.databinding.ActivityLoginSelectionBinding
import com.google.firebase.auth.FirebaseAuth

class LoginSelection : AppCompatActivity() {
    private lateinit var binding: ActivityLoginSelectionBinding

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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        checkLocationPermission()  //caaling function- location permission is given or not
        val currentUser = FirebaseAuth.getInstance().currentUser

        //checking if the user is logged in or not
        if (currentUser != null) {
            val email = currentUser.email
            if (!email.isNullOrEmpty()) {
                when {
                    email.contains("sales") -> {
                        startActivity(Intent(this, SalesManMainScreen::class.java))
                        finish()
                    }
                    email.contains("delivery") -> {
                        startActivity(Intent(this, DeliveryManMainScreen::class.java))
                        finish()
                    }
                    else -> {
                        FirebaseAuth.getInstance().signOut()
                    }
                }
            } else {
                FirebaseAuth.getInstance().signOut()
            }
        } else {
            FirebaseAuth.getInstance().signOut()
        }


        //handling login for salesman
        binding.loginassalesman.setOnClickListener{
            salesmanSelection()
        }

        //handling login for deliveryman
        binding.loginasdeliveryman.setOnClickListener{
            deliverySelection()
        }

    }
    //function to go to login page based on selection
    private fun deliverySelection() {
        val intent = Intent(this, LoginPage::class.java)
        intent.putExtra("logintype","deliveryman")
        startActivity(intent)
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