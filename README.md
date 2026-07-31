# Neveen

A simple Android app (**Neveen**) to track monthly **inflows** and **outflows** for the House
Payment Plan (Jul 2026 → Jan 2027). Every value is shown in **SAR** with its
**USD equivalent** (1 USD = 3.75 SAR). The savings target of **90,000 SAR** is
labelled **"Neveen Money"**.

## Features
- 7 months preloaded with the current plan values (fully editable).
- Add / edit / delete any inflow or outflow line item per month.
- Opening balance carries forward automatically from each month's closing balance.
- Live totals: total inflows, total outflows, net movement, closing balance.
- **Neveen Money** target (90,000 SAR) with gap-vs-target for every month.
- All edits are saved locally on the device and survive app restarts.
- "Reset to plan" restores the original values at any time.

## Install on your phone
The APK is built automatically by GitHub Actions and published as a Release.

1. Open the repository's **Releases** page and download `Neveen.apk`
   from the release tagged **`cashflow-latest`**, or use the direct asset link:
   `https://github.com/mhashem2011/apptest/releases/download/cashflow-latest/Neveen.apk`
2. On the phone, allow installing apps from unknown sources when prompted.
3. Open the downloaded APK and tap **Install**.

> This is a debug build signed with the standard Android debug key — perfect for
> personal sideloading. It is not a Play Store release.

## Tech
- Single-Activity Android app (Java) hosting a `WebView`.
- All UI and cash-flow logic live in `app/src/main/assets/index.html`
  (HTML/CSS/JS, data persisted via the WebView's `localStorage`).
- No third-party runtime dependencies — builds cleanly with the Android SDK.

## Build locally (optional)
Requires the Android SDK and Gradle 8.7+ (or Android Studio):

```bash
gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```
