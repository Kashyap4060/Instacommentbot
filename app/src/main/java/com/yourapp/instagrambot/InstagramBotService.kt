package com.yourapp.instagrambot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.Executors
import kotlin.random.Random

class InstagramBotService : AccessibilityService() {

    private val TAG = "BotService"
    private var isRunning = false
    private var currentQuery: String? = null

    private lateinit var commentManager: CommentManager
    private lateinit var rateLimiter: RateLimiter

    private var commentsPostedThisSession = 0

    // Run the (blocking) bot flow off the accessibility main thread so the long
    // Thread.sleep delays never freeze the service / trigger an ANR.
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var isProcessing = false
    @Volatile private var pendingNavigation = false

    // Dedup key so we don't spam logcat with identical screen dumps every event.
    private var lastScreenSignature = ""

    companion object {
        private const val IG = "com.instagram.android"
        private const val IG_LITE = "com.instagram.lite"
        private const val CHANNEL_ID = "bot_status"
        private const val NOTI_ID = 1001
        private var instance: InstagramBotService? = null
        fun getInstance(): InstagramBotService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        commentManager = CommentManager(this)
        rateLimiter = RateLimiter(this)
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        worker.shutdownNow()
        cancelNotification()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRunning) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != IG && packageName != IG_LITE) return

        // Only one flow iteration at a time. Events fire constantly; we coalesce
        // them into a single background pass.
        if (isProcessing) return
        isProcessing = true
        worker.execute {
            try {
                processOnce()
            } catch (e: Exception) {
                Log.e(TAG, "Error in bot loop", e)
            } finally {
                isProcessing = false
            }
        }
    }

    /** One pass of the state machine, run on the background worker thread. */
    private fun processOnce() {
        val root = rootInActiveWindow ?: run {
            diag("No active window content available yet")
            return
        }

        if (pendingNavigation) {
            status("On Instagram — navigating to Reels…")
            if (navigateToReels(root)) {
                pendingNavigation = false
                status("Opened Reels. Watching feed…")
            } else {
                diag("Reels tab not found on this screen yet; waiting…")
                dumpScreen(root)
            }
            return
        }

        when {
            isReelsScreen(root) -> {
                diag("Reels screen detected")
                handleReelsFlow(root)
            }
            isPostScreen(root) -> {
                diag("Post screen detected")
                handlePostFlow(root)
            }
            isSearchScreen(root) -> {
                diag("Search screen detected")
                handleSearchFlow(root)
            }
            else -> {
                diag("Unknown screen — no reel/post/search markers found")
                dumpScreen(root)
            }
        }
    }

    private fun handleReelsFlow(root: AccessibilityNodeInfo) {
        if (!rateLimiter.canPerformAction()) {
            diag("Rate limiter: skipping (limit/cooldown active)")
            return
        }

        val author = currentReelAuthor(root)

        // 1. Deciding to skip (10% chance)
        if (Random.nextInt(100) < 10) {
            status("Skipping ${author}'s reel (random skip)")
            scrollToNextReel()
            return
        }

        // 2. Decide to like (30% chance)
        if (Random.nextInt(100) < 30) {
            status("Liking ${author}'s reel")
            likeCurrentPost(root)
        }

        // 3. Commenting
        val commentButton = findCommentButton(root)
        if (commentButton != null) {
            status("Opening comments on ${author}'s reel")
            performClick(commentButton)
            delay(1000)

            val freshRoot = rootInActiveWindow ?: return
            if (checkForBlocks(freshRoot)) return

            val input = findCommentInputField(freshRoot)
            if (input != null) {
                val comment = commentManager.getNextComment()
                setText(input, comment)
                delay(500)

                val postBtn = findPostButton(freshRoot)
                if (postBtn != null) {
                    performClick(postBtn)
                    rateLimiter.recordAction()
                    commentsPostedThisSession++
                    status("Posted on ${author}'s reel: \"$comment\"")
                    delay(1000)
                } else {
                    diag("Post button not found after typing comment")
                }
            } else {
                diag("Comment input field not found after opening comments")
            }
        } else {
            diag("Comment button not found on ${author}'s reel")
        }

        scrollToNextReel()
    }

    private fun handlePostFlow(root: AccessibilityNodeInfo) {
        // Similar to reels but on a regular post
        if (!rateLimiter.canPerformAction()) {
            diag("Rate limiter: skipping (limit/cooldown active)")
            return
        }

        val commentButton = findCommentButton(root)
        if (commentButton != null) {
            status("Opening comments on post")
            performClick(commentButton)
            delay(1000)
            // ... rest of commenting logic
        } else {
            diag("Comment button not found on post screen")
        }
    }

    private fun handleSearchFlow(root: AccessibilityNodeInfo) {
        // Logic to click the first result if we just searched
        val firstResult = findNodeByResourceId(root, "$IG:id/row_feed_image_view")
        if (firstResult != null) {
            status("Opening first search result")
            performClick(firstResult)
        } else {
            diag("No search result row found yet")
        }
    }

    // --- Navigation ---

    /** Best-effort: click the Reels tab in the bottom navigation bar. */
    private fun navigateToReels(root: AccessibilityNodeInfo): Boolean {
        val tab = findNodeByContentDescription(root, "Reels")
            ?: findNodeByResourceId(root, "$IG:id/reels_tab")
            ?: findNodeByResourceId(root, "$IG:id/clips_tab")
        if (tab != null) {
            performClick(tab)
            delay(2500)
            return true
        }
        return false
    }

    // --- Screen detection ---

    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean {
        return findNodeByResourceId(root, "$IG:id/reel_view") != null ||
            findNodeByResourceId(root, "$IG:id/clips_video_container") != null
    }

    private fun isPostScreen(root: AccessibilityNodeInfo): Boolean {
        return findNodeByResourceId(root, "$IG:id/row_feed_comment_button") != null
    }

    private fun isSearchScreen(root: AccessibilityNodeInfo): Boolean {
        return findNodeByResourceId(root, "$IG:id/search_edit_text") != null
    }

    /** Best-effort author handle for the currently visible reel. */
    private fun currentReelAuthor(root: AccessibilityNodeInfo): String {
        val byId = findNodeByResourceId(root, "$IG:id/clips_author_username")
            ?: findNodeByPartialId(root, "username")
        val text = byId?.text?.toString() ?: byId?.contentDescription?.toString()
        return if (!text.isNullOrBlank()) text.trim() else "this"
    }

    private fun likeCurrentPost(root: AccessibilityNodeInfo) {
        val likeBtn = findNodeByResourceId(root, "$IG:id/like_button")
            ?: findNodeByResourceId(root, "$IG:id/row_feed_heart_button")
            ?: findNodeByContentDescription(root, "Like")
        performClick(likeBtn)
        delay(500)
    }

    private fun scrollToNextReel() {
        status("Scrolling to next reel…")
        val path = Path().apply {
            moveTo(360f, 1200f)
            lineTo(360f, 400f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()
        dispatchGesture(gesture, null, null)
        delay(rateLimiter.getNextDelayMs())
    }

    private fun checkForBlocks(root: AccessibilityNodeInfo): Boolean {
        val blockTexts = listOf("Try Again Later", "Action Blocked", "temporarily blocked")
        for (text in blockTexts) {
            if (findNodeByText(root, text) != null) {
                Log.w(TAG, "Block detected: $text")
                rateLimiter.setCooldown(30)
                status("BLOCKED! Cooling down for 30 mins")
                val okBtn = findNodeByText(root, "OK") ?: findNodeByText(root, "Dismiss")
                performClick(okBtn)
                return true
            }
        }
        return false
    }

    private fun findCommentButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Real Reels id (verified on device): comment_button. Older feed id kept as fallback.
        return findNodeByResourceId(node, "$IG:id/comment_button")
            ?: findNodeByResourceId(node, "$IG:id/row_feed_comment_button")
            ?: findNodeByContentDescription(node, "Comment")
            ?: findNodeByText(node, "Comment")
    }

    private fun findCommentInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Real comment-composer field (verified on device): the multiline edittext.
        return findNodeByResourceId(node, "$IG:id/layout_comment_thread_edittext_multiline")
            ?: findNodeByResourceId(node, "$IG:id/comment_text_field")
            ?: findNodeByResourceId(node, "$IG:id/layout_comment_thread_edittext")
    }

    private fun findPostButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Real post button (verified on device): appears once text is entered.
        return findNodeByResourceId(node, "$IG:id/layout_comment_thread_post_button_icon")
            ?: findNodeByContentDescription(node, "Post")
            ?: findNodeByText(node, "Post")
            ?: findNodeByResourceId(node, "$IG:id/button_post")
    }

    private fun performClick(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        else performClick(node.parent)
    }

    private fun setText(node: AccessibilityNodeInfo?, text: String) {
        if (node == null) return
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNodeByResourceId(node: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName == id) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByResourceId(node.getChild(i), id)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByPartialId(node: AccessibilityNodeInfo?, substring: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName?.contains(substring, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByPartialId(node.getChild(i), substring)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByText(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByContentDescription(node.getChild(i), desc)
            if (found != null) return found
        }
        return null
    }

    /**
     * Logs the resource-ids / text / content-descriptions of interactive nodes on the
     * current screen, so we can discover the real Instagram view ids for this app
     * version. Deduplicated by screen signature to avoid flooding logcat.
     */
    private fun dumpScreen(root: AccessibilityNodeInfo) {
        val lines = mutableListOf<String>()
        collectInteresting(root, lines)
        val signature = lines.joinToString("|").take(400)
        if (signature == lastScreenSignature) return
        lastScreenSignature = signature

        Log.d(TAG, "----- SCREEN DUMP (${lines.size} nodes) -----")
        lines.take(40).forEach { Log.d(TAG, it) }
        if (lines.size > 40) Log.d(TAG, "...(${lines.size - 40} more)")
        Log.d(TAG, "----- END SCREEN DUMP -----")
    }

    private fun collectInteresting(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        val id = node.viewIdResourceName
        val text = node.text?.toString()
        val cd = node.contentDescription?.toString()
        if (!id.isNullOrBlank() || !text.isNullOrBlank() || !cd.isNullOrBlank()) {
            val click = if (node.isClickable) " [clickable]" else ""
            out.add("id=$id text=$text desc=$cd$click")
        }
        for (i in 0 until node.childCount) collectInteresting(node.getChild(i), out)
    }

    private fun delay(ms: Long) {
        try { Thread.sleep(ms) } catch (e: Exception) { }
    }

    /** Important event: logcat + UI broadcast + notification. */
    private fun status(message: String) {
        Log.d(TAG, message)
        val intent = Intent("BOT_STATUS")
        intent.putExtra("status", message)
        intent.putExtra("commentsPosted", commentsPostedThisSession)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        updateNotification(message)
    }

    /** Noisy diagnostic: logcat + UI broadcast only (no notification spam). */
    private fun diag(message: String) {
        Log.d(TAG, message)
        val intent = Intent("BOT_STATUS")
        intent.putExtra("status", message)
        intent.putExtra("commentsPosted", commentsPostedThisSession)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bot Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live status of the Instagram bot" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Instagram Bot — $commentsPostedThisSession posted")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(isRunning)
                .setOnlyAlertOnce(true)
                .build()
            getSystemService(NotificationManager::class.java)?.notify(NOTI_ID, notification)
        } catch (e: Exception) {
            // POST_NOTIFICATIONS may be denied on API 33+; logging still works.
            Log.w(TAG, "Could not post notification: ${e.message}")
        }
    }

    private fun cancelNotification() {
        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTI_ID)
        } catch (e: Exception) { }
    }

    fun startBot(query: String? = null) {
        isRunning = true
        currentQuery = query
        commentsPostedThisSession = 0
        pendingNavigation = true
        lastScreenSignature = ""

        val intent = packageManager.getLaunchIntentForPackage(IG)
            ?: packageManager.getLaunchIntentForPackage(IG_LITE)

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            status("Instagram launched. Navigating to Reels…")
        } else {
            stopBot()
            status("Instagram not found!")
        }
    }

    fun stopBot() {
        isRunning = false
        pendingNavigation = false
        status("Bot stopped.")
    }
}
