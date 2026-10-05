# Architecture and trust boundaries

## Module layout

- `app/`: Android entry point, Hilt graph, preferences, user actions, and flavor-specific network permission.
- `ui/`: Compose screens and UI-only projections; application orchestration remains in Kotlin.
- `ai/api/`: runtime-neutral inference, tokenizer, and training contracts.
- `ai/inference/`: ONNX Runtime Mobile decoder loop, CPU-default execution, session compatibility checks, and optional provider profiling.
- `ai/tokenizer/` and `native/ai-core/`: Rust `tokenizers` library exposed to Kotlin through JNI. The native build packages `arm64-v8a` and `x86_64` libraries.
- `ai/safety/`: deterministic request policy and conservative output-pattern checks.
- `ai/retrieval/`, `knowledge/api/`, and `storage/database/`: typed knowledge contracts, date-aware local retrieval, and Room/SQLite FTS5.
- `ai/model-format/` and `storage/files/`: model-manifest validation, publisher signatures, import limits, encrypted artifact storage, and verified plaintext cache handling.
- `tools/`: explicit tool descriptions, schema and authorization checks, and the optional PubMed provider.
- `integration/sms/` and `integration/whatsapp/`: SMS draft intent and official `wa.me` handoff only.

The database currently uses schema version 1. Room schema migrations must be added before any future database schema change is distributed to existing installs.

## Local question flow

1. A question is held in transient UI state and sent to the in-memory conversation gateway. It is not written to the local database or training store.
2. `MedicalSafetyPolicy` evaluates the request before retrieval or model inference. Some emergency and high-risk patterns return a fixed safety redirection without invoking the model. These rules are incomplete and not clinically validated.
3. Local FTS5 retrieval requests verified records only. An effective date later than the current UTC date, an invalid date, or a review/expiration date earlier than the current UTC date makes a record ineligible. Date boundaries are inclusive.
4. With no eligible evidence, the app states that it cannot answer from local sources. With evidence but no active model, it displays excerpts with provenance and explicitly says no model-generated answer was produced.
5. With a model, ONNX Runtime Mobile performs real token generation locally. The runtime requires a compatible tokenizer and dynamic-sequence ONNX tensors. CPU is the default provider and fallback.
6. A conservative output validator rejects empty answers, unknown or missing citation IDs, and some dosage-pattern matches. It does not verify factual support, check citation entailment, or establish clinical safety.

External PubMed results are fetched only in the `research` flavor after Settings opt-in and a per-search confirmation. They are marked untrusted, are not inserted into the knowledge database, and are not added automatically to model context. The `offline` flavor declares no Internet permission.

## Model and data lifecycle

- A model arrives only through the user-selected bundle importer. The manifest is checked against a public key whose fingerprint the user must verify independently; signature and file hashes are then validated.
- Model and tokenizer artifacts are encrypted with AES-GCM using a per-file data key protected by Android Keystore. Plaintext is materialized only in the app-private cache and purged at app startup/unload.
- Runtime compatibility smoke tests execute real model inference but their output is not presented as medical advice. Activation is a separate explicit action.
- Imported knowledge is always assigned `UNREVIEWED`. The user must inspect the original source, context, and license before locally marking a record verified. This local action is not an independent clinical appraisal.
- Training JSONL can be validated and encrypted after explicit file selection. There is no training implementation in this build, and conversation history is neither imported nor used for training.
- Knowledge records, model-registry/trusted-publisher metadata, and preferences are in app-private SQLite/DataStore files, but are not separately field-encrypted. Only model/tokenizer and imported training-dataset artifacts use the AES-GCM artifact store. Android device-level file encryption and the screen lock remain important; backup is disabled, not equivalent to database encryption.

## Communication and logging

The app does not request SMS-send or SMS-read permissions. Sharing opens a pre-filled SMS draft for review. WhatsApp uses an HTTPS click-to-chat URL; the app has no private WhatsApp protocol or message-history access. External apps handle any eventual sending.

Structured logs contain bounded event metadata and do not accept prompt or conversation text. The research provider records a query hash and source metadata rather than the raw search query. Android backup is disabled for the app.

## Limits

No model weights, clinical dataset, source corpus, embedding model, or vector database are bundled. Search is lexical FTS5, not semantic vector retrieval. A trusted signature and technical compatibility do not imply clinical validity. No medical-device or clinical-efficacy claim is made. Build/test status must be taken from actual local or CI evidence, not from this architecture description.
