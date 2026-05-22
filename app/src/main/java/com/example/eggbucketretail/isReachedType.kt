package com.example.eggbucketretail

fun isReachedType(type: String?): Boolean {
    return type == "reached" ||
            type == "shop_closed" ||
            type == "stock_available" ||
            type == "other_vendor"
}