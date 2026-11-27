# BrowserX³

The user's own Android browser app — native Jetpack Compose + Android System WebView.

**v1.1.0** — System WebView engine only.

## Features
- Tabs: open, switch, close, pin, tab overview (cover-flow / grid / list)
- Candy-style toolbar on top: the search pill grows into a full-width editor and slides
  away while you scroll down (Bottom layout still available in Settings)
- Address bar with Google search (default) — DuckDuckGo and Bing in Settings
- Bookmarks and history (start empty), downloads with a downloads list
- Simple ad-block (built-in ad/tracker host list), cookie-banner hider, HTTPS-Only mode
- Private tabs (no history saved), Profiles, Toppings, Privacy X-Ray
- Reader Studio with read-aloud, Find in page, Desktop site, Print / Save PDF
- Dark mode: system / light / dark, Material You dynamic colour
- Home page with search + quick links
- Back button: previous page → home page → exit app
- In-app updates: Settings → About & Updates (or the 3-dot menu) checks GitHub, downloads
  the new APK and opens the system installer — no repeat downloads by hand

## In-app updates
The app reads the newest published GitHub release and compares its `versionCode` (written into
the release notes by the CI workflow) with the installed build. When a newer build exists it
downloads the APK into app storage and hands it to Android's package installer.

Notes:
- Android always shows the system installer confirmation once — a browser cannot install
  silently without device-owner privileges.
- Updates only install over an existing copy when both APKs share the same signing key.
  That is why every CI build is signed with the bundled `android/app/browserx-signing.p12`
  debug key instead of a throw-away key. To use your own key, add a git-ignored
  `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and rebuild —
  but then the first install needs one manual uninstall.

## Build
GitHub Actions builds the debug APK on every push and publishes it as the
`browserx-latest` release. Download the newest build:

https://github.com/mdreanislam01-ai/BrowserX-/releases/latest/download/app-debug.apk

## License note
BrowserX³ is written from scratch for this project. Candy Browser
(sk2andy/candy-browser) was used only as a feature reference — its code is
not copied (PolyForm Noncommercial 1.0.0 license).
