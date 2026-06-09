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

    private val LIKE_CHANCE = 15        // % of reels to like at random (Rule 1)
    private val LOOP_SETTLE_MS = 1200L  // breather between driver-loop passes
    private val DECIDE_MIN_MS = 10000L  // min "watch the reel" time before deciding
    private val DECIDE_MAX_MS = 15000L  // max "watch the reel" time before deciding
    private val UNKNOWN_MAX_RECOVER = 4 // unknown screens in a row before re-navigating to Reels

    // Run the (blocking) bot flow off the accessibility main thread so the long
    // Thread.sleep delays never freeze the service / trigger an ANR.
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var loopActive = false
    @Volatile private var pendingNavigation = false
    @Volatile private var pendingSearch = false   // navigating to a hashtag feed
    private var searchTextEntered = false          // hashtag already typed + submitted this run
    @Volatile private var inHashtagFeed = false    // commenting inside a hashtag's media feed
    private var consecutiveUnknown = 0

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

    // The bot is driven by a self-scheduling loop (runLoop), NOT by accessibility events:
    // a settled reel emits no events, so an event-driven loop stalls after one action.
    // We must implement this callback for the service to run, but it does no work.
    override fun onAccessibilityEvent(event: AccessibilityEvent) { }

    /**
     * Self-scheduling driver loop. Runs on the worker thread for the whole session so the
     * bot advances reel-to-reel on its own. Pacing between comments comes from the
     * rate-limiter delay inside scrollToNextReel; LOOP_SETTLE_MS is just a brief breather
     * between passes. Exits when stopBot() clears isRunning.
     */
    private fun runLoop() {
        if (loopActive) return
        loopActive = true
        try {
            while (isRunning) {
                try {
                    processOnce()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in bot loop", e)
                }
                delay(LOOP_SETTLE_MS)
            }
        } finally {
            loopActive = false
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

        if (pendingSearch) {
            navigateToHashtag(root)
            return
        }

        when {
            inHashtagFeed -> {
                consecutiveUnknown = 0
                diag("Hashtag feed — handling post")
                handleReelsFlow(root)
            }
            isReelsScreen(root) -> {
                consecutiveUnknown = 0
                diag("Reels screen detected")
                handleReelsFlow(root)
            }
            isPostScreen(root) -> {
                consecutiveUnknown = 0
                diag("Post screen detected")
                handlePostFlow(root)
            }
            else -> handleUnknownScreen(root)
        }
    }

    /**
     * The current screen matched no reel/post/search markers — most often a reel variant we
     * don't recognise (ads, sponsored, a transient loading frame). Recover by swiping to the
     * next reel instead of spinning on it. If it keeps happening we've probably drifted off
     * the Reels feed entirely, so re-navigate to Reels as a stronger recovery.
     */
    private fun handleUnknownScreen(root: AccessibilityNodeInfo) {
        consecutiveUnknown++
        dumpScreen(root)
        if (consecutiveUnknown >= UNKNOWN_MAX_RECOVER) {
            status("Unknown screen x$consecutiveUnknown — navigating back to Reels")
            consecutiveUnknown = 0
            pendingNavigation = true
            return
        }
        diag("Unknown screen ($consecutiveUnknown) — swiping to next reel to recover")
        scrollToNextReel()
    }

    private fun handleReelsFlow(root: AccessibilityNodeInfo) {
        val author = currentReelAuthor(root)

        // Human-like watch/decide time: "watch" the reel for 10–15s before deciding
        // whether to like, comment, or skip.
        val thinkMs = Random.nextLong(DECIDE_MIN_MS, DECIDE_MAX_MS + 1)
        status("Watching ${author}'s reel — deciding (${thinkMs / 1000}s)…")
        delay(thinkMs)
        if (!isRunning) return  // honour a Stop pressed during the watch window

        // Re-read the screen: nodes captured before the watch delay may now be stale.
        val reel = rootInActiveWindow ?: root

        // Rule 1: random likes on reels, independent of commenting (~LIKE_CHANCE%).
        if (Random.nextInt(100) < LIKE_CHANCE) {
            status("Liking ${author}'s reel")
            likeCurrentPost(reel)
        }

        // Rule 2: skip commenting on ~1 in 10 reels, at random (still like + scroll).
        val skipComment = Random.nextInt(10) == 0
        // Comment rate limits (hourly/daily/cooldown) also gate commenting, but never
        // liking or scrolling — the bot keeps moving so the driver loop can't stall.
        when {
            skipComment -> status("Skipping comment on ${author}'s reel (random 1-in-10)")
            !rateLimiter.canPerformAction() ->
                diag("Not commenting on ${author}'s reel (rate limit/cooldown active)")
            else -> commentOnCurrentReel(reel, author)
        }

        scrollToNextReel()
    }

    /** Open comments, type the next CSV comment, post it. Assumes commenting is allowed. */
    private fun commentOnCurrentReel(root: AccessibilityNodeInfo, author: String) {
        val commentButton = findCommentButton(root)
        if (commentButton == null) {
            diag("Comment button not found on ${author}'s reel")
            return
        }
        status("Opening comments on ${author}'s reel")
        performClick(commentButton)
        delay(1000)

        val freshRoot = rootInActiveWindow ?: return
        if (checkForBlocks(freshRoot)) return

        val input = findCommentInputField(freshRoot)
        if (input == null) {
            diag("Comment input field not found after opening comments")
            return
        }

        val comment = commentManager.getNextComment()
        setText(input, comment)
        delay(500)

        val postBtn = findPostButton(freshRoot)
        if (postBtn == null) {
            diag("Post button not found after typing comment")
            return
        }
        performClick(postBtn)
        rateLimiter.recordAction()
        commentsPostedThisSession++
        status("Posted on ${author}'s reel: \"$comment\"")
        delay(1000)
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

    /**
     * Drive Instagram's search UI to a hashtag's media feed, one step per pass. Screen-detection
     * based (not a fixed step counter) so it tolerates load delays and retries naturally:
     * each pass figures out which sub-screen we're on and advances. View-ids verified on-device.
     */
    private fun navigateToHashtag(root: AccessibilityNodeInfo) {
        val tag = currentQuery ?: run { pendingSearch = false; return }

        // Furthest step first. 1) Hashtag grid is up → open the first post, enter the feed.
        val grid = findNodeByResourceId(root, "$IG:id/grid_card_layout_container")
        if (grid != null) {
            status("Opening first post under #$tag")
            performClick(grid)
            delay(2500)
            pendingSearch = false
            inHashtagFeed = true
            return
        }

        // 2) Hashtag result rows → tap the exact match, else the first row.
        val hashtagRow = findHashtagRow(root, tag)
        if (hashtagRow != null) {
            status("Opening #$tag")
            performClick(hashtagRow)
            delay(2500)
            return
        }

        // 3) Submitted, tabs showing but no hashtag rows yet → open the "Tags" tab.
        if (searchTextEntered) {
            val tagsTab = findNodeByIdAndText(root, "$IG:id/igds_prism_chip_label", "Tags")
            if (tagsTab != null) {
                diag("Opening Tags results for #$tag")
                performClick(tagsTab)
                delay(1500)
                return
            }
        }

        // 4) Search input available → type "#tag" once and submit (biases live suggestions to tags).
        val input = findNodeByResourceId(root, "$IG:id/action_bar_search_edit_text")
        if (input != null) {
            if (!searchTextEntered) {
                status("Searching for #$tag")
                performClick(input)              // open the typing screen / focus the field
                delay(800)
                val fresh = findNodeByResourceId(rootInActiveWindow ?: root,
                    "$IG:id/action_bar_search_edit_text") ?: input
                setText(fresh, "#$tag")
                delay(800)
                submitSearch(fresh)
                searchTextEntered = true
                delay(1500)
            }
            return
        }

        // 5) Not on a search screen yet → open the Search/Explore tab.
        val searchTab = findNodeByResourceId(root, "$IG:id/search_tab")
            ?: findNodeByContentDescription(root, "Search and explore")
        if (searchTab != null) {
            diag("Opening Search tab")
            performClick(searchTab)
            delay(1500)
            return
        }

        diag("Search: waiting for a known search screen…")
        dumpScreen(root)
    }

    /** Hashtag row matching #tag exactly if present, otherwise the first hashtag row. */
    private fun findHashtagRow(root: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        return findNodeByIdAndText(root, "$IG:id/row_hashtag_textview_tag_name", "#$tag")
            ?: findNodeByResourceId(root, "$IG:id/row_hashtag_container")
    }

    /** Fire the IME "search" action on the focused field (API 30+; the device is Android 11). */
    private fun submitSearch(input: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
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

    /**
     * Close the comment sheet (and soft keyboard) before swiping, otherwise the
     * swipe lands inside the still-open comment list and scrolls comments instead
     * of advancing to the next reel. Up to two back presses: the first dismisses
     * the keyboard, the second collapses the sheet. Each press is guarded so we
     * never press back once too many and leave the Reels screen.
     */
    private fun closeCommentSheet() {
        repeat(2) {
            val root = rootInActiveWindow ?: return
            // Comment composer gone => sheet already collapsed, nothing to do.
            if (findCommentInputField(root) == null) return
            diag("Closing comment sheet before scroll")
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(700)
        }
    }

    private fun scrollToNextReel() {
        closeCommentSheet()
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

    /** Find a node matching both a resource-id and an exact (case-insensitive, trimmed) text. */
    private fun findNodeByIdAndText(node: AccessibilityNodeInfo?, id: String, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName == id &&
            node.text?.toString()?.trim().equals(text, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByIdAndText(node.getChild(i), id, text)
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
        // Normalize: strip a leading '#' and whitespace; blank => no search (generic Reels).
        val tag = query?.trim()?.removePrefix("#")?.trim()
        currentQuery = if (tag.isNullOrBlank()) null else tag
        commentsPostedThisSession = 0
        pendingNavigation = currentQuery == null   // generic Reels feed
        pendingSearch = currentQuery != null       // hashtag feed
        searchTextEntered = false
        inHashtagFeed = false
        consecutiveUnknown = 0
        lastScreenSignature = ""

        val intent = packageManager.getLaunchIntentForPackage(IG)
            ?: packageManager.getLaunchIntentForPackage(IG_LITE)

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            val dest = currentQuery?.let { "#$it" } ?: "Reels"
            status("Instagram launched. Navigating to $dest…")
            worker.execute { runLoop() }
        } else {
            stopBot()
            status("Instagram not found!")
        }
    }

    fun stopBot() {
        isRunning = false
        pendingNavigation = false
        pendingSearch = false
        inHashtagFeed = false
        status("Bot stopped.")
    }
}
