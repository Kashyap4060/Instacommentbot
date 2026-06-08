package com.yourapp.instagrambot

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.yourapp.instagrambot.ui.ActivityEntry
import com.yourapp.instagrambot.ui.ActivityLog
import com.yourapp.instagrambot.ui.BotUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val commentManager = CommentManager(app)

    private val _uiState = MutableStateFlow(BotUiState())
    val uiState: StateFlow<BotUiState> = _uiState.asStateFlow()

    // One-shot UI events (snackbars).
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: return
            val count = intent.getIntExtra("commentsPosted", _uiState.value.commentsPosted)
            onStatus(status, count)
        }
    }

    init {
        refreshLoadedCount()
    }

    fun registerReceiver() {
        LocalBroadcastManager.getInstance(getApplication())
            .registerReceiver(statusReceiver, IntentFilter("BOT_STATUS"))
    }

    fun unregisterReceiver() {
        LocalBroadcastManager.getInstance(getApplication())
            .unregisterReceiver(statusReceiver)
    }

    private fun onStatus(status: String, count: Int) {
        val entry = ActivityEntry(currentTime(), status)
        _uiState.update {
            it.copy(
                statusMessage = status,
                commentsPosted = count,
                activityLog = ActivityLog.append(it.activityLog, entry)
            )
        }
    }

    fun refreshServiceState(enabled: Boolean) {
        _uiState.update { it.copy(serviceEnabled = enabled) }
    }

    fun refreshLoadedCount() {
        _uiState.update { it.copy(commentsLoaded = commentManager.getCommentCount()) }
    }

    fun importCsv(uri: Uri) {
        val ok = commentManager.importCsv(uri)
        if (ok) refreshLoadedCount()
        _events.trySend(if (ok) "Comments imported successfully!" else "Failed to import CSV")
    }

    fun onStartPressed(query: String?) {
        InstagramBotService.getInstance()?.startBot(query)
        _uiState.update { it.copy(isRunning = true, statusMessage = "Bot starting...") }
    }

    fun onStopPressed() {
        InstagramBotService.getInstance()?.stopBot()
        _uiState.update { it.copy(isRunning = false, statusMessage = "Bot stopping...") }
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
