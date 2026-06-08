package com.yourapp.instagrambot.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Instagram brand gradient stops
val IgPurple = Color(0xFF833AB4)
val IgPink = Color(0xFFE1306C)
val IgOrange = Color(0xFFF77737)
val IgYellow = Color(0xFFFCAF45)

val InstagramGradient = Brush.linearGradient(
    colors = listOf(IgPurple, IgPink, IgOrange, IgYellow)
)

// Status indicator colors
val StatusIdle = Color(0xFF9E9E9E)
val StatusRunning = Color(0xFF4CAF50)
val StatusStop = Color(0xFFF44336)
