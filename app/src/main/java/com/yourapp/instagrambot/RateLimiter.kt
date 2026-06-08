package com.yourapp.instagrambot

import android.content.Context
import android.util.Log
import kotlin.random.Random

class RateLimiter(private val context: Context) {
    private val TAG = "RateLimiter"
    private val PREFS_NAME = "rate_limit_prefs"
    private val KEY_HOURLY_COUNT = "hourly_count"
    private val KEY_DAILY_COUNT = "daily_count"
    private val KEY_LAST_ACTION_TIME = "last_action_time"
    private val KEY_COOLDOWN_UNTIL = "cooldown_until"

    // Limits
    private val MAX_COMMENTS_PER_HOUR = 15
    private val MAX_COMMENTS_PER_DAY = 50
    private val MIN_DELAY_MS = 45000L // 45s
    private val MAX_DELAY_MS = 90000L // 90s

    fun canPerformAction(): Boolean {
        val now = System.currentTimeMillis()
        val cooldownUntil = getCooldownUntil()
        
        if (now < cooldownUntil) {
            val remainingSec = (cooldownUntil - now) / 1000
            Log.d(TAG, "Cooldown active. Remaining: ${remainingSec}s")
            return false
        }

        if (getHourlyCount() >= MAX_COMMENTS_PER_HOUR) {
            Log.d(TAG, "Hourly limit reached.")
            return false
        }

        if (getDailyCount() >= MAX_COMMENTS_PER_DAY) {
            Log.d(TAG, "Daily limit reached.")
            return false
        }

        return true
    }

    fun recordAction() {
        val now = System.currentTimeMillis()
        setHourlyCount(getHourlyCount() + 1)
        setDailyCount(getDailyCount() + 1)
        setLastActionTime(now)
        Log.d(TAG, "Action recorded. Day total: ${getDailyCount()}, Hour total: ${getHourlyCount()}")
    }

    fun setCooldown(minutes: Int) {
        val until = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_COOLDOWN_UNTIL, until).apply()
        Log.w(TAG, "Cooldown set for $minutes minutes")
    }

    fun getNextDelayMs(): Long {
        return Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
    }

    private fun getHourlyCount(): Int {
        resetIfExpired()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HOURLY_COUNT, 0)
    }

    private fun getDailyCount(): Int {
        resetIfExpired()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DAILY_COUNT, 0)
    }

    private fun setHourlyCount(count: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_HOURLY_COUNT, count).apply()
    }

    private fun setDailyCount(count: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DAILY_COUNT, count).apply()
    }

    private fun setLastActionTime(time: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ACTION_TIME, time).apply()
    }

    private fun getCooldownUntil(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
    }

    private fun resetIfExpired() {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAction = prefs.getLong(KEY_LAST_ACTION_TIME, 0L)
        
        // Check hourly reset (3600000 ms)
        if (now - lastAction > 3600000) {
            setHourlyCount(0)
        }
        
        // Check daily reset (86400000 ms)
        if (now - lastAction > 86400000) {
            setDailyCount(0)
        }
    }
}
