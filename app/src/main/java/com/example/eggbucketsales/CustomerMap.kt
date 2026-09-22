package com.example.eggbucketsales

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.eggbucketsales.Models.Customer
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.example.eggbucketsales.Repository.CustomerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CustomerMap : Fragment(), OnMapReadyCallback {
    private lateinit var googleMap: GoogleMap
    private val markerMap = mutableMapOf<String, Marker>()
    private val activeMarkers = mutableMapOf<String, Marker>()
    private val allCustomers = mutableListOf<Customer>()
    private lateinit var customerCard: LinearLayout
    private lateinit var customerImage: ImageView
    private lateinit var customerName: TextView
    private lateinit var customerBusiness: TextView
    private lateinit var customerlatlng: TextView
    private lateinit var customerDistance: TextView
    private lateinit var refreshLocationBtn: ImageView
    private lateinit var cardBtnUpdate: androidx.cardview.widget.CardView
    private lateinit var btnUpdate: TextView
    private lateinit var btnStart: TextView
    private lateinit var customerCategoryBadge: TextView
    private lateinit var customerLastDelivery: TextView
    private lateinit var layoutLastDelivery: View
    private var LOCATION_PERMISSION_REQUEST_CODE = 1
    private var customersListener: com.google.firebase.firestore.ListenerRegistration? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var currentUserLocation: LatLng? = null

    private var selectedCustomerUid: String? = null
    private var selectedMarker: Marker? = null
    private var isInitialCameraSet = false
    private var mapReady = false
    private val markerIconCache = mutableMapOf<String, BitmapDescriptor>()

    private fun isCustomerNewlyAdded(customer: Customer?): Boolean {
        return CustomerRepository.isCustomerNewlyAdded(customer?.createdAt ?: 0L)
    }

    private fun isCustomerAccessible(customer: Customer?): Boolean {
        return CustomerRepository.isCustomerAccessible(customer)
    }

    private fun formatDeliveryDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("d MMM", Locale.US)
            val d = parser.parse(dateStr)
            if (d != null) formatter.format(d) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun getMarkerIcon(context: Context, customer: Customer?, isSelected: Boolean): BitmapDescriptor {
        val isAccessible = isCustomerAccessible(customer)
        val status = customer?.status?.lowercase()

        val drawableRes = when {
            !isAccessible -> R.drawable.blue_marker                               // Blue for rest (no access)
            status == "delivered" -> R.drawable.green_marker                      // Green for (< 45, D0, D1) delivered today
            status == "reached" || status == "checked" -> R.drawable.orangemarker // Orange for (< 45, D0, D1) checked today
            else -> R.drawable.baseline_location_pin_24                           // Red for (< 45, D0, D1) pending
        }

        val width = if (isSelected) 125 else 80
        val height = if (isSelected) 125 else 80
        val cacheKey = "${drawableRes}_${width}_${height}"
        return markerIconCache.getOrPut(cacheKey) {
            resizeMarker(context, drawableRes, width, height)
        }
    }

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
        customerDistance = view.findViewById(R.id.customerDistanceforsalesman)
        refreshLocationBtn = view.findViewById(R.id.refreshLocationforsalesman)
        cardBtnUpdate = view.findViewById(R.id.cardBtnUpdateforsalesman)
        btnUpdate = view.findViewById(R.id.btnUpdateforsalesman)
        btnStart = view.findViewById(R.id.btnStartforsalesman)
        customerCategoryBadge = view.findViewById(R.id.customerCategoryBadgeforsalesman)
        customerLastDelivery = view.findViewById(R.id.customerLastDeliveryforsalesman)
        layoutLastDelivery = view.findViewById(R.id.layoutLastDeliveryforsalesman)

        customerCard.visibility = View.GONE // Initially hide

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Always load map fragment immediately regardless of location permission state
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupLocationUpdates()
    }

    private fun setupLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    currentUserLocation = latLng
                    if (mapReady && !isInitialCameraSet && selectedCustomerUid == null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                        isInitialCameraSet = true
                    }
                }
            }

            val locationRequest = LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 2000
                priority = Priority.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val latLng = LatLng(location.latitude, location.longitude)
                        currentUserLocation = latLng
                        if (mapReady && !isInitialCameraSet && selectedCustomerUid == null) {
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                            isInitialCameraSet = true
                        }
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        mapReady = true

        view?.findViewById<ProgressBar>(R.id.map_loading_progress)?.visibility = View.GONE

        // Custom InfoWindow to render customer name badge directly floating above marker pin (like Pic 3)
        googleMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                val infoView = layoutInflater.inflate(R.layout.custom_info_window, null)
                val tvTitle = infoView.findViewById<TextView>(R.id.info_window_title)
                val tvSnippet = infoView.findViewById<TextView>(R.id.info_window_snippet)

                tvTitle.text = marker.title ?: ""
                if (!marker.snippet.isNullOrEmpty()) {
                    tvSnippet.text = marker.snippet
                    tvSnippet.visibility = View.VISIBLE
                } else {
                    tvSnippet.visibility = View.GONE
                }
                return infoView
            }

            override fun getInfoContents(marker: Marker): View? = null
        })

        fetchCustomersAndMark()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
            setupLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        // Focus camera on Agent's Current Location if location is already fetched
        currentUserLocation?.let { latLng ->
            if (!isInitialCameraSet && selectedCustomerUid == null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                isInitialCameraSet = true
            }
        }

        googleMap.setOnMarkerClickListener { marker ->
            val uidFromMarker = marker.tag as? String
            val customer = allCustomers.find {
                it.uid.trim().lowercase() == uidFromMarker?.trim()?.lowercase()
            }

            if (customer != null) {
                selectAndFocusMarker(marker, customer, animateCamera = true)
            } else {
                Log.d("CustomerMap", "Customer not found for marker: ${marker.title}")
            }

            true
        }

        googleMap.setOnMapClickListener {
            clearSelectedMarker()
        }
    }

    private fun selectAndFocusMarker(marker: Marker, customer: Customer, animateCamera: Boolean = true) {
        val ctx = context ?: return

        // Reset previous selected marker icon & info window if different
        if (selectedMarker != null && selectedMarker != marker) {
            try {
                val prevCustomer = allCustomers.find { it.uid == selectedCustomerUid }
                selectedMarker?.setIcon(getMarkerIcon(ctx, prevCustomer, isSelected = false))
                selectedMarker?.zIndex = 0f
                selectedMarker?.hideInfoWindow()
            } catch (e: Exception) {
                Log.e("CustomerMap", "Error resetting previous marker icon", e)
            }
        }

        selectedMarker = marker
        selectedCustomerUid = customer.uid

        // Enlarge clicked marker, bring it to front & show name badge above marker
        try {
            marker.setIcon(getMarkerIcon(ctx, customer, isSelected = true))
            marker.zIndex = 10f
            marker.title = customer.name
            marker.snippet = customer.business
            marker.showInfoWindow()
        } catch (e: Exception) {
            Log.e("CustomerMap", "Error setting selected marker icon", e)
        }

        // Center camera on the selected marker
        if (animateCamera) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 17f))
        }

        showCustomerCard(customer)
    }

    private fun clearSelectedMarker() {
        val ctx = context
        if (selectedMarker != null && ctx != null) {
            try {
                val prevCustomer = allCustomers.find { it.uid == selectedCustomerUid }
                selectedMarker?.setIcon(getMarkerIcon(ctx, prevCustomer, isSelected = false))
                selectedMarker?.zIndex = 0f
                selectedMarker?.hideInfoWindow()
            } catch (e: Exception) {
                Log.e("CustomerMap", "Error clearing selected marker icon", e)
            }
        }
        selectedMarker = null
        selectedCustomerUid = null
        customerCard.visibility = View.GONE
    }

    private fun fetchCustomersAndMark() {
        val db = FirebaseFirestore.getInstance()
        val targetDates = CustomerRepository.getLast8DaysDateStrings()

        customersListener?.remove()
        customersListener = db.collection("customers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CustomerMap", "Listen failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val currentContext = context ?: return@addSnapshotListener

                val newCustomers = mutableListOf<Customer>()
                val processedUids = mutableSetOf<String>()
                val coordinateCounts = mutableMapOf<LatLng, Int>()
                var focusUid: String? = null
                var focusLatLng: LatLng? = null

                markerMap.clear()

                for (doc in snapshot.documents) {
                    val customer = CustomerRepository.processCustomer(doc, targetDates) ?: continue
                    val uid = customer.uid
                    val name = customer.name
                    val location = customer.location
                    val business = customer.business

                    // Check 'todayOverride' status
                    val todayOverride = doc.get("todayOverride") as? Map<*, *>
                    if (todayOverride != null) {
                        val overrideStatus = todayOverride["status"] as? String
                        if (overrideStatus?.uppercase() == "ON") {
                            focusUid = uid
                        }
                    }

                    val position = parseLatLng(location)

                    if (position != null) {
                        processedUids.add(uid)

                        val roundedPosition = roundLatLng(position)
                        val count = coordinateCounts[roundedPosition] ?: 0
                        coordinateCounts[roundedPosition] = count + 1

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

                        newCustomers.add(customer)

                        val isSelected = (selectedCustomerUid == uid)
                        val iconToUse = getMarkerIcon(currentContext, customer, isSelected)
                        val zIndexToUse = if (isSelected) 10f else 0f

                        val marker = if (activeMarkers.containsKey(uid)) {
                            activeMarkers[uid]!!.apply {
                                setPosition(finalPosition)
                                title = name
                                snippet = "Business: $business"
                                setIcon(iconToUse)
                                zIndex = zIndexToUse
                            }
                        } else {
                            googleMap.addMarker(
                                MarkerOptions()
                                    .position(finalPosition)
                                    .title(name)
                                    .snippet("Business: $business")
                                    .icon(iconToUse)
                                    .zIndex(zIndexToUse)
                            )?.also {
                                activeMarkers[uid] = it
                            }
                        }

                        marker?.let {
                            it.tag = uid
                            if (isSelected) {
                                selectedMarker = it
                            }
                            markerMap[uid.lowercase()] = it
                            markerMap[name.lowercase()] = it
                            markerMap[business.lowercase()] = it
                        }
                    }
                }

                // Remove stale markers no longer in dataset
                val iterator = activeMarkers.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!processedUids.contains(entry.key)) {
                        if (selectedCustomerUid == entry.key) {
                            selectedMarker = null
                            selectedCustomerUid = null
                            customerCard.visibility = View.GONE
                        }
                        entry.value.remove()
                        iterator.remove()
                    }
                }

                allCustomers.clear()
                allCustomers.addAll(newCustomers)

                // Handle Camera & Focus behavior on initial load
                if (selectedCustomerUid != null) {
                    val selectedCust = allCustomers.find { it.uid == selectedCustomerUid }
                    val currentSelectedMarker = activeMarkers[selectedCustomerUid]
                    if (selectedCust != null && currentSelectedMarker != null) {
                        selectAndFocusMarker(currentSelectedMarker, selectedCust, animateCamera = false)
                    }
                } else if (!isInitialCameraSet) {
                    // Priority 1: Agent's Current Location (GPS)
                    if (currentUserLocation != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation!!, 16f))
                        isInitialCameraSet = true
                    } else if (focusUid != null && focusLatLng != null) {
                        // Priority 2: Focus UID (todayOverride ON)
                        val focusCustomer = allCustomers.find { it.uid == focusUid }
                        val focusMarker = activeMarkers[focusUid]
                        if (focusMarker != null && focusCustomer != null) {
                            selectAndFocusMarker(focusMarker, focusCustomer, animateCamera = true)
                        } else {
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(focusLatLng, 15f))
                        }
                        isInitialCameraSet = true
                    }
                }
            }
    }

    private fun showCustomerCard(customer: Customer) {
        Log.d("CustomerMap", "Clicked on: ${customer.name}")
        customerCard.visibility = View.VISIBLE
        customerName.text = customer.name
        customerBusiness.text = customer.business
        customerlatlng.text = customer.location

        val customerLatLng = parseLatLng(customer.location)
        if (customerLatLng != null) {
            updateCustomerDistance(customerLatLng)
        } else {
            customerDistance.text = "Distance unknown"
        }

        refreshLocationBtn.setOnClickListener {
            if (customerLatLng != null) {
                updateCustomerDistance(customerLatLng)
            }
        }

        val isAccessible = isCustomerAccessible(customer)
        val isNew = isCustomerNewlyAdded(customer)
        val status = customer.status?.lowercase()

        // Setup Category Badge (Red for pending <45d/D0/D1, Orange for checked, Green for delivered, Blue for rest)
        when {
            !isAccessible -> {
                customerCategoryBadge.text = if (customer.category.isNotBlank()) customer.category else customer.computedFrequency
                customerCategoryBadge.setBackgroundResource(R.drawable.badge_blue_bg) // Blue badge (no access)
            }
            status == "delivered" -> {
                customerCategoryBadge.text = "DELIVERED"
                customerCategoryBadge.setBackgroundResource(R.drawable.badge_green_bg) // Green badge
            }
            status == "reached" || status == "checked" -> {
                customerCategoryBadge.text = "CHECKED"
                customerCategoryBadge.setBackgroundResource(R.drawable.badge_yellow_bg) // Orange badge
            }
            else -> {
                val text = if (isNew) "NEW" else customer.computedFrequency
                customerCategoryBadge.text = text
                customerCategoryBadge.setBackgroundResource(R.drawable.badge_d0_bg) // Red badge
            }
        }

        if (!customer.lastDeliveredDate.isNullOrBlank()) {
            val dateFmt = formatDeliveryDate(customer.lastDeliveredDate)
            val qtyStr = if (customer.lastDeliveredQty > 0) " • ${customer.lastDeliveredQty} trays" else ""
            val amtStr = if (customer.lastDeliveredAmount > 0) " (₹${customer.lastDeliveredAmount})" else ""
            customerLastDelivery.text = "Last: $dateFmt$qtyStr$amtStr"
            customerLastDelivery.setTextColor(ContextCompat.getColor(requireContext(), if (customer.isD0OrD1) R.color.red else R.color.maincolor))
        } else {
            if (isNew) {
                customerLastDelivery.text = "New Customer • No past orders"
                customerLastDelivery.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            } else {
                customerLastDelivery.text = "No orders in last 8 days"
                customerLastDelivery.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            }
        }

        if (!isAccessible) {
            // Customers present for > 45 days with regular orders: Agents can only view info, UPDATE button is hidden
            cardBtnUpdate.visibility = View.GONE
            btnUpdate.visibility = View.GONE
        } else {
            // Accessible customers (Newly added OR D0/D1 inactive): Agents can update
            cardBtnUpdate.visibility = View.VISIBLE
            btnUpdate.visibility = View.VISIBLE

            when (status) {
                "delivered" -> {
                    btnUpdate.text = "DELIVERED"
                    btnUpdate.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green))
                    btnUpdate.isEnabled = false
                    btnUpdate.isClickable = false
                }
                "reached", "checked" -> {
                    btnUpdate.text = "CHECKED"
                    btnUpdate.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.maincolor)) // Orange
                    btnUpdate.isEnabled = false
                    btnUpdate.isClickable = false
                }
                else -> {
                    btnUpdate.text = "UPDATE"
                    btnUpdate.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
                    btnUpdate.isEnabled = true
                    btnUpdate.isClickable = true
                }
            }
        }

        // UPDATE button opens DeliveryFormDialog offering DELIVER and UPDATE (check reason) options
        btnUpdate.setOnClickListener {
            if (!isCustomerAccessible(customer)) {
                Toast.makeText(requireContext(), "You can only update newly added or D0/D1 customers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (customer.status?.lowercase() == "delivered" || customer.status?.lowercase() == "reached") {
                Toast.makeText(requireContext(), "Customer is already updated for today", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (customerLatLng == null) {
                Toast.makeText(requireContext(), "Invalid customer location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentLoc = currentUserLocation
            if (currentLoc == null) {
                Toast.makeText(requireContext(), "Current GPS location not available. Turn on location services.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val results = FloatArray(1)
            Location.distanceBetween(
                currentLoc.latitude, currentLoc.longitude,
                customerLatLng.latitude, customerLatLng.longitude,
                results
            )
            val distanceInMeters = results[0]
            if (distanceInMeters > 50f) {
                Toast.makeText(
                    requireContext(),
                    "You must be within 50m of customer location to update. Current distance: ${distanceInMeters.toInt()}m",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val dialog = DeliveryFormDialog(customer) { actionType ->
                updateCustomerStatusToReached(customer, actionType)
            }
            dialog.show(childFragmentManager, "DeliveryFormDialog")
        }

        // START button opens Google Maps Navigation
        btnStart.setOnClickListener {
            if (customerLatLng != null) {
                val gmmIntentUri = Uri.parse("google.navigation:q=${customerLatLng.latitude},${customerLatLng.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    Toast.makeText(requireContext(), "Google Maps not installed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Invalid location", Toast.LENGTH_SHORT).show()
            }
        }

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

    private fun updateCustomerDistance(customerLatLng: LatLng) {
        val currentLoc = currentUserLocation
        if (currentLoc != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLoc.latitude, currentLoc.longitude,
                customerLatLng.latitude, customerLatLng.longitude,
                results
            )
            val distanceMeters = results[0]
            val distanceStr = if (distanceMeters >= 1000) {
                String.format(Locale.US, "%.1f km away", distanceMeters / 1000f)
            } else {
                "${distanceMeters.toInt()} m away"
            }
            customerDistance.text = distanceStr
        } else {
            customerDistance.text = "Calculating..."
        }
    }

    fun resizeMarker(context: Context, drawableRes: Int, width: Int, height: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, drawableRes)!!
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun searchCustomer(query: String) {
        if (query.isBlank()) return

        val matchedEntry = markerMap.entries.find { entry ->
            entry.key.contains(query.trim().lowercase())
        }

        if (matchedEntry != null) {
            val marker = matchedEntry.value
            val uid = marker.tag as? String
            val customer = allCustomers.find { it.uid == uid }
            if (customer != null) {
                selectAndFocusMarker(marker, customer, animateCamera = true)
            } else {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 17f))
                marker.showInfoWindow()
            }
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

    private fun roundLatLng(latLng: LatLng): LatLng {
        val roundedLat = Math.round(latLng.latitude * 100000.0) / 100000.0
        val roundedLng = Math.round(latLng.longitude * 100000.0) / 100000.0
        return LatLng(roundedLat, roundedLng)
    }

    private fun updateCustomerStatusToReached(customer: Customer, reasonId: String) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val todayDate = sdf.format(Date())

        val prefs = requireContext().getSharedPreferences("EggBucketPrefs", Context.MODE_PRIVATE)
        val agentName = prefs.getString("agent_name", "Unknown") ?: "Unknown"

        val db = FirebaseFirestore.getInstance()
        val customerRef = db.collection("customers").document(customer.uid)

        val newEntry = hashMapOf(
            "agentId" to currentUserId,
            "agentName" to agentName,
            "reason" to reasonId,
            "status" to "reached",
            "time" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        customerRef.get(com.google.firebase.firestore.Source.CACHE).addOnCompleteListener { task ->
            val updateData = hashMapOf<String, Any>()

            if (task.isSuccessful && task.result != null) {
                val document = task.result
                val last8Days = document.get("last8Days") as? Map<String, Any>
                val updatedLast8Days = mutableMapOf<String, Any>()

                if (last8Days != null) {
                    for ((dateKey, value) in last8Days) {
                        if (!isOlderThan30Days(dateKey, todayDate) && dateKey != todayDate) {
                            updatedLast8Days[dateKey] = value
                        }
                    }
                }
                updatedLast8Days[todayDate] = newEntry
                updateData["last8Days"] = updatedLast8Days
            } else {
                updateData["last8Days.$todayDate"] = newEntry
            }

            updateData["todayOverride.status"] = "reached"

            customerRef.update(updateData)

            val deliveriesCollectionRef = customerRef.collection("deliveries")
            deliveriesCollectionRef.document(todayDate).set(newEntry)

            deliveriesCollectionRef.get(com.google.firebase.firestore.Source.CACHE).addOnCompleteListener { subcollectionTask ->
                if (subcollectionTask.isSuccessful && subcollectionTask.result != null) {
                    for (doc in subcollectionTask.result.documents) {
                        val docId = doc.id
                        if (isOlderThan30Days(docId, todayDate)) {
                            deliveriesCollectionRef.document(docId).delete()
                        }
                    }
                }
            }
        }
        Toast.makeText(requireContext(), "Status Updated locally! Syncing in background.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        customersListener?.remove()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        activeMarkers.values.forEach { it.remove() }
        activeMarkers.clear()
        markerMap.clear()
        allCustomers.clear()
        selectedMarker = null
        selectedCustomerUid = null
        isInitialCameraSet = false
    }
}
