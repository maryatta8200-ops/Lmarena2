# Signed ONNX model bundle

Model weights are not shipped in the APK. The importer accepts a ZIP containing exactly these three files at the archive root:

- `manifest.json`
- `model.onnx`
- `tokenizer.json`

Unexpected files, path traversal, duplicate paths, empty entries, and entries above the configured limits are rejected. The current limits are 100 KiB for the manifest, 2 GiB for the ONNX model, and 50 MiB for the tokenizer. Nothing is downloaded by model import.

## Manifest fields

The schema is version 1 and is defined by `ModelManifest`, `ArchitectureMetadata`, and `TokenizerMetadata` in `ai/model-format`.

| Field | Meaning and validation |
| --- | --- |
| `schema_version` | Must be `1`. |
| `model_id`, `model_version` | Stable identifier and bounded version string. |
| `format`, `quantization` | Format must be `onnx`; quantization is descriptive metadata. |
| `model_sha256`, `tokenizer_sha256` | SHA-256 of the exact artifact bytes. Both are compared during import and again before runtime load. |
| `model_file`, `tokenizer_file` | Must be `model.onnx` and `tokenizer.json`. |
| `architecture` | Decoder-only transformer metadata: context length, vocabulary, hidden size, layer count, attention heads, input/output tensor names, optional attention-mask name, end-of-sequence IDs, and dynamic-sequence declaration. |
| `tokenizer` | Tokenizer ID/version, vocabulary size, and a bounded normalization description. Its vocabulary size must agree with the architecture metadata. |
| `source`, `license`, `created_at` | Bounded provenance and license text plus an ISO-8601 timestamp. These fields are signed. |
| `publisher_key_id`, `signature_algorithm`, `signature_base64` | Publisher identity key, currently `SHA256withRSA`, and the signature over the canonical payload. |

The canonical UTF-8 signing payload is newline-delimited, ends with a newline, and starts with `localmed-model-bundle-v1`. In order, it covers schema version; model ID/version; format and quantization; model path/hash; tokenizer path/hash; tokenizer ID/version/vocabulary/normalization; architecture kind/context/vocabulary/hidden size/layer count/heads/tensor names/sorted EOS IDs/dynamic-sequence flag; source; license; creation timestamp; publisher key ID; and signature algorithm. The signature field itself is excluded. See `ModelManifest.signingPayload()` for the exact encoding.

## Trust and runtime checks

1. Obtain the publisher's RSA public key and fingerprint from an independent trusted channel. Importing a key without comparing its fingerprint does not establish publisher identity.
2. Choose a model bundle and the trusted key ID. The importer validates the manifest, signature, archive layout, and both SHA-256 hashes before storing encrypted artifacts.
3. The runtime requires a batch size of one, dynamic sequence dimensions, int64 `input_ids`, an optional int64 `attention_mask`, float logits, and vocabulary compatibility. Unsupported tensor contracts fail the real smoke test rather than being simulated.
4. A successful technical smoke test only marks the bundle technically compatible. The user must explicitly activate it. Neither step establishes medical safety, quality, or clinical validity.

Imported encrypted artifacts stay in app-private storage. During loading the authenticated files are decrypted to an app-private temporary cache and checked again; plaintext cache files are removed at startup and unload. Removing or replacing the signing key unloads an active model when its signature no longer verifies. There is no bundled publisher key, private signing key, model weight, or sample clinical model.
