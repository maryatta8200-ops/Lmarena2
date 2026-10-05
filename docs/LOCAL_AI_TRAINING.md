# Training dataset import — training is not available

The Models screen can validate and encrypt an explicitly selected training JSONL file. This is **dataset intake only**. No optimizer, fine-tuning runtime, checkpoint writer, training job UI, or training backend is installed. The application does not start training or report a simulated training result.

Conversation text is not read, exported, or used for training. There is no automatic collection of user questions, answers, SMS, or WhatsApp content. A dataset import requires a user-selected document and explicit source/license metadata. The imported dataset is encrypted with the app's AES-GCM artifact store and can be deleted from the Models screen.

## JSONL contract

Each nonblank line must match schema version 1 and contain these fields:

- `schema_version`: `1`.
- `id`: unique stable ID (1–128 ASCII letters, digits, dot, underscore, colon, or hyphen).
- `task`: `instruction`, `completion`, or `supervised_finetuning`.
- `system`: up to 2,000 characters; it may be empty.
- `input` and `output`: each nonblank and at most 8,000 characters.
- `specialty`: nonblank, at most 120 characters.
- `source`: nonblank provenance, at most 500 characters.
- `license`: nonblank rights metadata, at most 200 characters.
- `language`: nonblank, at most 40 characters.
- `safety_labels`: optional list of at most 32 labels, each 1–64 characters.

Unknown fields, invalid UTF-8, malformed JSON, duplicate IDs, unsupported controls, or any rejected row cause the complete dataset validation to fail. The importer accepts at most 50 MiB, 10,000 examples, and 32,000 characters per row. The file hash and example count are recorded; the raw data is not logged.

## Current boundary

A validated dataset is stored encrypted and listed with its source, license, SHA-256, and example count. There is no way to launch or resume a training job in this build. Do not treat successful import as training, model creation, model improvement, or medical validation. A future trainer would require a separate implementation and review of resource limits, privacy, consent, data rights, model provenance, checkpoint integrity, and medical safety.
