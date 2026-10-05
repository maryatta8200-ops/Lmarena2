#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v cargo >/dev/null || { echo "Rust/cargo is required. Install Rust stable first." >&2; exit 2; }
command -v cargo-ndk >/dev/null || { echo "Install cargo-ndk 4.1.2: cargo install cargo-ndk --version 4.1.2 --locked" >&2; exit 2; }
command -v rustup >/dev/null || { echo "rustup is required to install Android Rust targets." >&2; exit 2; }

NDK_VERSION="28.2.13676358"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -n "$SDK_ROOT" && -d "$SDK_ROOT/ndk/$NDK_VERSION" ]]; then
  export ANDROID_NDK_HOME="$SDK_ROOT/ndk/$NDK_VERSION"
fi
if [[ -z "${ANDROID_NDK_HOME:-}" ]] || [[ ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "Install Android NDK $NDK_VERSION and set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
  exit 2
fi

rustup target add aarch64-linux-android x86_64-linux-android
cargo ndk --platform 26 -t arm64-v8a -t x86_64 \
  -o "${ROOT}/ai/tokenizer/src/main/jniLibs" \
  build --release --manifest-path "${ROOT}/native/ai-core/Cargo.toml"
