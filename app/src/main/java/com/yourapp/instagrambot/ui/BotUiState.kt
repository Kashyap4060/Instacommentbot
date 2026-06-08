package com.yourapp.instagrambot.ui

/** A single timestamped line in the live activity log. */
data class ActivityEntry(
    val timestamp: String,
    val message: String
)

/** Pure helper for managing the bounded activity log. */
object ActivityLog {
    const val MAX_ENTRIES = 100

    fun append(current: List<ActivityEntry>, entry: ActivityEntry): List<ActivityEntry> {
        val updated = current + entry
        return if (updated.size > MAX_ENTRIES) updated.takeLast(MAX_ENTRIES) else updated
    }
}

/** Immutable snapshot of everything the UI renders. */
data class BotUiState(
    val serviceEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val statusMessage: String = "Bot is idle.",
    val activityLog: List<ActivityEntry> = emptyList(),
    val commentsPosted: Int = 0,
    val commentsLoaded: Int = 0
)
