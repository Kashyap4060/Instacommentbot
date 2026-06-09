# Search-driven hashtag commenting — design

## Context

The app has a "Search topic (optional)" field whose value flows all the way to
`InstagramBotService.startBot(query)` and is stored in `currentQuery` — but
`currentQuery` is never read. The bot always navigates to the generic Reels feed
regardless of what is typed. The existing `isSearchScreen()` / `handleSearchFlow()`
are unreachable scaffolding and, as verified on-device, use wrong view-ids.

Goal: when the user types a hashtag and starts the bot, the bot should navigate to
that hashtag's media feed and run the existing like/comment/skip flow there.
Empty query keeps today's behavior (generic Reels feed).

## Verified on-device view-ids (moto g(9), Instagram, 2026-06-09)

| Purpose | resource-id | Notes |
|---|---|---|
| Search/Explore tab | `com.instagram.android:id/search_tab` | content-desc "Search and explore" |
| Search input field | `com.instagram.android:id/action_bar_search_edit_text` | tap to focus, then SET_TEXT |
| Submit search | Enter (KEYCODE_ENTER, 66) | results tabs appear only after submit |
| Results tab chips | `com.instagram.android:id/igds_prism_chip_label` | TextView; pick the one whose text == "Tags" |
| Hashtag result row | `com.instagram.android:id/row_hashtag_container` | clickable; name in `row_hashtag_textview_tag_name` ("#wedding…") |
| Hashtag grid item | `com.instagram.android:id/grid_card_layout_container` | clickable; opens a scrollable media feed |
| Opened media: comment | `com.instagram.android:id/comment_button` | same id the reels flow already uses |
| Opened media: like | `com.instagram.android:id/like_button` | same id the reels flow already uses |

Wrong ids in current code (to remove/replace): `search_edit_text`,
`row_feed_image_view`.

## Approach (chosen: in-app search navigation)

Drive Instagram's UI like a human rather than deep-linking. More reliable and
consistent with the rest of the accessibility-based bot.

### State machine

`startBot(query)`:
- `query` blank → `pendingNavigation = true` (existing Reels path, unchanged).
- `query` set → normalize (strip leading `#`, trim), store, set
  `pendingSearch = true`, launch Instagram.

`processOnce()` gains a `pendingSearch` branch that advances one step per pass
(mirroring how `pendingNavigation` works), re-reading `rootInActiveWindow` each
pass so it tolerates load delays:

1. Tap `search_tab`.
2. Focus `action_bar_search_edit_text`, SET_TEXT the normalized hashtag.
3. Submit (Enter), then tap the `igds_prism_chip_label` whose text is "Tags".
4. Tap the hashtag row — prefer the `row_hashtag_container` whose
   `row_hashtag_textview_tag_name` equals `#<query>`, else the first row.
5. Tap the first `grid_card_layout_container` to open the media feed.
6. Clear `pendingSearch` and set a flag `inHashtagFeed = true`.

Each step is guarded: if its target isn't present yet, return and retry next pass.
A bounded retry counter per step (reuse the unknown-screen pattern) re-launches the
search from step 1 if a step keeps failing, so the bot can't get permanently stuck.

### Commenting in the hashtag feed

Once `inHashtagFeed`, the opened feed exposes `comment_button` / `like_button` —
the same ids the reels flow uses. Reuse the existing per-item flow verbatim: the
10–15s decide window, ~15% like, 1-in-10 comment skip, comment-cycling from the CSV,
comment-sheet close, then scroll to the next item (the existing `scrollToNextReel`
swipe advances the vertical media feed too).

Screen detection: add `isHashtagFeed(root)` = `comment_button` present while
`inHashtagFeed`. The `when` in `processOnce` checks `pendingSearch` first, then the
hashtag feed, then the existing reels/post/search arms.

### Error handling / recovery

- Per-step retry budget during navigation → restart search from step 1.
- If the hashtag feed stops showing `comment_button` (drifted off), fall back to the
  existing unknown-screen recovery (swipe; after N, re-run search).
- Rate limiting, blocks, and cooldown behave exactly as today.

## Out of scope (YAGNI)

- Account search and keyword/top-result search (hashtag only for now).
- Choosing Top vs Recent within the hashtag (use whatever the grid opens into).
- Multiple hashtags / rotating hashtags.

## Testing / verification

On-device, with a throwaway account:
1. Enter a hashtag, start the bot.
2. Logcat (`BotService:D`) should show the navigation steps, then
   `Watching … — deciding` on hashtag posts, real CSV comments, likes/skips.
3. Confirm it advances through multiple hashtag posts autonomously.
4. Empty query → still goes to the generic Reels feed (regression check).
