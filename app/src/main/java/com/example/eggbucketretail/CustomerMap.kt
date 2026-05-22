package com.example.eggbucketretail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.eggbucketretail.Models.Customer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.FirebaseFirestore

class CustomerMap : Fragment(), OnMapReadyCallback {
    private lateinit var googleMap: GoogleMap
    private val markerMap = mutableMapOf<String, Marker>()
    private val allCustomers = mutableListOf<Customer>()
    private lateinit var customerCard: LinearLayout
    private lateinit var customerImage: ImageView
    private lateinit var customerName: TextView
    private lateinit var customerBusiness: TextView
    private lateinit var customerlatlng: TextView
    private  var  LOCATION_PERMISSION_REQUEST_CODE = 1
    private var customersListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_customer_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind bottom card views
        customerCard = view.findViewById(R.id.customerBottomCard)
        customerImage = view.findViewById(R.id.customerbottomimageforsalesman)
        customerName = view.findViewById(R.id.customerNameforsalesman)
        customerBusiness = view.findViewById(R.id.businessNameforsalesman)
        customerlatlng = view.findViewById(R.id.customerLatlngforsalesman)


        customerCard.visibility = View.GONE // Initially hide

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    // setting google maps
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        fetchCustomersAndMark()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true  // Optional, usually true by default

        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                 LOCATION_PERMISSION_REQUEST_CODE
            )
        }

//        googleMap.setOnMarkerClickListener { marker ->
//            val markerTitle = marker.title?.trim()?.lowercase()  // Normalize the title
//            Log.d("CustomerMap", "Marker clicked: $markerTitle")
//
//            val customer = allCustomers.find {
//                it.uid.trim().lowercase() == markerTitle // Compare with the normalized customer name
//            }
//
//            if (customer != null) {
//                showCustomerCard(customer)
//            } else {
//                Log.d("CustomerMap", "Customer not found for: $markerTitle")
//            }
//
//            true  // Return true to prevent the default info window behavior
//        }
        // handling marker click
        googleMap.setOnMapClickListener {
            customerCard.visibility = View.GONE
        }




    }
    // fetching customer from firestore
    private fun fetchCustomersAndMark() {
        val db = FirebaseFirestore.getInstance()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }
        val todayDate = sdf.format(java.util.Date())
        customersListener?.remove()
        customersListener = db.collection("customers")
            .addSnapshotListener { snapshot, error ->
                if (snapshot == null) return@addSnapshotListener
                
                allCustomers.clear()
                googleMap.clear()
                markerMap.clear()

                var firstLatLng: LatLng? = null
                var focusLatLng: LatLng? = null
                var focusUid: String? = null
                val coordinateCounts = mutableMapOf<LatLng, Int>()

                for (doc in snapshot.documents) {
                    val uid = doc.id
                    val name = doc.getString("name") ?: continue
                    val location = doc.getString("location") ?: continue
                    val business = doc.getString("business") ?: "Unknown"
                    val phone = doc.getString("phone") ?: "N/A"
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    // Check 'todayOverride' map from Firestore
                    var showOnMap = true
                    val todayOverride = doc.get("todayOverride") as? Map<*, *>
                    
                    if (todayOverride != null) {
                        val overrideDate = todayOverride["date"] as? String
                        val overrideStatus = todayOverride["status"] as? String
                        
                        if (overrideDate == todayDate) {
                            if (overrideStatus?.uppercase() == "OFF") {
                                showOnMap = false
                            } else if (overrideStatus?.uppercase() == "ON") {
                                focusUid = uid
                            }
                        }
                    }

                    if (!showOnMap) continue

                    val position = parseLatLng(location)

                    if (position != null) {
                        val count = coordinateCounts[position] ?: 0
                        coordinateCounts[position] = count + 1

                        val finalPosition = if (count > 0) {
                            val angle = count * (2 * Math.PI / 8.0)
                            val radius = 0.00006 * count
                            LatLng(
                                position.latitude + radius * Math.sin(angle),
                                position.longitude + radius * Math.cos(angle)
                            )
                        } else {
                            position
                        }

                        if (uid == focusUid) {
                            focusLatLng = finalPosition
                        }

                        if (firstLatLng == null) {
                            firstLatLng = finalPosition
                        }

                        val customer = Customer(
                            uid=uid,
                            name=name,
                            location = location,
                            business = business,
                            imageUrl = imageUrl,
                            phone = phone)
                        allCustomers.add(customer)

                        val marker = googleMap.addMarker(
                            MarkerOptions()
                                .position(finalPosition)
                                .title(name)
                                .snippet("Business: $business")
                                .icon(resizeMarker(requireContext(), R.drawable.baseline_location_pin_24, 80, 80))
                        )
                        marker?.tag = uid

                        marker?.let {
                            markerMap[uid.lowercase()]=it
                            markerMap[name.lowercase()] = it
                            markerMap[business.lowercase()] = it
                        }
                    }
                }

                googleMap.setOnMapLoadedCallback {
                    val targetLatLng = focusLatLng ?: firstLatLng
                    val targetZoom = if (focusLatLng != null) 15f else 10f
                    targetLatLng?.let {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, targetZoom))
                        if (focusUid != null) {
                            val marker = markerMap[focusUid.lowercase()]
                            marker?.showInfoWindow()
                            val customer = allCustomers.find { c -> c.uid == focusUid }
                            customer?.let { c ->
                                showCustomerCard(c)
                            }
                        }
                    } ?: Toast.makeText(requireContext(), "No valid customer location found", Toast.LENGTH_SHORT).show()
                }
            }
        googleMap.setOnMarkerClickListener { marker ->
            val uidFromMarker = marker.tag as? String

            val customer = allCustomers.find {
                it.uid.trim().lowercase() == uidFromMarker?.trim()?.lowercase()
            }

            if (customer != null) {
                showCustomerCard(customer)
            } else {
                Log.d("CustomerMap", "Customer not found for: ${marker.title}")
            }

            true
        }

        googleMap.setOnMapClickListener {
            customerCard.visibility = View.GONE
        }




    }
    // customer card
    private fun showCustomerCard(customer: Customer) {
        Log.d("CustomerMap", "Clicked on: ${customer.name}")
        customerCard.visibility = View.VISIBLE
        customerName.text = customer.name
        customerBusiness.text = customer.business
        customerlatlng.text = customer.location // shows LatLng

        if (customer.imageUrl.isNotBlank()) {
            Glide.with(requireContext())
                .load(customer.imageUrl)
                .placeholder(R.drawable.logo)
                .into(customerImage)
        } else {
            customerImage.setImageResource(R.drawable.logo)
        }
        customerImage.setOnClickListener {
            val intent = Intent(requireContext(), FullScreenImage::class.java)
            intent.putExtra("image_url", customer.imageUrl)
            startActivity(intent)
        }
    }

    // using red  marker
    fun resizeMarker(context: Context, drawableRes: Int, width: Int, height: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, drawableRes)!!
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    // handling  search fucntion
    fun searchCustomer(query: String) {
        if (query.isBlank()) return

        val matchedEntry = markerMap.entries.find { entry ->
            entry.key.contains(query.trim().lowercase())
        }

        if (matchedEntry != null) {
            val marker = matchedEntry.value
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 15f))
            marker.showInfoWindow()
        } else {
            Toast.makeText(requireContext(), "Customer not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseLatLng(location: String): LatLng? {
        return try {
            val pattern = Regex("-?\\d+\\.\\d+")
            val matches = pattern.findAll(location).map { it.value.toDoubleOrNull() }.toList()
            if (matches.size >= 2 && matches[0] != null && matches[1] != null) {
                LatLng(matches[0]!!, matches[1]!!)
            } else {
                val parts = location.split(",").map { it.replace("[^0-9.-]".toRegex(), "").toDoubleOrNull() }
                if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
                    LatLng(parts[0]!!, parts[1]!!)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("LocationParser", "Failed to parse location: $location", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        customersListener?.remove()
    }
}
