# LocalMed Research

A modular Android workspace for **offline-first medical education and research**. The app is not a clinical decision-support system. It does not ship medical model weights or a clinical knowledge corpus, and it does not substitute canned output for a working model.

## What is in this build

- **Offline flavor:** no `INTERNET` permission. Knowledge search, encrypted local storage, model import, and ONNX inference do not depend on a service.
- **Research flavor:** adds optional PubMed E-utilities access. It is off by default, each search requires a separate confirmation, and results stay outside local model context and local evidence.
- **Local retrieval:** Room/SQLite FTS5 searches imported records. Imports start `UNREVIEWED`; only locally verified, currently effective, unexpired records can be retrieved.
- **Real local inference:** ONNX Runtime Mobile plus a Rust `tokenizers` JNI bridge. CPU is the default runtime. The APK contains no model weights; a user-imported signed bundle must pass provenance, publisher-signature, SHA-256, tokenizer, tensor-shape, and smoke checks before activation.
- **Safety and privacy:** deterministic pre-inference medical risk rules, output citation/dose-pattern checks, no long-term conversation memory, no conversation training, and AES-GCM encryption for model bundles and training-dataset files. Knowledge records and app metadata are stored in the private app database but are not separately field-encrypted. Safety rules are not clinically validated.
- **Communication handoff:** SMS opens a draft only. WhatsApp uses the public `wa.me` click-to-chat handoff; the app does not read or synchronize private WhatsApp messages. Neither integration sends a message automatically.

The current app supports licensed knowledge import and review, signed model import and activation, and encrypted training-dataset import. It does **not** include a transformer training backend, a medically validated model, a licensed corpus, an embedding model, or vector search. If no model is installed, it presents real local source excerpts or a clear unavailable/uncertain state; it does not fabricate an AI answer.

## Build debug APKs

Prerequisites:

- JDK 17
- Android SDK platform 37 and Build Tools 37.0.0
- Android NDK 28.2.13676358
- Rust stable and `cargo-ndk` 4.1.2
- Gradle 9.6.0 (the wrapper pins the distribution)

From the repository root:

```bash
cargo install cargo-ndk --version 4.1.2 --locked
./scripts/build-native.sh
./gradlew :app:assembleOfflineDebug :app:assembleResearchDebug
```

The installable, debug-signed outputs are:

- `app/build/outputs/apk/offline/debug/app-offline-debug.apk`
- `app/build/outputs/apk/research/debug/app-research-debug.apk`

Only `arm64-v8a` and `x86_64` are packaged. Debug APKs are for testing, not Play Store release distribution. They are signed with the local/CI debug key and are not signed release artifacts.

GitHub Actions runs Rust bridge tests, builds the native libraries, runs the Android unit tests and lint, assembles both debug APKs, verifies APK signatures and checks that only the research flavor requests `INTERNET`, then retains the APK artifacts for seven days. The workflow does not create a signed release APK or AAB.

## Safety and scope

This software is for educational/research exploration only, not diagnosis, prescribing, dosage decisions, or urgent care. Deterministic rules intercept some emergencies and high-risk requests but are incomplete and have not undergone clinical validation. A valid signature or successful runtime smoke test proves neither clinical safety nor medical accuracy. Publisher fingerprints must be verified through an independent channel.

See [Architecture](docs/ARCHITECTURE.md), [Model bundle format](docs/MODEL_BUNDLE.md), [Knowledge import](docs/KNOWLEDGE_IMPORT.md), and [Training dataset import](docs/LOCAL_AI_TRAINING.md).
