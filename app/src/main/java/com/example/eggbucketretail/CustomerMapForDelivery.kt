    package com.example.eggbucketretail

    import android.content.Context
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.graphics.Bitmap
    import android.graphics.Canvas
    import android.location.Location
    import android.os.Bundle
    import android.util.Log
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.*
    import android.Manifest
    import android.os.Looper
    import androidx.core.app.ActivityCompat
    import androidx.core.content.ContextCompat
    import androidx.fragment.app.Fragment

    import androidx.viewpager2.widget.ViewPager2

    import com.example.eggbucketretail.Adapters.CustomerCardAdapter
    import com.example.eggbucketretail.Models.Customer

    import com.google.android.gms.location.*
    import com.google.android.gms.maps.CameraUpdateFactory
    import com.google.android.gms.maps.GoogleMap
    import com.google.android.gms.maps.OnMapReadyCallback
    import com.google.android.gms.maps.SupportMapFragment
    import com.google.android.gms.maps.model.*
    import com.google.firebase.firestore.FirebaseFirestore
    import com.google.firebase.firestore.ListenerRegistration
    import java.text.SimpleDateFormat
    import java.util.Date
    import java.util.Locale

    class CustomerMapForDelivery : Fragment(), OnMapReadyCallback {

        private lateinit var googleMap: GoogleMap
        private val markerMap = mutableMapOf<String, Marker>()
        private val allCustomers = mutableListOf<Customer>()
        private var viewPager: ViewPager2? = null
        private lateinit var customerAdapter: CustomerCardAdapter
        private var lastSelectedCustomerPosition: LatLng? = null
        private var lastSelectedCustomer: Customer? = null
        private lateinit var fusedLocationClient: FusedLocationProviderClient
        private lateinit var locationCallback: LocationCallback
        private lateinit var locationRequest: LocationRequest
        private var currentUserLocation: LatLng? = null
        private  val LOCATION_PERMISSION_REQUEST_CODE = 1
        private var customersListener: ListenerRegistration? = null


        private var mapReady = false
        private var locationPermissionGranted = false
        private var hasFetchedCustomers = false
        private var isFirstLoad = true
        private val activeMarkers = mutableMapOf<String, Marker>()
        private val markerIcons = mutableMapOf<Int, BitmapDescriptor>()

        private fun getCachedIcon(context: Context, resId: Int): BitmapDescriptor {
            return markerIcons.getOrPut(resId) {
                resizeMarker(context, resId, 100, 100)
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_customer_map_for_delivery, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewPager = view.findViewById(R.id.customer_view_pager)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001
                )
            } else {
                locationPermissionGranted = true
                setupLocationUpdates()
                setupMapFragment()
            }
        }

        private fun setupLocationUpdates() {
            locationRequest = LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = Priority.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val latLng = LatLng(location.latitude, location.longitude)
                        if (isSignificantChange(latLng, currentUserLocation)) {
                            currentUserLocation = latLng
                            if (::customerAdapter.isInitialized) {
                                customerAdapter.updateCurrentLocation(latLng)
                            }
                            
                            if (isFirstLoad && mapReady) {
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                                isFirstLoad = false
                            }
                        }
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }



        private fun setupMapFragment() {
            val mapFragment = childFragmentManager.findFragmentById(R.id.mapfordelivery) as SupportMapFragment
            mapFragment.getMapAsync(this)
        }

        override fun onMapReady(map: GoogleMap) {
            googleMap = map
            googleMap.uiSettings.isZoomControlsEnabled = true
            mapReady = true
            
            view?.findViewById<ProgressBar>(R.id.map_loading_progress)?.visibility = View.GONE

            if (locationPermissionGranted && !hasFetchedCustomers) {
                fetchCustomersAndMark()
                hasFetchedCustomers = true
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

                googleMap.isMyLocationEnabled = true
                googleMap.uiSettings.isMyLocationButtonEnabled = true

            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            googleMap.setOnMarkerClickListener { marker ->
                marker.showInfoWindow()
                val uid = marker.tag as? String ?: ""
                allCustomers.indexOfFirst { it.uid == uid }.takeIf { it >= 0 }?.let { position ->
                    viewPager?.currentItem = position
                    lastSelectedCustomerPosition = marker.position
                    lastSelectedCustomer = allCustomers[position]
                    viewPager?.visibility = View.VISIBLE
                    moveMapToCustomer(marker.position)
                }
                true
            }
        }

        private fun fetchCustomersAndMark() {
            val db = FirebaseFirestore.getInstance()
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            customersListener?.remove()
            // Cleanup existing listeners
            deliveryListeners.values.forEach { it.remove() }
            deliveryListeners.clear()

            customersListener = db.collection("customers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("MapError", "Listen failed.", error)
                        return@addSnapshotListener
                    }
                    
                    // Don't clear if we have no data and it's just a cache miss
                    if (snapshot == null || (snapshot.isEmpty && snapshot.metadata.isFromCache)) {
                        return@addSnapshotListener
                    }
                    
                    val oldPosition = viewPager?.currentItem ?: 0
                    val currentContext = context ?: return@addSnapshotListener
                    
                    val newCustomers = mutableListOf<Customer>()
                    val processedUids = mutableSetOf<String>()
                    
                    // First, identify which markers to keep/update/add
                    for (doc in snapshot.documents) {
                        val uid = doc.id
                        val name = doc.getString("name") ?: continue
                        val location = doc.getString("location") ?: continue
                        val business = doc.getString("business") ?: "Unknown"
                        val phone = doc.getString("phone") ?: "N/A"
                        val imageUrl = doc.getString("imageUrl") ?: ""
                        
                        val todayOverride = doc.get("todayOverride") as? Map<*, *>
                        var showOnMap = true
                        if (todayOverride != null && todayOverride["date"] == todayDate && 
                            (todayOverride["status"] as? String)?.uppercase() == "OFF") {
                            showOnMap = false
                        }

                        val latLng = parseLatLng(location)
                        if (latLng != null && showOnMap) {
                            processedUids.add(uid)
                            
                            val last8Days = doc.get("last8Days") as? Map<*, *>
                            val todayData = last8Days?.get(todayDate) as? Map<*, *>
                            val status = todayData?.get("status") as? String

                            val customer = Customer(uid, name, business, phone, imageUrl, "", 0, location, true, status)
                            newCustomers.add(customer)

                            try {
                                val iconRes = when (status) {
                                    "delivered" -> R.drawable.green_marker
                                    "reached" -> R.drawable.orangemarker
                                    else -> R.drawable.baseline_location_pin_24
                                }
                                // Use slightly larger size for better visibility
                                val icon = getCachedIcon(currentContext, iconRes)
                                
                                val marker = if (activeMarkers.containsKey(uid)) {
                                    activeMarkers[uid]!!.apply {
                                        position = latLng
                                        title = name
                                        setIcon(icon)
                                    }
                                } else {
                                    googleMap.addMarker(MarkerOptions()
                                        .position(latLng)
                                        .title(name)
                                        .icon(icon)
                                        .anchor(0.5f, 0.5f))?.also {
                                        activeMarkers[uid] = it
                                    }
                                }

                                marker?.let {
                                    it.tag = uid
                                    // Update search map
                                    markerMap[uid.lowercase()] = it
                                    markerMap[name.lowercase()] = it
                                }
                            } catch (e: Exception) {
                                Log.e("MapError", "Failed to add marker for $name", e)
                            }
                        }
                    }

                    // Remove markers that are no longer present
                    val it = activeMarkers.entries.iterator()
                    while (it.hasNext()) {
                        val entry = it.next()
                        if (!processedUids.contains(entry.key)) {
                            entry.value.remove()
                            it.remove()
                        }
                    }

                    allCustomers.clear()
                    allCustomers.addAll(newCustomers)

                    if (!::customerAdapter.isInitialized) {
                        val currentLatLng = currentUserLocation ?: LatLng(0.0, 0.0)
                        customerAdapter = CustomerCardAdapter(
                            currentContext,
                            currentLatLng,
                            onCustomerSelected = { customer, latLng ->
                                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                                activeMarkers[customer.uid]?.showInfoWindow()
                            },
                            onImageClick = { imgUrl ->
                                val intent = Intent(currentContext, FullScreenImage::class.java)
                                intent.putExtra("image_url", imgUrl)
                                startActivity(intent)
                            }
                        )
                        viewPager?.adapter = customerAdapter
                        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                            override fun onPageSelected(position: Int) {
                                if (position in allCustomers.indices) {
                                    val customer = allCustomers[position]
                                    val marker = activeMarkers[customer.uid]
                                    marker?.let {
                                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it.position, 17f))
                                        it.showInfoWindow()
                                    }
                                }
                            }
                        })
                    }
                    customerAdapter.submitList(allCustomers.toList())

                    if (allCustomers.isNotEmpty()) {
                        viewPager?.visibility = View.VISIBLE
                        if (isFirstLoad) {
                            val targetIndex = if (oldPosition < allCustomers.size) oldPosition else 0
                            viewPager?.currentItem = targetIndex
                            parseLatLng(allCustomers[targetIndex].location)?.let { latLng ->
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                                viewPager?.postDelayed({
                                    activeMarkers[allCustomers[targetIndex].uid]?.showInfoWindow()
                                }, 500)
                            }
                            isFirstLoad = false
                        }
                    } else {
                        viewPager?.visibility = View.GONE
                    }
                }
        }

        private fun isSignificantChange(newLocation: LatLng, oldLocation: LatLng?, thresholdMeters: Float = 15f): Boolean {
            if (oldLocation == null) return true
            val result = FloatArray(1)
            Location.distanceBetween(
                oldLocation.latitude, oldLocation.longitude,
                newLocation.latitude, newLocation.longitude,
                result
            )
            return result[0] > thresholdMeters
        }


        override fun onResume() {
            super.onResume()
            if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            }
        }

        override fun onPause() {
            super.onPause()
            if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }


        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
                setupLocationUpdates()
                setupMapFragment()

                // 🔁 If map is already ready (it often is), retry fetching customers
                if (mapReady && !hasFetchedCustomers) {
                    fetchCustomersAndMark()
                    hasFetchedCustomers = true
                }
            }
        }

        private fun moveMapToCustomer(latLng: LatLng) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
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

        private fun resizeMarker(context: Context, drawableRes: Int, width: Int, height: Int): BitmapDescriptor {
            val drawable = ContextCompat.getDrawable(context, drawableRes)!!
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }

        fun searchCustomer(query: String) {
            if (query.isBlank()) return
            val matchedEntry = markerMap.entries.find { it.key.contains(query.trim().lowercase()) }
            matchedEntry?.let { entry ->
                val marker = entry.value
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 17f))
                marker.showInfoWindow()
                allCustomers.indexOfFirst { it.name.equals(marker.title, ignoreCase = true) }.takeIf { it >= 0 }?.let { position ->
                    viewPager?.currentItem = position
                    lastSelectedCustomerPosition = marker.position
                    lastSelectedCustomer = allCustomers[position]
                    viewPager?.visibility = View.VISIBLE
                } ?: Toast.makeText(requireContext(), "Customer data not found", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(requireContext(), "Customer not found", Toast.LENGTH_SHORT).show()
        }


        private val deliveryListeners = mutableMapOf<String, ListenerRegistration>()

        fun refreshCustomerMap(onComplete: () -> Unit) {
            if (mapReady) {
                fetchCustomersAndMark()
                Toast.makeText(requireContext(), "Map refreshed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Map is not ready yet", Toast.LENGTH_SHORT).show()
            }
            onComplete() // call back after refresh logic
        }
        override fun onDestroyView() {
            super.onDestroyView()
            customersListener?.remove()
            deliveryListeners.values.forEach { it.remove() }
            deliveryListeners.clear()
            
            // CRITICAL: Clear markers from memory so they don't conflict with the next map instance
            activeMarkers.values.forEach { it.remove() }
            activeMarkers.clear()
            markerMap.clear()

            if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }


    }