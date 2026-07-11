# DawaTrail

*da war ich.* — a lightweight Android viewer for your [Dawarich](https://github.com/Freika/dawarich) GPS history: browse your days on a calendar, see each day's track on a map, and export tracks as GPX.

> **Unofficial project.** DawaTrail is an independent, third‑party viewer. It is **not affiliated with, endorsed by, or supported by the Dawarich project**. Dawarich is a separate project by its own authors; DawaTrail only talks to Dawarich's public HTTP API and contains no Dawarich code. The name is a friendly homage — Dawarich itself puns on the German *„da war ich"* (“that's where I was”), and DawaTrail simply draws the trail of where you were.

## Features

- **Connect to your Dawarich server** with URL + API key — stored locally on the device, no account, no login.
- **Calendar per month**, marking every day that has GPS points. The whole month is fetched in a single paginated request (fast, easy on the server).
- **Tap a day** to draw its route on the map, with start/end markers and stats (distance, duration, points).
- **Month view** — overlay every day of the month at once, coloured by date (blue → amber), with month totals.
- **GPX export & share** — export the shown day or the whole month as a valid GPX 1.1 file and share it via the Android share sheet.
- **Keyless maps** — OpenTopoMap (topo/hiking, default), OpenStreetMap, Esri World Imagery (satellite) and CartoDB Dark. No map API key or third‑party account required.
- Responsive layout (mobile bottom sheet / desktop sidebar), edge‑to‑edge dark UI.

## Setup

1. Install the APK (see below, or via [Obtainium](https://github.com/ImranR98/Obtainium)).
2. On first launch, enter your **Dawarich URL** (e.g. `https://dawarich.example.com`) and **API key**.
3. Pick a day in the calendar — done.

Everything is stored in the WebView's local storage on your device only.

## Build

Standard Gradle Android build. Requires a JDK compatible with the bundled Gradle (JDK 17/21 — the Android Studio JBR works well):

```bash
JAVA_HOME=/path/to/jbr ./gradlew assembleDebug
```

Release signing reads from an (un‑checked‑in) `keystore.properties`; if it's missing the release build simply stays unsigned instead of failing.

## How it works

The app is a thin Kotlin `WebView` wrapper (`MainActivity.kt`) around a single self‑contained page, `app/src/main/assets/mpd-trail.html`, which holds all the UI and logic (Leaflet for the map). A small `@JavascriptInterface` bridges GPX sharing to the native Android share sheet.

## License

MIT — see [LICENSE](LICENSE). This applies to DawaTrail's own code only; Dawarich is a separate project under its own license.
