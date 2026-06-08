package com.yourapp.instagrambot

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import com.yourapp.instagrambot.ui.BotScreen
import com.yourapp.instagrambot.ui.theme.InstagramBotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val csvPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) viewModel.importCsv(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            InstagramBotTheme {
                val state by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var query by rememberSaveable { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                BotScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    query = query,
                    onQueryChange = { query = it },
                    onImportCsv = { csvPicker.launch("text/*") },
                    onStart = {
                        if (!isAccessibilityServiceEnabled()) {
                            openAccessibilitySettings()
                        } else {
                            viewModel.onStartPressed(query.trim().ifEmpty { null })
                        }
                    },
                    onStop = { viewModel.onStopPressed() },
                    onEnableService = { openAccessibilitySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServiceState(isAccessibilityServiceEnabled())
        viewModel.refreshLoadedCount()
        viewModel.registerReceiver()
    }

    override fun onPause() {
        super.onPause()
        viewModel.unregisterReceiver()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        for (service in enabled) {
            val info = service.resolveInfo.serviceInfo
            if (info.packageName == packageName &&
                info.name == InstagramBotService::class.java.name
            ) {
                return true
            }
        }
        return false
    }
}
