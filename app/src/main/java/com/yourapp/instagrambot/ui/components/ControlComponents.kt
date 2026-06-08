package com.yourapp.instagrambot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourapp.instagrambot.ui.ActivityEntry
import com.yourapp.instagrambot.ui.theme.InstagramGradient
import com.yourapp.instagrambot.ui.theme.StatusStop

@Composable
fun ConfigurationCard(
    query: String,
    onQueryChange: (String) -> Unit,
    commentsLoaded: Int,
    onImportCsv: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search topic (optional)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onImportCsv, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("IMPORT COMMENTS CSV")
            }
            Spacer(Modifier.height(10.dp))
            AssistChip(
                onClick = {},
                label = { Text("$commentsLoaded comments loaded") }
            )
        }
    }
}

@Composable
fun ActionButtons(
    isRunning: Boolean,
    serviceEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val startEnabled = !isRunning && serviceEnabled
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (startEnabled) Modifier.background(InstagramGradient)
                    else Modifier.background(Color(0xFFBDBDBD))
                )
                .clickable(enabled = startEnabled, onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("START", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(
            onClick = onStop,
            enabled = isRunning,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusStop),
            border = BorderStroke(1.dp, StatusStop),
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .padding(start = 6.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("STOP", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LiveActivityLog(entries: List<ActivityEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Live Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text(
                    "No activity yet. Press Start to begin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(entries.size) {
                    listState.animateScrollToItem(entries.size - 1)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    items(entries) { entry ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                entry.timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyFooter() {
    Text(
        "Safety: random delays of 45–90s are applied between comments. Hourly cap 15, daily cap 50.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    )
}
