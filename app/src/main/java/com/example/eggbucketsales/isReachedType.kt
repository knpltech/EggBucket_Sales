package com.example.eggbucketsales

fun isReachedType(type: String?): Boolean {
    return type == "reached" ||
            type == "stock_available" ||
            type == "other_vendor" ||
            type == "price_issue" ||
            type == "shop_closed" ||
            type == "confirmed_tomorrow" ||
            type == "not_interested" ||
            type == "busy_right_now" ||
            type == "busy_now" ||
            type == "not_available" ||
            type == "owner_not_available" ||
            type == "quality_issue" ||
            type == "need_credit"
}