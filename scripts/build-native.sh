#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v cargo >/dev/null || { echo "Rust/cargo is required. Install Rust stable first." >&2; exit 2; }
command -v cargo-ndk >/dev/null || { echo "Install cargo-ndk 4.1.2: cargo install cargo-ndk --version 4.1.2 --locked" >&2; exit 2; }

rustup target add aarch64-linux-android x86_64-linux-android
cargo ndk --platform 26 -t arm64-v8a -t x86_64 \
  -o "${ROOT}/ai/tokenizer/src/main/jniLibs" \
  build --release --manifest-path "${ROOT}/native/ai-core/Cargo.toml"
