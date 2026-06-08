# Instagram Bot UI/UX Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Instagram Bot Android app's XML/AppCompat UI with a Jetpack Compose, Instagram-gradient interface, without changing any bot automation logic.

**Architecture:** A thin Compose presentation layer (`MainActivity` host + `MainViewModel` + stateless composables) sits on top of the unchanged engine (`InstagramBotService`, `CommentManager`, `RateLimiter`). The ViewModel listens to the existing `LocalBroadcastManager` `"BOT_STATUS"` broadcasts and exposes a `StateFlow<BotUiState>`.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00) with Material 3, AGP 8.1.4, Gradle 8.5, compileSdk 34.

---

## Engine files — DO NOT MODIFY

These keep working exactly as-is. No task touches them:
- `app/src/main/java/com/yourapp/instagrambot/InstagramBotService.kt`
- `app/src/main/java/com/yourapp/instagrambot/CommentManager.kt`
- `app/src/main/java/com/yourapp/instagrambot/RateLimiter.kt`
- `app/src/main/java/com/yourapp/instagrambot/RateLimitConfig.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`

## Build note

There is no `gradlew` script in the repo, but `gradle/wrapper/gradle-wrapper.properties` is pinned to Gradle 8.5. **Build/verify by opening the project in Android Studio and running `Build > Make Project` (Ctrl+F9), or `Build > Build Bundle(s)/APK(s) > Build APK(s)`.** Where a step says "Run: `./gradlew …`", use the equivalent Android Studio action if no `gradlew` is on PATH. The JVM unit test in Task 3 can be run from Android Studio by clicking the gutter run arrow next to the test, or via `Run 'ActivityLogTest'`.

---

## File Structure

**Create:**
- `app/src/main/java/com/yourapp/instagrambot/ui/theme/Color.kt` — brand colors + gradient brush
- `app/src/main/java/com/yourapp/instagrambot/ui/theme/Type.kt` — typography
- `app/src/main/java/com/yourapp/instagrambot/ui/theme/Theme.kt` — `InstagramBotTheme`
- `app/src/main/java/com/yourapp/instagrambot/ui/BotUiState.kt` — UI state + `ActivityLog` helper
- `app/src/main/java/com/yourapp/instagrambot/MainViewModel.kt` — state holder + broadcast bridge
- `app/src/main/java/com/yourapp/instagrambot/ui/components/StatusComponents.kt` — app bar, status card, banner
- `app/src/main/java/com/yourapp/instagrambot/ui/components/ControlComponents.kt` — config, actions, log, footer
- `app/src/main/java/com/yourapp/instagrambot/ui/BotScreen.kt` — screen assembly
- `app/src/test/java/com/yourapp/instagrambot/ActivityLogTest.kt` — unit test

**Modify:**
- `build.gradle` (root) — Kotlin + AGP versions
- `app/build.gradle` — Compose + dependencies + SDK bump
- `app/src/main/java/com/yourapp/instagrambot/MainActivity.kt` — Compose host (full rewrite)
- `app/src/main/res/values/themes.xml` — no-action-bar base theme
- `app/src/main/res/values/strings.xml` — copy

**Delete:**
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/drawable/status_indicator_active.xml`
- `app/src/main/res/drawable/status_indicator_inactive.xml`

---

## Task 0: Initialize git (for frequent commits)

**Files:** none (repo root)

- [ ] **Step 1: Init repo and make a baseline commit**

This folder is not a git repo yet; the plan commits after each task. Initialize and snapshot the current state first.

```bash
git init
printf "build/\n.gradle/\n.idea/\nlocal.properties\n*.iml\n" > .gitignore
git add -A
git commit -m "chore: baseline before Compose UI rebuild"
```

Expected: a baseline commit is created. (If git is unavailable, skip the `git commit` steps throughout — they are not required for the app to build.)

---

## Task 1: Build configuration for Compose

**Files:**
- Modify: `build.gradle` (root)
- Modify: `app/build.gradle`

- [ ] **Step 1: Update root `build.gradle` versions**

Replace the entire contents of `build.gradle` (root) with:

```groovy
// Top-level build file
buildscript {
    ext.kotlin_version = '1.9.22'
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.4'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

- [ ] **Step 2: Update `app/build.gradle`**

Replace the entire contents of `app/build.gradle` with:

```groovy
plugins {
    id 'com.android.application'
    id 'kotlin-android'
}

android {
    namespace 'com.yourapp.instagrambot'
    compileSdk 34

    defaultConfig {
        applicationId "com.yourapp.instagrambot"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.8'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.1.0'

    // Compose
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.material:material-icons-core'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

    debugImplementation 'androidx.compose.ui:ui-tooling'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 3: Sync Gradle**

In Android Studio: `File > Sync Project with Gradle Files`.
Expected: sync succeeds and downloads the Compose dependencies. (The app will not fully compile yet because `MainActivity` still references the old XML — that is fixed in Task 8. To get a clean sync without compiling, this step only needs the dependency graph to resolve.)

- [ ] **Step 4: Commit**

```bash
git add build.gradle app/build.gradle
git commit -m "build: enable Jetpack Compose, bump Kotlin 1.9.22 / AGP 8.1.4 / sdk 34"
```

---

## Task 2: Theme

**Files:**
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/theme/Color.kt`
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/theme/Type.kt`
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/theme/Theme.kt`

- [ ] **Step 1: Create `Color.kt`**

```kotlin
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
```

- [ ] **Step 2: Create `Type.kt`**

```kotlin
package com.yourapp.instagrambot.ui.theme

import androidx.compose.material3.Typography

val AppTypography = Typography()
```

- [ ] **Step 3: Create `Theme.kt`**

```kotlin
package com.yourapp.instagrambot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = IgPink,
    secondary = IgPurple,
    tertiary = IgOrange
)

private val DarkColors = darkColorScheme(
    primary = IgPink,
    secondary = IgPurple,
    tertiary = IgOrange
)

@Composable
fun InstagramBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
```

- [ ] **Step 4: Verify it compiles**

In Android Studio: `Build > Make Project` (Ctrl+F9).
Expected: no errors from the `ui/theme` package. (`MainActivity` errors are expected until Task 8.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yourapp/instagrambot/ui/theme/
git commit -m "feat: add Compose Instagram-gradient theme"
```

---

## Task 3: UI state model and activity-log helper (TDD)

**Files:**
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/BotUiState.kt`
- Test: `app/src/test/java/com/yourapp/instagrambot/ActivityLogTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/yourapp/instagrambot/ActivityLogTest.kt`:

```kotlin
package com.yourapp.instagrambot

import com.yourapp.instagrambot.ui.ActivityEntry
import com.yourapp.instagrambot.ui.ActivityLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTest {

    @Test
    fun append_addsEntryToEnd() {
        val start = listOf(ActivityEntry("00:00:01", "first"))
        val result = ActivityLog.append(start, ActivityEntry("00:00:02", "second"))
        assertEquals(2, result.size)
        assertEquals("second", result.last().message)
    }

    @Test
    fun append_capsAtMaxEntries_keepingNewest() {
        var log = emptyList<ActivityEntry>()
        for (i in 1..ActivityLog.MAX_ENTRIES + 5) {
            log = ActivityLog.append(log, ActivityEntry("t", "msg$i"))
        }
        assertEquals(ActivityLog.MAX_ENTRIES, log.size)
        // Oldest 5 dropped; newest retained
        assertEquals("msg${ActivityLog.MAX_ENTRIES + 5}", log.last().message)
        assertEquals("msg6", log.first().message)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

In Android Studio: click the run gutter arrow next to `ActivityLogTest`, or `Run 'ActivityLogTest'`.
Expected: FAILS to compile — `ActivityEntry` / `ActivityLog` are unresolved.

- [ ] **Step 3: Create `BotUiState.kt` with the minimal implementation**

```kotlin
package com.yourapp.instagrambot.ui

/** A single timestamped line in the live activity log. */
data class ActivityEntry(
    val timestamp: String,
    val message: String
)

/** Pure helper for managing the bounded activity log. */
object ActivityLog {
    const val MAX_ENTRIES = 100

    fun append(current: List<ActivityEntry>, entry: ActivityEntry): List<ActivityEntry> {
        val updated = current + entry
        return if (updated.size > MAX_ENTRIES) updated.takeLast(MAX_ENTRIES) else updated
    }
}

/** Immutable snapshot of everything the UI renders. */
data class BotUiState(
    val serviceEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val statusMessage: String = "Bot is idle.",
    val activityLog: List<ActivityEntry> = emptyList(),
    val commentsPosted: Int = 0,
    val commentsLoaded: Int = 0
)
```

- [ ] **Step 4: Run the test to verify it passes**

In Android Studio: `Run 'ActivityLogTest'`.
Expected: both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yourapp/instagrambot/ui/BotUiState.kt app/src/test/java/com/yourapp/instagrambot/ActivityLogTest.kt
git commit -m "feat: add BotUiState and bounded ActivityLog helper with tests"
```

---

## Task 4: MainViewModel (broadcast bridge)

**Files:**
- Create: `app/src/main/java/com/yourapp/instagrambot/MainViewModel.kt`

- [ ] **Step 1: Create `MainViewModel.kt`**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

In Android Studio: `Build > Make Project`.
Expected: `MainViewModel` compiles (it references the existing `CommentManager` and `InstagramBotService`, which are unchanged). `MainActivity` errors are still expected until Task 8.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yourapp/instagrambot/MainViewModel.kt
git commit -m "feat: add MainViewModel bridging BOT_STATUS broadcasts to Compose state"
```

---

## Task 5: Status components

**Files:**
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/components/StatusComponents.kt`

- [ ] **Step 1: Create `StatusComponents.kt`**

```kotlin
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
```

Note: `Modifier.weight(1f)` inside `ServiceEnableBanner` resolves via `RowScope` (the `Row` content lambda) — no extra import is required beyond the layout imports already listed.

- [ ] **Step 2: Verify it compiles**

In Android Studio: `Build > Make Project`.
Expected: `StatusComponents.kt` compiles with no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yourapp/instagrambot/ui/components/StatusComponents.kt
git commit -m "feat: add gradient app bar, hero status card, pulsing dot, service banner"
```

---

## Task 6: Control components

**Files:**
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/components/ControlComponents.kt`

- [ ] **Step 1: Create `ControlComponents.kt`**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

In Android Studio: `Build > Make Project`.
Expected: `ControlComponents.kt` compiles with no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yourapp/instagrambot/ui/components/ControlComponents.kt
git commit -m "feat: add configuration card, action buttons, live activity log, footer"
```

---

## Task 7: BotScreen assembly

**Files:**
- Create: `app/src/main/java/com/yourapp/instagrambot/ui/BotScreen.kt`

- [ ] **Step 1: Create `BotScreen.kt`**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

In Android Studio: `Build > Make Project`.
Expected: `BotScreen.kt` compiles with no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yourapp/instagrambot/ui/BotScreen.kt
git commit -m "feat: assemble BotScreen from components"
```

---

## Task 8: Wire up MainActivity + resources, remove old XML

**Files:**
- Modify (full rewrite): `app/src/main/java/com/yourapp/instagrambot/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Delete: `app/src/main/res/layout/activity_main.xml`
- Delete: `app/src/main/res/drawable/status_indicator_active.xml`
- Delete: `app/src/main/res/drawable/status_indicator_inactive.xml`

- [ ] **Step 1: Replace `MainActivity.kt` entirely**

```kotlin
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
```

- [ ] **Step 2: Replace `themes.xml`**

Replace the entire contents of `app/src/main/res/values/themes.xml` with:

```xml
<resources>
    <style name="Theme.InstagramBot" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="android:statusBarColor">#833AB4</item>
        <item name="android:windowBackground">@android:color/white</item>
    </style>
</resources>
```

- [ ] **Step 3: Update `strings.xml`**

Replace the entire contents of `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Instagram Bot</string>
    <string name="service_desc">Auto comments on Instagram reels and search results with rate limiting</string>
</resources>
```

- [ ] **Step 4: Delete the obsolete resource files**

```bash
rm app/src/main/res/layout/activity_main.xml
rm app/src/main/res/drawable/status_indicator_active.xml
rm app/src/main/res/drawable/status_indicator_inactive.xml
```

(On Windows PowerShell: `Remove-Item app\src\main\res\layout\activity_main.xml, app\src\main\res\drawable\status_indicator_active.xml, app\src\main\res\drawable\status_indicator_inactive.xml`)

- [ ] **Step 5: Build the whole app**

In Android Studio: `Build > Make Project` (Ctrl+F9).
Expected: BUILD SUCCESSFUL with no unresolved references. The old `R.layout.activity_main` / `R.id.*` references are gone because `MainActivity` no longer uses them.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: replace XML UI with Compose BotScreen host in MainActivity"
```

---

## Task 9: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the unit tests**

In Android Studio: right-click `app/src/test/java/com/yourapp/instagrambot/ActivityLogTest.kt` > `Run 'ActivityLogTest'`.
Expected: all tests PASS.

- [ ] **Step 2: Build a debug APK**

In Android Studio: `Build > Build Bundle(s)/APK(s) > Build APK(s)`.
Expected: BUILD SUCCESSFUL; APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Manual smoke test (device or emulator, API 26+)**

Install and verify each item:
- App launches into the new Compose screen with the gradient app bar and gradient hero card.
- When the accessibility service is **off**, the red "Accessibility service disabled" banner is visible. Tapping **ENABLE** opens system Accessibility settings.
- After enabling the service and returning, the banner disappears (verify on `onResume`).
- Tapping **IMPORT COMMENTS CSV**, picking a text/CSV file, shows a snackbar ("Comments imported successfully!" or "Failed to import CSV") and updates the "N comments loaded" chip.
- With the service enabled, **START** is tappable (gradient); **STOP** is disabled until running. Pressing START sets the hero status dot to green/"Running" and disables START.
- As the service broadcasts status, the **Live Activity** log appends timestamped lines and auto-scrolls to the newest.
- Pressing **STOP** sets status back to "Idle" and re-enables START.
- Toggle device dark mode: the screen renders correctly in both light and dark.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: Instagram Bot Compose UI rebuild complete"
```

---

## Self-Review Notes (for the implementer)

- **Spec coverage:** theme (Task 2), state + bounded log (Task 3), broadcast bridge / ViewModel (Task 4), all 7 screen components incl. live log and service banner (Tasks 5–7), MainActivity host + snackbar error handling + delete old XML (Task 8), verification incl. dark mode (Task 9). Out-of-scope items (editable rate limits, multi-screen) are intentionally not implemented; rate limits surface as the read-only `SafetyFooter`.
- **Engine untouched:** No task edits `InstagramBotService`, `CommentManager`, `RateLimiter`, `RateLimitConfig`, or the accessibility config.
- **Type consistency:** `BotUiState`, `ActivityEntry`, and `ActivityLog.append/MAX_ENTRIES` are defined in Task 3 and used identically in Tasks 4, 6, 7. `MainViewModel.events`/`uiState`/`importCsv`/`onStartPressed`/`onStopPressed`/`refreshServiceState`/`refreshLoadedCount` defined in Task 4 are used as-is in Task 8.
