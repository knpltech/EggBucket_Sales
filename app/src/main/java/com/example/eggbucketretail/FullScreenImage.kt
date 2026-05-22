package com.example.eggbucketretail

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.eggbucketretail.databinding.ActivityFullScreenImageBinding

class FullScreenImage : AppCompatActivity() {
    private lateinit var binding: ActivityFullScreenImageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val imageUrl = intent.getStringExtra("image_url")
        if(!imageUrl.isNullOrEmpty()){
            Glide.with(this)
                .load(imageUrl)
                .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                .into(binding.fullScreenImage)
        }

        binding.fullScreenImage.setOnClickListener {
            finish()
        }


    }
}