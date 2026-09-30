# Metis for Android

Native Android client for a self-hosted Metis server. Android views call the existing authenticated server APIs directly; the app does not embed a WebView or run a separate backend.

Enter your server URL on first launch, then sign in with your Metis account. The mobile layout uses the web client's Geist and GFS Didot fonts, Lucide icons, dark surfaces, mobile sidebar, project filters and bottom composer.

Supported: chat creation/history/streaming/cancellation, model and agent-mode selection, chat search across titles and messages, pin/archive/rename/delete/move actions, projects, shared notes, automation overview, workspace reading/editing with version checks, pending questions with single/multiple choices, approval decisions, context/quota footer and chat sharing with optional password.

Full web feature parity is still in progress. Voice/realtime input, the complete settings/provider-management UI, interactive graphs/canvases, richer tool diff views and full automation editing are not implemented. Context counts fall back to a visibly marked estimate when matching server telemetry is unavailable.

## Build

Use JDK 17, Android SDK Platform 35 and Gradle 8.9:

```sh
cd android
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can also open this directory as a Gradle project. Debug signing is intended for direct testing; release distribution needs a release signing key.

## Verification

Tested on a Pixel 7 emulator running Android 15: authenticated startup, native sidebar, project filters, title/message search, composer and keyboard positioning, and a chat request returning `Native-Parity-OK`. Screenshots were compared with the mobile web view. Approval, sharing and workspace writes have not been exercised against live user data in this pass.

## Licenses

Geist and GFS Didot are bundled under the SIL Open Font License; notices are in `app/src/main/assets/fonts`. Lucide icons are distributed under the ISC license; see `app/src/main/assets/lucide-LICENSE.txt`.
