package com.example.eggbucketretail

import java.text.SimpleDateFormat
import java.util.*

fun isOlderThan30Days(dateStr: String, todayDateStr: String): Boolean {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    return try {
        val date = sdf.parse(dateStr)
        val todayDate = sdf.parse(todayDateStr)
        if (date != null && todayDate != null) {
            val diffInMs = todayDate.time - date.time
            val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
            diffInDays >= 30
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
