# Instagram Bot — UI/UX Rebuild (Jetpack Compose)

**Date:** 2026-06-08
**Scope:** Presentation layer only. The automation engine is not modified.

## Goal

Rebuild the Instagram Bot Android app's user interface from scratch using
Jetpack Compose with an Instagram-inspired gradient visual direction, replacing
the existing XML/AppCompat single-screen UI. The bot automation behavior is
unchanged — this is a pure UI/UX upgrade.

## Decisions (from brainstorming)

- **Scope:** UI/UX only. Keep the bot engine, `CommentManager`, and
  `RateLimiter` logic working as-is.
- **UI technology:** Jetpack Compose.
- **Visual direction:** Instagram-inspired — purple → pink → orange → yellow
  brand gradient, rounded cards, modern social-app feel. Light + dark support.

## Architecture

The existing automation pieces stay intact and keep communicating exactly as
they do today:

- `InstagramBotService` (AccessibilityService) continues to broadcast status via
  `LocalBroadcastManager` on the `"BOT_STATUS"` intent action, carrying
  `status: String` and `commentsPosted: Int` extras. **Not modified.**
- `CommentManager`, `RateLimiter`, `RateLimitConfig` — **not modified.**
- `accessibility_service_config.xml` — **not modified.**

A thin presentation layer is added on top:

```
MainActivity (Compose host: setContent { })
   └─ BotScreen (stateless-ish, reads BotUiState)
        ├─ GradientAppBar
        ├─ HeroStatusCard
        ├─ ServiceEnableBanner   (visible only when service disabled)
        ├─ ConfigurationCard
        ├─ ActionButtons (Start / Stop)
        ├─ LiveActivityLog
        └─ SafetyFooter
   └─ MainViewModel (AndroidViewModel)
        ├─ registers/unregisters the "BOT_STATUS" BroadcastReceiver
        ├─ holds BotUiState
        ├─ reads CommentManager.getCommentCount()
        └─ checks Accessibility service enabled state
```

### State

```kotlin
data class BotUiState(
    val serviceEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val statusMessage: String = "Bot is idle.",
    val activityLog: List<ActivityEntry> = emptyList(),
    val commentsPosted: Int = 0,
    val commentsLoaded: Int = 0,
)

data class ActivityEntry(val timestamp: String, val message: String)
```

- `serviceEnabled` — recomputed in `onResume` via `AccessibilityManager`
  (same check the current `MainActivity` does).
- `isRunning` — toggled by the UI when Start/Stop is pressed. (The service does
  not broadcast a running flag today; deriving it from user action keeps the
  engine untouched.)
- `statusMessage` / `commentsPosted` — updated from each `"BOT_STATUS"`
  broadcast.
- `activityLog` — each incoming status is appended with a timestamp; capped to a
  reasonable size (e.g. last 100 entries) to bound memory.
- `commentsLoaded` — `CommentManager.getCommentCount()` after import / on
  resume.

### Data flow

1. User taps **Import CSV** → `MainViewModel` invokes
   `CommentManager.importCsv(uri)` → updates `commentsLoaded`.
2. User taps **Start** → checks service enabled; if enabled,
   `InstagramBotService.getInstance()?.startBot(query)`, set `isRunning = true`.
   If disabled, show the enable banner / route to Accessibility settings.
3. Service runs → broadcasts `"BOT_STATUS"` → receiver appends to log and updates
   counts.
4. User taps **Stop** → `InstagramBotService.getInstance()?.stopBot()`,
   `isRunning = false`.

## Visual / Theme spec

- `ui/theme/Color.kt` — brand gradient stops
  (`#833AB4`, `#E1306C`, `#F77737`, `#FCAF45`), plus light and dark
  `ColorScheme` palettes. A shared `instagramGradient: Brush`.
- `ui/theme/Theme.kt` — `InstagramBotTheme` wrapping Material 3
  `MaterialTheme` with light/dark schemes following the system setting.
- `ui/theme/Type.kt` — Material 3 typography scale.
- Rounded corners (cards ~16dp), gentle elevation, generous padding.

## Components (`ui/`)

- **GradientAppBar** — title "Instagram Bot" with the brand gradient and a small
  logo glyph.
- **HeroStatusCard** — gradient-filled card; animated pulsing status dot whose
  color reflects Idle (grey) / Running (green) / Cooldown (amber); large
  "Comments Posted" count.
- **ServiceEnableBanner** — only composed when `!serviceEnabled`; prominent CTA
  button opening `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- **ConfigurationCard** — outlined search-topic `TextField`, "Import Comments
  CSV" button, an assist chip reading "N comments loaded".
- **ActionButtons** — gradient-filled **Start** (disabled while running or when
  service off), outlined red **Stop** (disabled while idle).
- **LiveActivityLog** — scrolling, timestamped list of status entries (the key
  UX improvement over the single overwritten status line). Auto-scrolls to the
  newest entry.
- **SafetyFooter** — the rate-limit info note.

## Files

**Add**
- `app/src/main/java/com/yourapp/instagrambot/ui/theme/Color.kt`
- `app/src/main/java/com/yourapp/instagrambot/ui/theme/Theme.kt`
- `app/src/main/java/com/yourapp/instagrambot/ui/theme/Type.kt`
- `app/src/main/java/com/yourapp/instagrambot/ui/BotScreen.kt`
- `app/src/main/java/com/yourapp/instagrambot/ui/components/*.kt` (one file per
  component, or grouped logically)
- `app/src/main/java/com/yourapp/instagrambot/MainViewModel.kt`

**Rewrite**
- `MainActivity.kt` → Compose host using `setContent { InstagramBotTheme { BotScreen(...) } }`
- `app/build.gradle` → enable Compose (`buildFeatures { compose true }`,
  `composeOptions`), add Compose BOM + Material 3 + activity-compose +
  lifecycle-viewmodel-compose; bump Kotlin to 1.9.x and `compileSdk`/`targetSdk`
  to 34 for a current Compose compiler.
- `app/src/main/res/values/themes.xml` → a no-action-bar base theme compatible
  with a Compose host (the visual theme now lives in Compose).
- `AndroidManifest.xml` → point the activity at the base theme if needed.
- `app/src/main/res/values/strings.xml` → updated copy.

**Delete**
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/drawable/status_indicator_active.xml`
- `app/src/main/res/drawable/status_indicator_inactive.xml`

**Untouched (engine)**
- `InstagramBotService.kt`
- `CommentManager.kt`
- `RateLimiter.kt`
- `RateLimitConfig.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`

## Out of scope (YAGNI)

- Editable rate-limit settings — would require wiring `RateLimitConfig` into
  `RateLimiter` (engine change). Rate limits are shown as read-only info in the
  safety footer instead.
- Multi-screen navigation — the app remains a single screen.
- Any change to the bot's automation, detection, or rate-limiting behavior.

## Error handling

- CSV import failure → user-visible message (snackbar) "Failed to import CSV".
- Start pressed with service disabled → show the enable banner and route to
  Accessibility settings, do not start.
- `InstagramBotService.getInstance()` null (service not connected) → status
  message indicates the service isn't ready / instruct enabling it.

## Testing / verification

Since this is an Android UI rebuild on a Windows dev machine, verification is:

1. **Build** — `./gradlew assembleDebug` succeeds with the new Compose deps.
2. **Manual smoke** (on device/emulator if available): app launches to the new
   screen; service-enable banner shows when disabled and hides when enabled;
   CSV import updates the loaded-count chip; Start/Stop toggle the hero status
   and enabled states; live activity log appends timestamped broadcast messages
   and auto-scrolls.
3. Compose previews for each component for visual confirmation without a device.
