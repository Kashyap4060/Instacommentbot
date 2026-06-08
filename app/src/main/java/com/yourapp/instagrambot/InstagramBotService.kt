package com.yourapp.instagrambot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.random.Random

class InstagramBotService : AccessibilityService() {

    private val TAG = "BotService"
    private var isRunning = false
    private var currentQuery: String? = null
    
    private lateinit var commentManager: CommentManager
    private lateinit var rateLimiter: RateLimiter
    
    private var commentsPostedThisSession = 0

    companion object {
        private var instance: InstagramBotService? = null
        fun getInstance(): InstagramBotService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        commentManager = CommentManager(this)
        rateLimiter = RateLimiter(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
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
        
        val root = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.instagram.android" && packageName != "com.instagram.lite") return

        // Simple state check based on visible UI
        when {
            isReelsScreen(root) -> handleReelsFlow(root)
            isPostScreen(root) -> handlePostFlow(root)
            isSearchScreen(root) -> handleSearchFlow(root)
        }
    }

    private fun handleReelsFlow(root: AccessibilityNodeInfo) {
        if (!rateLimiter.canPerformAction()) return

        // 1. Deciding to skip (10% chance)
        if (Random.nextInt(100) < 10) {
            Log.d(TAG, "Randomly skipping this reel")
            scrollToNextReel()
            return
        }

        // 2. Decide to like (30% chance)
        if (Random.nextInt(100) < 30) {
            likeCurrentPost(root)
        }

        // 3. Commenting
        val commentButton = findCommentButton(root)
        if (commentButton != null) {
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
                    sendStatusUpdate("Posted: $comment")
                    delay(1000)
                }
            }
        }
        
        scrollToNextReel()
    }

    private fun handlePostFlow(root: AccessibilityNodeInfo) {
        // Similar to reels but on a regular post
        if (!rateLimiter.canPerformAction()) return
        
        val commentButton = findCommentButton(root)
        if (commentButton != null) {
            performClick(commentButton)
            delay(1000)
            // ... rest of commenting logic
        }
    }

    private fun handleSearchFlow(root: AccessibilityNodeInfo) {
        // Logic to click the first result if we just searched
        val firstResult = findNodeByResourceId(root, "com.instagram.android:id/row_feed_image_view")
        if (firstResult != null) {
            performClick(firstResult)
        }
    }

    // --- Helper Methods ---

    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean {
        return findNodeByText(root, "Reels") != null || 
               findNodeByResourceId(root, "com.instagram.android:id/reel_view") != null
    }

    private fun isPostScreen(root: AccessibilityNodeInfo): Boolean {
        return findNodeByResourceId(root, "com.instagram.android:id/row_feed_comment_button") != null
    }
    
    private fun isSearchScreen(root: AccessibilityNodeInfo): Boolean {
        return findNodeByResourceId(root, "com.instagram.android:id/search_edit_text") != null
    }

    private fun likeCurrentPost(root: AccessibilityNodeInfo) {
        val likeBtn = findNodeByResourceId(root, "com.instagram.android:id/row_feed_heart_button")
        performClick(likeBtn)
        delay(500)
    }

    private fun scrollToNextReel() {
        sendStatusUpdate("Scrolling to next reel...")
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
                sendStatusUpdate("BLOCKED! Cooling down for 30 mins")
                val okBtn = findNodeByText(root, "OK") ?: findNodeByText(root, "Dismiss")
                performClick(okBtn)
                return true
            }
        }
        return false
    }

    private fun findCommentButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByResourceId(node, "com.instagram.android:id/row_feed_comment_button")
            ?: findNodeByText(node, "Comment")
    }

    private fun findCommentInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByResourceId(node, "com.instagram.android:id/comment_text_field")
            ?: findNodeByResourceId(node, "com.instagram.android:id/layout_comment_thread_edittext")
    }

    private fun findPostButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByText(node, "Post") ?: findNodeByResourceId(node, "com.instagram.android:id/button_post")
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

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByText(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    private fun delay(ms: Long) {
        try { Thread.sleep(ms) } catch (e: Exception) { }
    }

    private fun sendStatusUpdate(status: String) {
        val intent = Intent("BOT_STATUS")
        intent.putExtra("status", status)
        intent.putExtra("commentsPosted", commentsPostedThisSession)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun startBot(query: String? = null) {
        isRunning = true
        currentQuery = query
        commentsPostedThisSession = 0
        
        val intent = packageManager.getLaunchIntentForPackage("com.instagram.android")
            ?: packageManager.getLaunchIntentForPackage("com.instagram.lite")
            
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            sendStatusUpdate("Instagram launched. Navigating...")
            
            // Logic to handle initial navigation would go here (e.g. clicking search)
        } else {
            stopBot()
            sendStatusUpdate("Instagram not found!")
        }
    }

    fun stopBot() {
        isRunning = false
        sendStatusUpdate("Bot stopped.")
    }
}
