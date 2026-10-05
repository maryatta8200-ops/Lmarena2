# Knowledge JSONL import

Knowledge is not bundled. Select a JSON Lines file through the Library screen. Every nonblank line must be one schema-v1 JSON object; unknown fields are rejected. The JSONL decoder requires valid UTF-8.

## Fields

Required fields:

- `schema_version`: integer `1`.
- `id`: stable ID of 1–128 ASCII letters, digits, dot, underscore, colon, or hyphen.
- `record_type`: one of `FACT`, `GUIDELINE`, `TRIAL`, `EDUCATIONAL_EXPLANATION`, or `CPSP_PAKISTAN_SYNOPSIS` (case-insensitive on import).
- `specialty`, `title`, `content`, `source`, `author_or_organization`, `evidence_level`, and `license`.

Optional fields:

- `source_url`: bounded HTTPS URL with a host and no embedded username/password.
- `publication_year`: from 1500 through the current UTC year plus one.
- `version`: positive revision integer; defaults to 1.
- `effective_date` and `review_or_expiration_date`: optional ISO calendar dates in `yyyy-MM-dd` form.
- `provenance`: at most 32 string key/value entries with bounded lengths and no control characters.

The importer enforces field-size limits, a 20 MiB file limit, a 128 KiB per-line limit, at most 5,000 records, and a bounded physical-line count. Malformed/invalid individual rows are reported and not converted into records. Exceeding the dataset/line-count ceiling rejects the entire import. Stable IDs repeated within one file are rejected after the first valid instance.

## Review and retrieval

All imported records are saved as `UNREVIEWED`, regardless of any claimed review status in external data. The Library UI allows a user to inspect, locally verify, reject, or delete records. Local verification is not an independent clinical appraisal and does not certify the source.

The retrieval query uses local Room/SQLite FTS5 lexical search. Only `VERIFIED` records are eligible for assistant context. If present, `effective_date` must be today or earlier and `review_or_expiration_date` must be today or later, using the current UTC date. A malformed date fails closed. These dates are also surfaced with citations. There is no embedding model or vector database in this build.

The app never uses imported knowledge as training data. External PubMed results are separate, untrusted, and are not automatically added to this library. Users are responsible for confirming source authenticity, license/usage rights, currency, context, and relevance before local review.
