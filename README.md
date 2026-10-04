# Relay — private SMS and local reply learning

Relay is a native Android messaging app prototype built around a familiar, clean inbox. It is not a Google product and does not use Google Messages branding or assets.

## What is included

- A Compose-based conversation, number-book, local-learning, and settings interface.
- A local SQLite store for manually added numbers, categories, profession, purpose, SMS history, and opted-in training examples.
- Default-SMS-role and runtime-permission requests. Auto-reply is off by default and requires both the global switch and explicit per-number opt-in.
- An on-device TF-IDF / Naive Bayes retrieval engine. It returns a reply that the user previously supplied; it does not generate ungrounded text and makes no cloud requests.
- JSON and SQLite training-file import. See [`docs/LOCAL_AI_TRAINING.md`](docs/LOCAL_AI_TRAINING.md).
- A WhatsApp handoff using the official `wa.me` link. It opens WhatsApp for the number; it does **not** read, sync, or send WhatsApp messages from Relay.
- No `INTERNET` permission. Imported content, contacts, and messages stay in app-private storage; Android's shared SMS provider is used when Relay is the default SMS app. App backup is disabled.

## Android requirements and build

- Android Studio / Android SDK platform 35, JDK 17, and Gradle 8.9.
- Minimum Android version: 8.0 (API 26).
- Open the repository in Android Studio and sync the Gradle wrapper, or run `./gradlew testDebugUnitTest lint assembleDebug`. The wrapper pins Gradle 8.9 and verifies the downloaded distribution with its SHA-256 checksum.
- The GitHub Actions workflow runs unit tests, lint, and a debug APK build, then uploads the APK as a short-lived artifact.

To send and receive SMS, the user must choose Relay as the default SMS app and grant the requested Android runtime permissions. The app never changes the system default silently. Android/SIM/carrier support and account/SMS charges still apply. No call-log, microphone, camera, location, or internet permission is requested.

## Important scope limits

- WhatsApp integration is an official-app handoff only. Ordinary third-party apps cannot access a person's private WhatsApp inbox using only a phone number. Business messaging would require a separately authorized WhatsApp Business Platform integration.
- This first build handles SMS. It registers the Android MMS delivery role, but MMS media download/rendering is not implemented yet.
- The learning engine is a small local classifier/retriever, not a fine-tuned generative LLM. This is intentional: it can be trained immediately from user examples without uploading sensitive conversations or downloading a multi-gigabyte model. A future model adapter can be added without changing the SMS/contacts data layer.
- This prototype is not a Play Store compliance or security certification. Default-SMS distribution is subject to Google's current SMS permission and policy requirements.

## Safety defaults

Automatic replies are disabled on install. Enabling them requires SMS role/permissions, the global auto-reply toggle, a contact-specific toggle, a trained match, and a strong similarity threshold. The receiver suppresses replies to opt-out words and applies a per-number cooldown. The user remains responsible for reviewing training examples and testing behavior before enabling auto-reply.
