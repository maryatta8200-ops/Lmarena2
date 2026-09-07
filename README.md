# LM Arena Agent (Android)

A native **Android** app that clones the idea of an *AI agent mode* and talks to
**LMArena (lmarena.ai)** as its backend through an **OpenAI-compatible** endpoint.

It is built with **Kotlin + Jetpack Compose**, streams responses token-by-token,
lets you pick any model advertised by the backend, and exposes a small "agent
mode" persona picker (Assistant / Coder / Analyst / Writer).

---

## Why an OpenAI-compatible backend?

LMArena is a browser-based model arena. It does not ship a stable public API, so
the ecosystem wraps it with **OpenAI-compatible bridge/proxy** servers (for
example `hung319/lmarena2api`, `CloudWaddie/LMArenaBridge`, or any other gateway
that exposes LMArena models over `/v1/chat/completions`).

This app speaks that protocol (`GET /v1/models`, `POST /v1/chat/completions`
with Server-Sent Events streaming), so it works with **any such bridge or
self-hosted proxy**.

In the app's **Settings** you set:

- **Base URL** — your LMArena bridge endpoint (default `https://api.lmarena.ai/v1`).
- **API key / token** — optional, sent as a `Bearer` token if the bridge needs one.
- **Temperature**, **Agent mode** toggle.

On the chat screen you pick a **model** from the list the backend returns.

---

## Features

- Streaming chat (SSE) with an agent-style, task-oriented input box.
- Per-message markdown text with automatic scrolling.
- Model picker (populated from `/v1/models`) + manual refresh.
- Agent personas / system prompts (Assistant, Coder, Analyst, Writer).
- Settings screen to configure the LMArena backend, key, temperature, agent mode.
- Dark/light theme with dynamic color on Android 12+.

---

## Getting the APK

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) builds the APK on
GitHub's runners. The simplest way to grab a ready-to-install build is the
**GitHub Release**:

- **Latest release:** <https://github.com/maryatta8200-ops/Lmarena2/releases/tag/v1.0.0>
- **`app-debug.apk`** (installable): <https://github.com/maryatta8200-ops/Lmarena2/releases/download/v1.0.0/app-debug.apk>
- **`app-release-unsigned.apk`**: <https://github.com/maryatta8200-ops/Lmarena2/releases/download/v1.0.0/app-release-unsigned.apk>

You can also download the **`lmarena-agent-apk`** artifact from any workflow run's
**Artifacts** section (e.g. after a push or a manual **Run workflow** dispatch):

- `app/build/outputs/apk/debug/app-debug.apk` — debug build (installable).
- `app/build/outputs/apk/release/app-release-unsigned.apk` — unsigned release.

> Note: the sandbox used to author this project cannot reach the Android SDK /
> Maven / Gradle mirrors, so the APK is compiled in CI rather than locally. Once
> built it is published to the Actions artifact and (on manual/tag runs) to a
> GitHub Release.

---

## Building locally

```bash
# Requires JDK 17 and the Android SDK (set ANDROID_HOME or local.properties).
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

On first run, ensure `local.properties` has `sdk.dir=/path/to/Android/sdk`.

---

## Project layout

```
app/src/main/java/com/lmarena/agent/
├── MainActivity.kt            # Entry point + navigation (chat <-> settings)
├── data/
│   ├── SettingsStore.kt       # Persisted backend/behaviour settings + personas
│   └── LlmApi.kt              # OpenAI-compatible REST + SSE streaming client
└── ui/
    ├── ChatViewModel.kt       # Chat/model state, streaming orchestration
    ├── ChatScreen.kt          # Chat UI
    ├── SettingsScreen.kt      # Backend configuration UI
    └── theme/Theme.kt         # Material 3 theme (dark/light, dynamic color)
```

---

## License

MIT
