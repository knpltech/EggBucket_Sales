package com.example.eggbucketsales.Adapters



import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Calendar
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.eggbucketsales.DeliveryFormDialog
import com.example.eggbucketsales.Models.Customer
import com.example.eggbucketsales.R
import com.example.eggbucketsales.isReachedType
import com.example.eggbucketsales.isOlderThan30Days
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class CustomerCardAdapter(
    private val context: Context,
    private var currentUserLocation: LatLng?,
    private val onCustomerSelected: (Customer, LatLng) -> Unit,
    private val onImageClick: (String) -> Unit
) : ListAdapter<Customer, CustomerCardAdapter.CardViewHolder>(CustomerDiffCallback()) {

    class CustomerDiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem == newItem
        }
    }


    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerName: TextView = itemView.findViewById(R.id.customerName)
        val businessName: TextView = itemView.findViewById(R.id.businessName)
        val customerImage: ImageView = itemView.findViewById(R.id.customerbottomimage)
        val distanceText: TextView = itemView.findViewById(R.id.Distancefromcurrent)
        val refreshButton: ImageView = itemView.findViewById(R.id.refresh_location)
        val deliveryButton: TextView =itemView.findViewById(R.id.deliverybutton)
        val routingbutton:TextView=itemView.findViewById(R.id.routingstart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.customer_card, parent, false)
        return CardViewHolder(view)
    }

    override fun getItemCount(): Int = currentList.size

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val customer = getItem(position)

        // Set customer name and business
        holder.customerName.text = customer.name
        holder.businessName.text = customer.business
        updateDeliveryUI(holder, customer.status)


        // Load image with Glide
        Glide.with(context)
            .load(customer.imageUrl)
            .placeholder(R.drawable.logo)
            .into(holder.customerImage)

        // Parse customer LatLng
        val customerLatLng = parseLatLng(customer.location)

        if (customerLatLng != null) {
            // Handle card click to select customer
            holder.itemView.setOnClickListener {
                onCustomerSelected(customer, customerLatLng)
            }

            holder.routingbutton.setOnClickListener{
                val gmmIntentUri = Uri.parse("google.navigation:q=${customerLatLng.latitude},${customerLatLng.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                if (mapIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mapIntent)
                } else {
                    Toast.makeText(context, "Google Maps not installed", Toast.LENGTH_SHORT).show()
                }

            }

            // Initial distance calculation
            refreshDistance(holder, customerLatLng)

            // Refresh button click
            holder.refreshButton.setOnClickListener {
                animateRefreshButton(holder.refreshButton)
                refreshDistance(holder, customerLatLng)
            }

            // Delivery button click
            holder.deliveryButton.setOnClickListener {
                handleDeliveryButtonClick(holder, customerLatLng,customer.uid)
            }
        } else {
            holder.distanceText.text = "Distance unknown"
        }
        if (customerLatLng != null) {
            refreshDistance(holder, customerLatLng)
        }
        // Image click
        holder.customerImage.setOnClickListener {
            onImageClick(customer.imageUrl)
        }
    }

    private fun handleDeliveryButtonClick(holder: CardViewHolder, customerLatLng: LatLng, uid: String) {
        val position = holder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val currentCustomer = getItem(position)

        if (currentCustomer.status?.lowercase() == "delivered" || currentCustomer.status?.lowercase() == "reached" ||
            holder.deliveryButton.text.toString().equals("DELIVERED", ignoreCase = true) ||
            holder.deliveryButton.text.toString().equals("CHECKED", ignoreCase = true)) {
            holder.deliveryButton.isEnabled = false
            holder.deliveryButton.isClickable = false
            Toast.makeText(context, "Customer is already updated for today", Toast.LENGTH_SHORT).show()
            return
        }

        val currentLocation = currentUserLocation
        if (currentLocation != null) {
            animateRefreshButton(holder.refreshButton)

            val result = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude,
                currentLocation.longitude,
                customerLatLng.latitude,
                customerLatLng.longitude,
                result
            )
            val distanceInMeters = result[0]
            // radius distance
            if (distanceInMeters <= 50f) {
                val fragmentActivity = context as? FragmentActivity
                fragmentActivity?.let {
                    val dialog = DeliveryFormDialog(getItem(position)) { actionType ->
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                        }
                        val todayDate = sdf.format(Date())

                        // Fetch Agent Name from local cache (Instantly available)
                        val prefs = context.getSharedPreferences("EggBucketPrefs", Context.MODE_PRIVATE)
                        val agentName = prefs.getString("agent_name", "Unknown") ?: "Unknown"

                        val db = FirebaseFirestore.getInstance()
                        val customerRef = db.collection("customers").document(uid)

                        // UPDATE DATA DIRECTLY: Read from cache, perform pruning, and save both to the map and subcollection
                        customerRef.get(Source.CACHE).addOnCompleteListener { task ->
                            val updateData = hashMapOf<String, Any>()
                            val newEntry = hashMapOf(
                                "agentId" to currentUserId,
                                "agentName" to agentName,
                                "reason" to actionType,
                                "status" to "reached",
                                "time" to FieldValue.serverTimestamp()
                            )

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

                            // Sync with deliveries subcollection
                            val deliveriesCollectionRef = customerRef.collection("deliveries")
                            deliveriesCollectionRef.document(todayDate).set(newEntry)

                            // Clean up the subcollection older than 30 days
                            deliveriesCollectionRef.get(Source.CACHE).addOnCompleteListener { subcollectionTask ->
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
                        Toast.makeText(context, "Status Updated locally! Syncing in background.", Toast.LENGTH_SHORT).show()
                    }
                    dialog.show(it.supportFragmentManager, "DeliveryFormDialog")
                }
            } else {
                Toast.makeText(
                    context,
                    "You must be within 50m of customer location to update. Current distance: ${distanceInMeters.toInt()}m",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(context, "Location not available. Please wait for GPS location.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDistance(holder: CardViewHolder, customerLatLng: LatLng) {
        val currentLocation = currentUserLocation
        if (currentLocation != null) {
            val result = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude,
                currentLocation.longitude,
                customerLatLng.latitude,
                customerLatLng.longitude,
                result
            )

            val distanceInMeters = result[0]
            holder.distanceText.text = if (distanceInMeters < 1000) {
                String.format("%.0f m away", distanceInMeters)
            } else {
                val distanceInKm = distanceInMeters / 1000
                String.format("%.2f km away", distanceInKm)
            }
        } else {
            holder.distanceText.text = "error"
        }
    }

    fun updateCurrentLocation(newLocation: LatLng) {
        val result = FloatArray(1)
        Location.distanceBetween(
            currentUserLocation?.latitude ?: 0.0,
            currentUserLocation?.longitude ?: 0.0,
            newLocation.latitude,
            newLocation.longitude,
            result
        )

        val distanceMoved = result[0]
        if (distanceMoved > 10) {
            currentUserLocation = newLocation
            notifyItemRangeChanged(0, itemCount)
        }
    }



    private fun animateRefreshButton(button: ImageView) {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 500
            repeatCount = 0
            interpolator = LinearInterpolator()
        }
        button.startAnimation(rotate)
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

    private fun updateDeliveryUI(holder: CardViewHolder, status: String?) {
        if (status != null) {
            if (status == "delivered") {
                holder.deliveryButton.text = "DELIVERED"
                holder.deliveryButton.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.green)
                )
                holder.deliveryButton.isEnabled = false
                holder.deliveryButton.isClickable = false
            } else if (status == "reached") {
                holder.deliveryButton.text = "CHECKED"
                holder.deliveryButton.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.maincolor)
                )
                holder.deliveryButton.isEnabled = false
                holder.deliveryButton.isClickable = false
            } else {
                holder.deliveryButton.text = "UPDATE"
                holder.deliveryButton.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.red)
                )
                holder.deliveryButton.isEnabled = true
                holder.deliveryButton.isClickable = true
            }
        } else {
            holder.deliveryButton.text = "UPDATE"
            holder.deliveryButton.setBackgroundColor(
                ContextCompat.getColor(context, R.color.red)
            )
            holder.deliveryButton.setTextColor(
                ContextCompat.getColor(context, android.R.color.white)
            )
            holder.deliveryButton.isEnabled = true
            holder.deliveryButton.isClickable = true
        }
    }


}