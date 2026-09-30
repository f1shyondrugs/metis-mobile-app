# Metis for Android

Native Android client for a self-hosted Metis server. Android views call the existing authenticated APIs directly; the app contains no WebView and runs no separate backend. Enter the server URL on first launch, then sign in with your Metis account.

The mobile layout uses Metis' Geist and GFS Didot fonts, dark surfaces, Lucide icons, sidebar, project filters and composer. Version 1.2.0 adds:

- Server-backed settings for voice, models/parameters, subagent models, browser preferences, compression and feature flags. Existing provider connections can be edited/tested; API-key and local connections can be added. Account/OAuth onboarding still uses server setup.
- Microphone recording with permission handling, duration limit, stop/cancel and retry. M4A audio is sent to `/api/voice/transcribe` with the current provider configuration. Transcripts append to the draft without automatically sending it. Android speech recognition handles the browser provider when a recognizer is installed. Realtime transcription is not implemented.
- Native interactive graph blocks: functions, sliders, points, lines, circles and labels; drag/pinch navigation and reset. Graph and chart JSON fences render inside messages and workspace previews. Charts support bar, line, area, pie, donut and scatter with tap inspection. Advanced web graph editing and chart stacking/legend controls are still pending.
- Expanded tool details with selectable input, output, errors and copy actions; before/after file views use stored tool snapshots or `/api/chats/:id/tool-diff`. Chat logs can be loaded, filtered and inspected.

Existing features include chat creation/history/streaming/cancellation, model and agent-mode selection, title/message search, pin/archive/rename/delete/move, projects, shared notes, automation overview, workspace editing with version checks, questions, approval decisions, context/quota display and sharing. Context uses a marked estimate when server telemetry is unavailable. Complete web feature parity and full automation editing remain in progress.

## Build

Use JDK 17, Android SDK Platform 35 and Gradle 8.9:

```sh
cd android
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Open this directory in Android Studio to build there. Debug signing is for direct testing; distribution needs a release key.

Source asset bundles in `assets/*.b64` are decoded by Gradle into generated resources. Keep the text bundles in source control; do not commit caches or APKs.

## Verification

```sh
ANDROID_HOME=/path/to/android-sdk gradle connectedDebugAndroidTest
```

Five offline fixture tests exercise graph parameters and malformed-source fallback, all chart types, settings PATCH preservation, actual emulator microphone recording/multipart upload with a mock transcription response, and tool input/output/diff views. Live API checks are optional: run instrumentation with `-e liveServer true` on an emulator already signed into a test account containing the earlier `Native-Parity-OK` test chat. That check reads preferences/models/providers/logs and sends an empty preferences PATCH, preserving existing settings.

Verified on Pixel 7 / Android 15: six tests including authenticated server contracts. Audio capture and transcript handling were tested; recognition by a real transcription provider with spoken audio was not. Approval/sharing/workspace mutations and provider credential changes have not been executed against live account data in this pass. Test screenshots are evidence artifacts, not bundled in the APK.

## Licenses

Geist and GFS Didot: SIL Open Font License, notices in `assets/fonts`. Lucide: ISC, `assets/lucide-LICENSE.txt`. Expression evaluation uses exp4j 0.4.8 (Apache-2.0). Markdown uses Markwon 4.6.2 (Apache-2.0).
