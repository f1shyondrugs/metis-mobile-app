# Metis Mobile

Metis Mobile is a set of native mobile clients for connecting to a Metis server.

## Apps

- **Android:** Native Android client in `android/`. It supports first-run server setup, sign in, chat list, new chats, conversation history, and streaming assistant replies.
- **iPhone:** Native iOS app planned. The iOS client is not included yet.

On first launch, enter the URL of the Metis server you want to use. Sign in with an account on that server. The app requires a network connection to the configured server.

## Build the Android app

Install JDK 17, Gradle 8.9, and Android SDK Platform 35. From the repository root:

```sh
cd android
gradle assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. It uses Android's debug signing key and is intended for direct installation and testing. Play Store releases need a release signing key and release build configuration.

## Status

Android development is underway. The iPhone app is planned for this repository.
