# BrowserX³

The user's own Android browser app — native Jetpack Compose + Android System WebView.

**v1.0.0 (Build 1)** — System WebView engine only.

## Features (v1)
- Tabs: open, switch, close, tab overview
- Address bar with Google search (default) — DuckDuckGo and Bing in Settings
- Toolbar on top by default: search pill, tab counter and 3-dot menu
  (Bottom layout still available in Settings → Address Bar Position)
- Bookmarks and history (start empty)
- Downloads via Android DownloadManager, with a downloads list
- Simple ad-block (built-in ad/tracker host list)
- Private tabs (no history saved)
- Dark mode: system / light / dark
- Home page with search + quick links
- Back / forward / refresh / share
- Swipe left/right on the address bar to switch tabs

## v2 (planned)
GeckoView engine option + Firefox extension install (XPI), engine switching with checkpoint/restart.

## Build
GitHub Actions builds the debug APK on every push and publishes it to the
`browserx-v1.0.0` release. Download:

https://github.com/mdreanislam01-ai/BrowserX-/releases/download/browserx-v1.0.0/app-debug.apk

## License note
BrowserX³ is written from scratch for this project. Candy Browser
(sk2andy/candy-browser) was used only as a feature reference — its code is
not copied (PolyForm Noncommercial 1.0.0 license).
