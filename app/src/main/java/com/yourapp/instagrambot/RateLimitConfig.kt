package com.yourapp.instagrambot

data class RateLimitConfig(
    var minDelayMs: Long = 45000,
    var maxDelayMs: Long = 90000,
    var maxCommentsPerHour: Int = 15,
    var maxCommentsPerSession: Int = 50,
    var cooldownOnBlockMinutes: Int = 15
)
