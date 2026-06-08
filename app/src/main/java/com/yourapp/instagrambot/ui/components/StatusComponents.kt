package com.yourapp.instagrambot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.instagrambot.ui.BotUiState
import com.yourapp.instagrambot.ui.theme.InstagramGradient
import com.yourapp.instagrambot.ui.theme.StatusIdle
import com.yourapp.instagrambot.ui.theme.StatusRunning

@Composable
fun GradientAppBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(InstagramGradient)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Instagram Bot",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.7f else 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
        if (active) {
            Box(
                Modifier
                    .size(14.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.4f))
            )
        }
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun HeroStatusCard(state: BotUiState) {
    val statusColor = if (state.isRunning) StatusRunning else StatusIdle
    val statusText = if (state.isRunning) "Running" else "Idle"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(InstagramGradient)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = statusColor, active = state.isRunning)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "${state.commentsPosted}",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "comments posted this session",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ServiceEnableBanner(onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Accessibility service disabled",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Enable it so the bot can control Instagram.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onEnable) { Text("ENABLE") }
        }
    }
}
