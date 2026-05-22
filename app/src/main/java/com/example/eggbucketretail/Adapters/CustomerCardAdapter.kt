package com.example.eggbucketretail.Adapters



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
import com.example.eggbucketretail.DeliveryFormDialog
import com.example.eggbucketretail.Models.Customer
import com.example.eggbucketretail.R
import com.example.eggbucketretail.isReachedType
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

    private fun handleDeliveryButtonClick(holder: CardViewHolder, customerLatLng: LatLng,uid:String) {
        if(holder.deliveryButton.text.equals("DELIVERED") || holder.deliveryButton.text.equals("REACHED")){
            holder.deliveryButton.isEnabled=false
            holder.deliveryButton.isClickable=false
        }
        val currentLocation=currentUserLocation
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
            //radius distance
            if (distanceInMeters <= 50f) {
                val fragmentActivity = context as? FragmentActivity
                fragmentActivity?.let {
                    val dialog = DeliveryFormDialog(getItem(holder.adapterPosition)) { actionType ->
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                        // Fetch Agent Name
                        FirebaseFirestore.getInstance().collection("DeliveryMan").document(currentUserId).get()
                            .addOnSuccessListener { agentDoc ->
                                val agentName = agentDoc.getString("name") ?: "Unknown"

                                FirebaseFirestore.getInstance().collection("customers").document(uid).get()
                                    .addOnSuccessListener { customerDoc ->
                                        val last8Days = customerDoc.get("last8Days") as? Map<String, Any> ?: emptyMap()
                                        val updateData = hashMapOf<String, Any>()

                                        // Cleanup logic: keep only dates from the last 8 days
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        val calendar = Calendar.getInstance()
                                        calendar.add(Calendar.DAY_OF_YEAR, -8)
                                        val thresholdDate = calendar.time

                                        last8Days.keys.forEach { dateKey ->
                                            try {
                                                val entryDate = sdf.parse(dateKey)
                                                if (entryDate != null && entryDate.before(thresholdDate)) {
                                                    updateData["last8Days.$dateKey"] = FieldValue.delete()
                                                }
                                            } catch (e: Exception) {
                                                // Ignore unparseable dates
                                            }
                                        }

                                        // Add reached status
                                        updateData["last8Days.$todayDate"] = hashMapOf(
                                            "agentId" to currentUserId,
                                            "agentName" to agentName,
                                            "reason" to actionType,
                                            "status" to "reached",
                                            "time" to FieldValue.serverTimestamp()
                                        )

                                        FirebaseFirestore.getInstance()
                                            .collection("customers")
                                            .document(uid)
                                            .update(updateData)
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Status Updated!", Toast.LENGTH_SHORT).show()
                                                // Status will be updated via the fragment's snapshot listener
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                    }
                            }
                    }
                    dialog.show(it.supportFragmentManager, "DeliveryFormDialog")
                }
            } else {
                Toast.makeText(context, "Reach the destination", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
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
            if (location.contains("Lat:") && location.contains("Lng:")) {
                val latString = location.substringAfter("Lat:").substringBefore(",").trim()
                val lngString = location.substringAfter("Lng:").trim()
                val lat = latString.toDouble()
                val lng = lngString.toDouble()
                LatLng(lat, lng)
            } else {
                val parts = location.split(",").map { it.trim() }
                if (parts.size >= 2) {
                    LatLng(parts[0].toDouble(), parts[1].toDouble())
                } else null
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