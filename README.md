# BrowserX³ for Android

**1.1.0 · build 2** — a native Jetpack Compose browser using Android System WebView. This version replaces the old quick-link dashboard and overflow list with a gesture-driven start page, address capsule and visual workspace.

## নতুন বিল্ড চেনার উপায়

[Releases](https://github.com/mdreanislam01-ai/BrowserX-/releases) থেকে **1.1.0** লেখা APK নিন, পুরোনো `browserx-v1.0.0` নয়। Branch preview-র নাম **BrowserX³ Preview**: আগের অ্যাপ না মুছেই পাশাপাশি চালাতে পারবেন। এটি আলাদা ইনস্টল, তাই আগের অ্যাপের ডেটা এখানে দেখা যাবে না। **⋮ → Settings → About BrowserX³**-তে ভার্সন ও বিল্ড নম্বর দেখা যায়।

1. হোমের মাঝের **X³** বা নিচের ঠিকানার ঘরে চাপ দিয়ে ওয়েবসাইট বা সার্চ লিখুন। `>` লিখলে ব্রাউজারের নিজস্ব কমান্ড দেখা যায়।
2. ট্যাবের সংখ্যা চাপুন, বা নিচের বার থেকে উপরে সোয়াইপ করুন। **⋮ → Coverflow / Grid / List** দিয়ে ভিউ বদলান। বারে ডানে-বামে সোয়াইপ করলে ট্যাব বদলায়।
3. **⋮ → Settings → Tabs & gestures** থেকে বারের অবস্থান, দুই পাশের বাটন, মেনুর ধরন ও হ্যাপটিক বদলান।
4. কোনো লেখার পাতায় **⋮ → Reader Studio → Save offline** চাপুন। পরে **⋮ → Offline reading** থেকে নেট ছাড়াই পড়ুন।

## Interaction map

| Surface | Working behavior |
| --- | --- |
| Start & address | Centered BrowserX³ search button, editable four-column favorites shelf, regular/private treatments. One editor for URLs, searches, local tab/library matches and `>` commands. No queries sent for autocomplete. |
| Address capsule | Tab count / address / new tab / More by default. Choose the leading and trailing controls in Settings. Hold the count or `+` for pinned tabs. Hold a web address for Reader Studio. Wide windows show a scrollable tab strip. |
| Menu | Bottom sheet or floating menu with library shortcuts, grouped actions and back / forward / reload or stop / favorite / pin. Page-only actions depend on the active tab. |
| Overview | Centered, paged Coverflow with swipe-up dismissal, compact Grid and preview-free List. Private filter, profile picker, new tab, pin/unpin and confirmed, mode-scoped close-all. Regular previews come from the actual WebView and are memory-only. |
| Workspaces | Per-profile tabs, active selection, pinned state, desktop mode, page trails and history. Optional restoration of regular URLs after restart (not the WebView's native back stack). Existing pre-1.1 history belongs to Personal. Favorites, settings and the downloads list are global. |
| Link Peek & trails | Hold an ordinary text link for a temporary live preview: dismiss, copy, share or promote its current URL. Each tab has a branching trail: pan, zoom, revisit a node or fork into a new tab. Retains the newest 64 stops. |
| Reader Studio | Extract local article text, adjust paper color and type size, read aloud via Android TTS, or explicitly save up to 40 articles per profile for offline reading. Private articles cannot be saved. |
| Page tools | Find with match counts, desktop/mobile view, share, print/save PDF, Android downloads, searchable Favorites and History. Privacy X-Ray shows real per-page blocked requests and recent domains. |
| Settings | Searchable sections for search provider, suggestions, tabs and gestures, startup, appearance, privacy and installed version. The protection and Toppings switches control the WebView pipeline, not decorative toggles. |

### Storage & engine boundaries

- `MULTI_PROFILE`-capable WebViews separate website storage between regular profiles. Older providers still separate the app's tab/history lists but **share site logins**; Profiles explains this.
- Private tabs use a fresh temporary WebView storage profile. They are not written to the session, library, usage totals or history, and never get page thumbnails. Closing the last private tab or leaving the profile retires that store; abandoned temporary stores are cleaned at startup. This is not a claim that the WebView never touches disk. A provider without storage isolation must be updated to use private tabs. A download explicitly started in private can still leave a file in Android Downloads.
- Built-in Toppings are a local tracker-host filter, a CSS cookie-banner hider and WebView darkening. This is **not** a GeckoView or Firefox extension build. Cross-device sync, arbitrary Topping installation, snoozing and Site Capsules are not implemented. BrowserX³ does not present nonworking buttons for these features.

## Build and verify

JDK 17, Gradle 8.7, Android SDK platform 35 and build-tools 35.0.0:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
# Side-by-side preview; same features, separate package/launcher label:
gradle -PbrowserxPreview :app:assembleDebug
# With an Android device/emulator attached:
gradle :app:connectedDebugAndroidTest
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. [Actions](https://github.com/mdreanislam01-ai/BrowserX-/actions/workflows/android-apk.yml) uploads a commit-identified APK, runs the instrumented UI flow on Android, and collects device screenshots. Only a passing `main` build publishes the stable version. A branch build marked `[preview-apk]` (or dispatched with `publish_preview`) publishes a separately identified prerelease after those checks. These are debug-signed APKs, not production-signed updates.

## Design reference

The public screens and interaction descriptions of [Candy Browser](https://github.com/sk2andy/candy-browser) are a product reference. BrowserX³ keeps its own package, branding and implementation. No upstream Candy source, logo or screenshots are bundled.
