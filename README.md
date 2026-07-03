# SportsLive HQ — Native Android (Java)

A fully dynamic, single-JSON-driven live sports streaming app built with
AndroidX Media3 ExoPlayer, Retrofit + Gson, and Material 3.

## What's inside

```
SportsLiveHQ/
├── .github/workflows/android.yml   # CI: builds a debug APK on every push to main
├── app/
│   ├── build.gradle                 # Media3 ExoPlayer, Gson, Retrofit, Glide deps
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/sportslive/hq/app/
│       │   ├── MainActivity.java          # Toolbar, categories, event list, info sheet
│       │   ├── PlayerActivity.java         # ExoPlayer, ClearKey DRM, multi-server, countdown
│       │   ├── models/                     # AppData, AppProfile, Category, Event, Team,
│       │   │                               #   StreamingSource, DrmKeys, SocialLinks
│       │   ├── network/                    # ApiClient (Retrofit) + ApiService
│       │   ├── adapters/                   # CategoryAdapter, EventAdapter
│       │   └── utils/                      # Constants, DateUtils (countdown math)
│       └── res/
│           ├── layout/          # activity_main, activity_player, item_event_card, etc.
│           ├── drawable/        # capsule chip selector, LIVE/COMING SOON badges, icons
│           ├── color/           # capsule_text_selector.xml
│           └── values/          # colors, strings, themes (dark, purple/cyan accents)
├── sample_app_data.json         # The exact reference JSON this app is built against
├── build.gradle / settings.gradle / gradle.properties
└── .gitignore
```

## How it works

1. **`Constants.CONFIG_JSON_URL`** (in `utils/Constants.java`) points at your hosted
   JSON config file — swap it for your real endpoint. `MainActivity` fetches it via
   Retrofit and parses it into `AppData` with Gson.
2. **Top bar**: logo + app name pinned left, an "i" `ImageButton` pinned right. Tapping
   it opens a `BottomSheetDialog` (`bottomsheet_info.xml`) populated from `app_profile`,
   with Facebook/Telegram/YouTube buttons that fire `ACTION_VIEW` intents.
3. **Categories**: a horizontal `RecyclerView` of capsule chips
   (`CategoryAdapter`) filters the vertical event `RecyclerView`
   (`EventAdapter`) by `category_id`.
4. **Match cards**: VS layout with both team logos/names, a red `LIVE` badge or a
   blue/gray `COMING SOON` badge.
5. **Tapping a LIVE card** → `PlayerActivity` builds a Media3 `MediaItem`, picks the
   MIME type from `type` (`hls`/`mpd`), and — if `drm_protected` is `true` — builds a
   ClearKey `DrmConfiguration` on the fly from `key_id`/`key` (hex → base64url JWK Set
   embedded as a `data:` URI, so no external license server is required).
6. **Multi-server switching**: one button per entry in `streaming_sources`
   (built dynamically in `PlayerActivity.buildServerButtons()`). Tapping a button calls
   `exoPlayer.stop()` + `clearMediaItems()` then re-prepares with the new source —
   no activity restart.
7. **Tapping a COMING SOON card** → same `PlayerActivity`, but it loops
   `demo_intro_url` (`REPEAT_MODE_ONE`) and shows a live countdown
   (`DateUtils.parseIsoToMillis` / `formatCountdown`) computed from `start_time`,
   ticking every second via a `Handler`.

## Player experience

- **Full screen**: system status/nav bars are hidden (`WindowInsetsControllerCompat`);
  swipe from an edge to reveal them temporarily.
- **Controls**: Media3's native styled controller overlays the video, auto-hides after
  5 seconds while playing, and reappears on tap. A custom top bar (back button, LIVE/AD
  badge, title, servers icon) is synced to show/hide with it via
  `PlayerView.ControllerVisibilityListener`.
- **Sources**: `type` in `streaming_sources` supports `"hls"`, `"mpd"`, and now `"ts"`
  (or any other value) — `"ts"`/unknown types are routed through `ProgressiveMediaSource`,
  which covers raw MPEG-TS, continuous mpegts, mp4, and similar direct/progressive
  streams (common on MAG-style IPTV panels).
- **Server switching**: tap the servers icon (top-right) for a bottom sheet listing all
  `streaming_sources`, with a checkmark on the active one.
- **Errors**: any playback failure (network, DRM, bad/unsupported format) shows a
  full-screen error panel with a plain-language message plus the raw ExoPlayer error
  code, and **Retry** / **Change Server** buttons — nothing fails silently.
- **Ads**: an optional top-level `"ads"` block in the JSON plays a skippable pre-roll
  before the event loads:
  ```json
  "ads": {
    "enabled": true,
    "video_url": "https://yourcdn.com/ads/preroll.mp4",
    "skip_after_seconds": 5,
    "click_url": "https://sponsor-site.com"
  }
  ```
  Set `"enabled": false` (or omit `"ads"` entirely) to disable ads app-wide. The skip
  button stays disabled/counting down for `skip_after_seconds`, then becomes tappable;
  the ad also auto-advances to content when it finishes naturally.



Open the project root in Android Studio (Jellyfish+) and let it sync — no
placeholders, everything compiles as-is against `compileSdk 34` / `minSdk 23`.

### CI (GitHub Actions)

`.github/workflows/android.yml` runs on every push/PR to `main`:
sets up JDK 17, provisions Gradle via `gradle/actions/setup-gradle`, runs
`gradle assembleDebug`, and uploads `app-debug.apk` as a build artifact —
no committed Gradle wrapper jar required.

## Notes / things to swap for production

- `Constants.CONFIG_JSON_URL` — point at your real JSON endpoint. `sample_app_data.json`
  now uses **real, publicly reachable test streams** (a genuine ClearKey-encrypted DASH
  asset from Shaka Player's own demo assets, plus a public HLS test stream) so you can
  confirm the app plays video correctly before swapping in your own panel's URLs. To
  test it as-is, host `sample_app_data.json` somewhere reachable (e.g. push it to a
  public GitHub repo and use the `raw.githubusercontent.com` link) and point
  `CONFIG_JSON_URL` at that.
- If you see `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`, it means the device could not
  open a connection to that exact host at all — check the URL resolves/loads in a
  browser first, check the emulator/device has internet, and check your server isn't
  blocking the app's User-Agent or IP.
- `network_security_config.xml` now allows cleartext (HTTP) traffic globally, since many
  self-hosted IPTV/stream panels don't run HTTPS. Tighten this with `<domain-config>`
  entries for production if you want HTTPS-only elsewhere.
- Launcher icon is a minimal placeholder vector — replace via Android
  Studio's Image Asset tool for production branding.
