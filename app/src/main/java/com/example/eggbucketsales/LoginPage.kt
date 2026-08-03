package com.example.eggbucketsales

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eggbucketsales.databinding.ActivityLoginPageBinding
import com.example.eggbucketsales.databinding.ActivityLoginSelectionBinding
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.log

class LoginPage : AppCompatActivity() {
    private lateinit var binding:ActivityLoginPageBinding
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding=ActivityLoginPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //firebase authentication
        auth = FirebaseAuth.getInstance()

        //handling click on login button
        binding.loginbutton.setOnClickListener {
            val phone = binding.loginphonenumber.text.toString().trim()
            val password = binding.loginpassword.text.toString().trim()
            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show progress bar and disable button
            binding.loginProgressBar.visibility = android.view.View.VISIBLE
            binding.loginbutton.isEnabled = false

            loginToSalesman(phone, password)
        }
    }

    // function for salesman login
    private fun loginToSalesman(phone: String, password: String) {
        val email = "$phone@eggbucketsales.in"
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                // Hide progress bar and enable button after task completion
                binding.loginProgressBar.visibility = android.view.View.GONE
                binding.loginbutton.isEnabled = true

                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, SalesManMainScreen::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect Credentials", Toast.LENGTH_SHORT).show()
                }
            }
    }




}
