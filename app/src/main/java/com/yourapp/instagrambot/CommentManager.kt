package com.yourapp.instagrambot

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class CommentManager(private val context: Context) {
    private val TAG = "CommentManager"
    private val PREFS_NAME = "comment_prefs"
    private val KEY_COMMENTS = "comments_list"
    private val KEY_INDEX = "current_index"
    
    private var comments: MutableList<String> = mutableListOf()
    private var currentIndex = 0

    init {
        loadFromPrefs()
    }

    fun importCsv(uri: Uri): Boolean {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val newList = mutableListOf<String>()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.isNotBlank() == true) {
                    // Basic CSV parsing: take everything if no comma, or first part if comma exists
                    // Or more likely for comments, one per line.
                    newList.add(line!!.trim())
                }
            }
            reader.close()
            
            if (newList.isNotEmpty()) {
                comments = newList
                currentIndex = 0
                saveToPrefs()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing CSV", e)
        }
        return false
    }

    fun getNextComment(): String {
        if (comments.isEmpty()) return "Nice post!"
        
        val comment = comments[currentIndex]
        currentIndex = (currentIndex + 1) % comments.size
        // Rule 3: after the last comment, currentIndex wraps to 0 and we start over.
        if (currentIndex == 0) Log.d(TAG, "Reached end of comment list — cycling back to top")
        saveIndex()
        return comment
    }

    fun getCommentCount(): Int = comments.size

    private fun saveToPrefs() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            // Store as an ordered, newline-joined string. A StringSet (the old format)
            // loses CSV order and silently drops duplicate comments, so cycling through
            // the list "from the top" was non-deterministic. CSV is one comment per line,
            // so embedded newlines aren't a concern.
            putString(KEY_COMMENTS, comments.joinToString("\n"))
            putInt(KEY_INDEX, currentIndex)
            apply()
        }
    }

    private fun saveIndex() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_INDEX, currentIndex).apply()
    }

    private fun loadFromPrefs() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Read the ordered string. Guard against the old StringSet format (re-import needed).
        val stored = try { prefs.getString(KEY_COMMENTS, null) } catch (e: ClassCastException) { null }
        if (!stored.isNullOrEmpty()) {
            comments = stored.split("\n").toMutableList()
        }
        currentIndex = prefs.getInt(KEY_INDEX, 0)
        
        // Safety check
        if (comments.isNotEmpty() && currentIndex >= comments.size) {
            currentIndex = 0
        }
    }
}
