package com.example.eggbucketsales

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class AddCustomer : Fragment() {

    private lateinit var customerNameEditText: TextInputEditText
    private lateinit var businessNameEditText: TextInputEditText
    private lateinit var locationEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var photoPreview: ImageView
    private lateinit var addButton: Button
    private lateinit var addimage:CardView
    private lateinit var addimagetext:TextView
    private lateinit var uploadProgress: ProgressBar
    private var capturedImage: Bitmap? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storageRef by lazy { FirebaseStorage.getInstance().reference }
    private var deviceOrientation: Int = 0
    private lateinit var mauth: FirebaseAuth
    private lateinit var locationProgress: ProgressBar



    private val fusedLocationProvider by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                fetchLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                cameraLauncher.launch(null)
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }


    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let {
            val correctedBitmap = when (deviceOrientation) {
                90 -> rotateBitmap(it, 90f) // Landscape to portrait
                270 -> rotateBitmap(it, -90f) // Reverse landscape
                else -> it // Already portrait or close
            }

            photoPreview.visibility = View.VISIBLE
            photoPreview.setImageBitmap(correctedBitmap)
            capturedImage = correctedBitmap
            addimagetext.text = "Retake Image"
        } ?: Toast.makeText(requireContext(), "Camera cancelled", Toast.LENGTH_SHORT).show()
    }


    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }






    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_customer, container, false)

        customerNameEditText = view.findViewById(R.id.customerNameEditText)
        businessNameEditText = view.findViewById(R.id.businessNameEditText)
        locationEditText = view.findViewById(R.id.locationEditText)
        phoneEditText = view.findViewById(R.id.phoneNumberEditText)
        addimage=view.findViewById(R.id.addimage)
        photoPreview = view.findViewById(R.id.photoPreview)
        addButton = view.findViewById(R.id.addCustomerButton)
        addimagetext=view.findViewById(R.id.addImageText)
        uploadProgress = view.findViewById(R.id.uploadProgress)
        locationProgress = view.findViewById(R.id.locationProgress)

        mauth= FirebaseAuth.getInstance()
        locationEditText.setOnClickListener {
            requestLocationPermission()
        }

        addimage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                cameraLauncher.launch(null)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        addButton.setOnClickListener {
            val name = customerNameEditText.text.toString().trim()
            val business = businessNameEditText.text.toString().trim()
            val location = locationEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()

            if (name.isEmpty() || business.isEmpty() || location.isEmpty() || phone.isEmpty() || capturedImage == null) {
                Toast.makeText(requireContext(), "All fields are mandatory!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            uploadProgress.visibility = View.VISIBLE
            addButton.isEnabled = false

            val imageId = UUID.randomUUID().toString()
            val imageRef = storageRef.child("Customer/$imageId.jpg")

            val baos = java.io.ByteArrayOutputStream()
            capturedImage?.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val imageData = baos.toByteArray()

            val counterDocRef = firestore.collection("globalcounter").document("customercounter")
            val salesDocRef = firestore.collection("Salesman").document(mauth.currentUser?.uid.toString())

            salesDocRef.get().addOnSuccessListener { salesDoc ->
                val salesId = salesDoc.getString("sales_id") ?: "UNKNOWN"

                // Step 2: Get current counter value
                counterDocRef.get().addOnSuccessListener { counterDoc ->
                    val counter = counterDoc.getLong("counter") ?: 0
                    val customerId = "${salesId}C${counter + 1}"   // Combine salesId with counter to form customerId

                    // Step 3: Upload image
                    imageRef.putBytes(imageData)
                        .addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { uri ->
                                val customerData = mapOf(
                                    "custid" to customerId,
                                    "name" to name,
                                    "business" to business,
                                    "location" to location,
                                    "phone" to phone,
                                    "imageUrl" to uri.toString(),
                                    "createdAt" to System.currentTimeMillis(),
                                    "createdby" to FirebaseAuth.getInstance().currentUser?.uid
                                )

                                // Step 4: Save customer data
                                firestore.collection("customers").add(customerData)
                                    .addOnSuccessListener {
                                        // Step 5: Update counter after successful add
                                        counterDocRef.update("counter", counter + 1)
                                            .addOnSuccessListener {
                                                Toast.makeText(requireContext(), "Customer Added with ID: $customerId", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(requireContext(), "Customer added but failed to update counter.", Toast.LENGTH_SHORT).show()
                                            }

                                        resetForm()
                                    }
                                    .addOnFailureListener { firestoreError ->
                                        // Rollback image upload on failure
                                        imageRef.delete().addOnCompleteListener {
                                            Toast.makeText(requireContext(), "Failed to save customer. Rolled back image.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnCompleteListener {
                                        uploadProgress.visibility = View.GONE
                                        addButton.isEnabled = true
                                    }

                            }.addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to get image URL", Toast.LENGTH_SHORT).show()
                                uploadProgress.visibility = View.GONE
                                addButton.isEnabled = true
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
                            uploadProgress.visibility = View.GONE
                            addButton.isEnabled = true
                        }

                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to fetch customer counter", Toast.LENGTH_SHORT).show()
                    uploadProgress.visibility = View.GONE
                    addButton.isEnabled = true
                }

            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to fetch salesperson ID", Toast.LENGTH_SHORT).show()
                uploadProgress.visibility = View.GONE
                addButton.isEnabled = true
            }

        }



        val orientationEventListener = object : android.view.OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                deviceOrientation = when {
                    orientation in 45..134 -> 270 // Reverse Landscape
                    orientation in 135..224 -> 180 // Reverse Portrait
                    orientation in 225..314 -> 90  // Landscape
                    else -> 0 // Portrait
                }
            }
        }
        orientationEventListener.enable()



        return view
    }
    private fun resetForm() {
        customerNameEditText.setText("")
        businessNameEditText.setText("")
        locationEditText.setText("")
        phoneEditText.setText("")
        photoPreview.setImageBitmap(null)
        photoPreview.visibility = View.GONE
        addimagetext.text = "Add Image"
        capturedImage = null
    }


    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun fetchLocation() {
        locationProgress.visibility = View.VISIBLE
        locationEditText.isEnabled = false
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show()
            locationProgress.visibility = View.GONE
            locationEditText.isEnabled = true
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            numUpdates = 1
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationProvider.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val locText = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                        locationEditText.setText(locText)
                    } else {
                        Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                    // Hide progress bar and stop updates
                    locationProgress.visibility = View.GONE
                    locationEditText.isEnabled = true
                    fusedLocationProvider.removeLocationUpdates(this)
                }
            },
            null
        )
    }


}
