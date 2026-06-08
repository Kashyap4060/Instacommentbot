package com.yourapp.instagrambot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourapp.instagrambot.ui.components.ActionButtons
import com.yourapp.instagrambot.ui.components.ConfigurationCard
import com.yourapp.instagrambot.ui.components.GradientAppBar
import com.yourapp.instagrambot.ui.components.HeroStatusCard
import com.yourapp.instagrambot.ui.components.LiveActivityLog
import com.yourapp.instagrambot.ui.components.SafetyFooter
import com.yourapp.instagrambot.ui.components.ServiceEnableBanner

@Composable
fun BotScreen(
    state: BotUiState,
    snackbarHostState: SnackbarHostState,
    query: String,
    onQueryChange: (String) -> Unit,
    onImportCsv: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEnableService: () -> Unit
) {
    Scaffold(
        topBar = { GradientAppBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroStatusCard(state)
            if (!state.serviceEnabled) ServiceEnableBanner(onEnableService)
            ConfigurationCard(
                query = query,
                onQueryChange = onQueryChange,
                commentsLoaded = state.commentsLoaded,
                onImportCsv = onImportCsv
            )
            ActionButtons(
                isRunning = state.isRunning,
                serviceEnabled = state.serviceEnabled,
                onStart = onStart,
                onStop = onStop
            )
            LiveActivityLog(state.activityLog)
            SafetyFooter()
        }
    }
}
