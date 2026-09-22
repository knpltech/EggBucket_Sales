package com.example.eggbucketsales.Models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Customer(
    @DocumentId
    val uid: String = "",
    val name: String = "",
    val business: String = "",
    val phone: String = "",
    val imageUrl: String = "",
    val createdby: String = "",
    val createdAt: Long = 0,
    val location: String = "",
    val showOnMap: Boolean = true,
    var status: String? = null,
    val lastDeliveredDate: String? = null,
    val lastDeliveredQty: Int = 0,
    val lastDeliveredAmount: Int = 0,
    val lastDeliveredAgent: String? = null,
    val category: String = "",
    val isD0OrD1: Boolean = false,
    var deliveredCount: Int = 0,
    var computedFrequency: String = "D0",
    val address: String = "",
    val zone: String = "",
    val route: String = "",
    val businessType: String = "",
    val customerType: String = "",

    @get:PropertyName("Peak_Frequency")
    @set:PropertyName("Peak_Frequency")
    var peakFrequency: String = "",

    @get:PropertyName("Peak_Potential")
    @set:PropertyName("Peak_Potential")
    var peakPotential: String = "",

    val last8Days: @RawValue Map<String, Any?> = emptyMap()
) : Parcelable
