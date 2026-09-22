package com.example.eggbucketsales.Repository

import com.example.eggbucketsales.Models.Customer
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class LastDeliveryDetails(
    val lastDeliveredDate: String? = null,
    val lastDeliveredQty: Int = 0,
    val lastDeliveredAmount: Int = 0,
    val lastDeliveredAgent: String? = null
)

class CustomerRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val customersCollection = firestore.collection("customers")

    companion object {
        private val IST_TIMEZONE = TimeZone.getTimeZone("Asia/Kolkata")

        /**
         * Generates date strings for today + previous 7 days (8 days total) in IST timezone (yyyy-MM-dd)
         */
        fun getLast8DaysDateStrings(): List<String> {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
                timeZone = IST_TIMEZONE
            }

            val dates = mutableListOf<String>()
            val calendar = Calendar.getInstance(IST_TIMEZONE)

            for (i in 0..7) {
                dates.add(dateFormat.format(calendar.time))
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

            return dates
        }

        /**
         * Counts delivered orders in the last 8 days rolling window (Today + past 7 days)
         * Matching web CustomerManagement logic
         */
        fun calculateDeliveredCount(
            last8Days: Map<*, *>?,
            targetDates: List<String>
        ): Int {
            if (last8Days.isNullOrEmpty()) return 0

            var count = 0
            for (dateKey in targetDates) {
                val entry = last8Days[dateKey] ?: continue

                val isDelivered = when (entry) {
                    is String -> entry.trim().equals("delivered", ignoreCase = true)
                    is Map<*, *> -> {
                        val status = entry["status"] as? String ?: entry["type"] as? String
                        status?.trim()?.equals("delivered", ignoreCase = true) == true
                    }
                    else -> false
                }

                if (isDelivered) {
                    count++
                }
            }

            return minOf(count, 7)
        }

        /**
         * Checks if customer was newly added within the last 45 days
         */
        fun isCustomerNewlyAdded(createdAt: Long): Boolean {
            if (createdAt <= 0L) return false
            val calendar = Calendar.getInstance(IST_TIMEZONE)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -45)
            val last45DaysMillis = calendar.timeInMillis
            return createdAt >= last45DaysMillis
        }

        /**
         * Checks if customer is accessible to sales agents:
         * Either newly added (< 45 days) OR inactive/lapse customer (D0 / D1).
         */
        fun isCustomerAccessible(customer: Customer?): Boolean {
            if (customer == null) return false
            return isCustomerNewlyAdded(customer.createdAt) || customer.isD0OrD1
        }

        /**
         * Extracts most recent delivery info from customer's last8Days map
         */
        fun extractLastDeliveryDetails(last8Days: Map<*, *>?): LastDeliveryDetails {
            if (last8Days.isNullOrEmpty()) return LastDeliveryDetails()

            val deliveredEntries = mutableListOf<Pair<String, Map<*, *>>>()
            for ((key, value) in last8Days) {
                val dateStr = key as? String ?: continue
                if (value is Map<*, *>) {
                    val status = (value["status"] as? String ?: value["type"] as? String)?.trim()
                    if (status.equals("delivered", ignoreCase = true)) {
                        deliveredEntries.add(Pair(dateStr, value))
                    }
                } else if (value is String && value.trim().equals("delivered", ignoreCase = true)) {
                    deliveredEntries.add(Pair(dateStr, emptyMap<String, Any>()))
                }
            }

            if (deliveredEntries.isEmpty()) return LastDeliveryDetails()

            deliveredEntries.sortByDescending { it.first }
            val mostRecent = deliveredEntries.first()
            val latestDate = mostRecent.first
            val entryMap = mostRecent.second

            val latestQty = (entryMap["quantity"] as? Number)?.toInt()
                ?: (entryMap["quantity"] as? String)?.toIntOrNull() ?: 0

            val total = (entryMap["totalAmount"] as? Number)?.toInt()
                ?: (entryMap["totalAmount"] as? String)?.toIntOrNull()

            val latestAmount = if (total != null && total > 0) {
                total
            } else {
                val cash = (entryMap["cashAmount"] as? Number)?.toInt()
                    ?: (entryMap["cashAmount"] as? String)?.toIntOrNull() ?: 0
                val upi = (entryMap["upiAmount"] as? Number)?.toInt()
                    ?: (entryMap["upiAmount"] as? String)?.toIntOrNull() ?: 0
                cash + upi
            }
            val latestAgent = entryMap["agentName"] as? String

            return LastDeliveryDetails(
                lastDeliveredDate = latestDate,
                lastDeliveredQty = latestQty,
                lastDeliveredAmount = latestAmount,
                lastDeliveredAgent = latestAgent
            )
        }

        /**
         * Converts Firestore DocumentSnapshot to fully computed Customer model
         */
        fun processCustomer(doc: DocumentSnapshot, targetDates: List<String>): Customer? {
            val name = doc.getString("name") ?: return null
            val location = doc.getString("location") ?: ""
            val business = doc.getString("business") ?: doc.getString("businessType") ?: "Unknown"
            val phone = doc.getString("phone") ?: "N/A"
            val imageUrl = doc.getString("imageUrl") ?: ""
            val createdby = doc.getString("createdby") ?: ""

            val rawCreatedAt = when (val raw = doc.get("createdAt")) {
                is Number -> raw.toLong()
                is com.google.firebase.Timestamp -> raw.toDate().time
                is java.util.Date -> raw.time
                else -> 0L
            }
            val createdAt = if (rawCreatedAt in 1..99_999_999_999L) rawCreatedAt * 1000L else rawCreatedAt

            @Suppress("UNCHECKED_CAST")
            val last8Days = doc.get("last8Days") as? Map<String, Any?>

            val deliveredCount = calculateDeliveredCount(last8Days, targetDates)
            val computedFrequency = "D$deliveredCount"
            val isD0OrD1 = (deliveredCount == 0 || deliveredCount == 1 || last8Days.isNullOrEmpty())
            val effectiveCategory = computedFrequency

            val deliveryDetails = extractLastDeliveryDetails(last8Days)

            val todayDate = targetDates.firstOrNull() ?: ""
            val todayData = last8Days?.get(todayDate) as? Map<*, *>
            val dayStatus = todayData?.get("status") as? String

            val todayOverride = doc.get("todayOverride") as? Map<*, *>
            val overrideStatus = todayOverride?.get("status") as? String

            val effectiveStatus = if (overrideStatus?.lowercase() == "delivered" || overrideStatus?.lowercase() == "reached") {
                overrideStatus
            } else {
                dayStatus
            }

            return Customer(
                uid = doc.id,
                name = name,
                business = business,
                phone = phone,
                imageUrl = imageUrl,
                createdby = createdby,
                createdAt = createdAt,
                location = location,
                showOnMap = doc.getBoolean("showOnMap") ?: true,
                status = effectiveStatus,
                lastDeliveredDate = deliveryDetails.lastDeliveredDate,
                lastDeliveredQty = deliveryDetails.lastDeliveredQty,
                lastDeliveredAmount = deliveryDetails.lastDeliveredAmount,
                lastDeliveredAgent = deliveryDetails.lastDeliveredAgent,
                category = effectiveCategory,
                isD0OrD1 = isD0OrD1,
                deliveredCount = deliveredCount,
                computedFrequency = computedFrequency,
                address = doc.getString("address") ?: "",
                zone = doc.getString("zone") ?: "",
                route = doc.getString("route") ?: "",
                businessType = doc.getString("businessType") ?: "",
                customerType = doc.getString("customerType") ?: "",
                peakFrequency = doc.getString("Peak_Frequency") ?: doc.getString("peakFrequency") ?: doc.getString("category") ?: "",
                peakPotential = doc.getString("Peak_Potential") ?: doc.getString("peakPotential") ?: "",
                last8Days = last8Days ?: emptyMap()
            )
        }
    }

    /**
     * Fetches all customers and returns only (below 45 day) D0 and D1 customers
     */
    fun getSalesCustomers(
        onSuccess: (List<Customer>) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        val targetDates = getLast8DaysDateStrings()

        customersCollection.get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    val customer = processCustomer(doc, targetDates) ?: return@mapNotNull null
                    if (isCustomerAccessible(customer)) {
                        customer
                    } else {
                        null
                    }
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                onSuccess(list)
            }
            .addOnFailureListener { e ->
                onFailure?.invoke(e)
            }
    }

    /**
     * Real-time updates via Kotlin Flow for (below 45 day) D0 and D1 customers
     */
    fun getSalesCustomersFlow(): Flow<List<Customer>> = callbackFlow {
        val targetDates = getLast8DaysDateStrings()

        val listener = customersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val customer = processCustomer(doc, targetDates) ?: return@mapNotNull null
                    if (isCustomerAccessible(customer)) {
                        customer
                    } else {
                        null
                    }
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                trySend(list)
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Filter only strictly D0 & D1 customers
     */
    fun processAndFilterD0D1(snapshot: QuerySnapshot): List<Customer> {
        val targetDates = getLast8DaysDateStrings()

        return snapshot.documents.mapNotNull { doc ->
            val customer = processCustomer(doc, targetDates) ?: return@mapNotNull null
            if (customer.deliveredCount == 0 || customer.deliveredCount == 1 || customer.isD0OrD1) {
                customer
            } else {
                null
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
