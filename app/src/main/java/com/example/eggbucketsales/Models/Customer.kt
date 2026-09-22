package com.example.eggbucketsales.Models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Customer(
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
    val isD0OrD1: Boolean = false
): Parcelable

