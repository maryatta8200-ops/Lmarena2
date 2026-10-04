# Train Relay locally

Relay's first learner is deliberately small, explainable, and offline. It uses a tokenized TF-IDF similarity retriever combined with a multinomial Naive Bayes intent classifier. For a message, the engine selects a close training example and returns that example's saved reply. It does not invent a reply when the match is weak. Model fitting and inference happen in the Android app; there is no model API, telemetry endpoint, or `INTERNET` permission.

This is example-based personalization, not neural-network fine-tuning. For private SMS auto-replies it is a safer starting point than an unconstrained generative model: every possible output is one of the replies the user imported or added. An optional on-device LLM can be evaluated later behind the `ReplySuggestionEngine` interface, but it would require a model file, substantially more storage/RAM, and its own safety evaluation. It is not required for this build.

## Local architecture

1. `TrainingImporter` validates a selected JSON or SQLite file and inserts normalized examples into the app-private `training_examples` table.
2. `LocalLearningEngine` builds the local token/intent statistics in memory and combines Naive Bayes intent likelihood with TF-IDF cosine retrieval.
3. The Local AI tab uses the same model for a preview. The Android SMS delivery receiver may request a suggestion only after all explicit global/per-number switches and SMS-role checks pass.
4. The auto-send path sends only a stored example reply when the score is at least 0.78, the message is not an opt-out, and the per-number cooldown has expired. Otherwise the model abstains.

There is no network model provider in the app. `ReplySuggestionEngine` is the boundary for future local model experiments; do not connect it to a cloud service without a separate privacy/consent design.

## JSON format

Choose a `.json` file in the **Local AI** tab. The root object must be version 1 and contain `examples`. Each example has an intent label, an example inbound message, and the reply to reuse:

```json
{
  "version": 1,
  "examples": [
    {
      "intent": "busy",
      "input": "I am in a meeting, can I call you later?",
      "reply": "I am busy right now. I will call you when I am free."
    },
    {
      "intent": "arrival_time",
      "input": "When will you get here?",
      "reply": "I should be there in about twenty minutes."
    }
  ]
}
```

Constraints: up to 5,000 examples per import and 10,000 stored examples total; intent 1–64 characters; input 1–500 characters; reply 1–320 characters; JSON file up to 5 MiB. Empty values, malformed JSON, overlong fields, and unsupported versions are rejected. Examples are added to the local training table; importing does not replace your whole dataset.

## SQLite format

Choose a `.sqlite` or `.db` file. It must contain either an `examples` or `training_examples` table with text columns named `intent`, `input_text`, and `reply_text`:

```sql
CREATE TABLE examples (
  intent TEXT NOT NULL,
  input_text TEXT NOT NULL,
  reply_text TEXT NOT NULL
);
```

The import is opened read-only, limited to 5,000 accepted rows and 20 MiB, and copied to a temporary app-cache file that is deleted after import. The importer never executes SQL from the file. Rows use the same length limits as JSON.

## Suggested workflow

1. Create a small, representative file with messages and replies you are comfortable storing on this device. Avoid exporting other people's private conversations without their consent.
2. Import the file in **Local AI**. Check the accepted-example count and try several expected messages in the suggestion preview.
3. Add a number in **Numbers**, choose a category, profession, and purpose, then explicitly enable auto-reply for that number if appropriate.
4. Enable the global auto-reply switch only after Relay is the default SMS app and all required Android permissions are granted.
5. Test on a non-sensitive conversation first. The auto-send path requires a local score of at least 0.78 (the score is a similarity heuristic, not a calibrated probability) and abstains on weak matches. Keep auto-reply off if suggestions are not suitable.
6. To stop replies immediately, turn off the global switch or the individual number switch. To remove a training example, clear the local training data in the AI tab.

## Data and limitations

Training examples, contacts, and Relay's message index are stored in Relay's app-private SQLite database. The app does not upload them. Android's own SMS provider continues to be managed by the operating system/default-SMS role; uninstalling Relay does not erase system SMS. Backup is disabled for Relay's private files, but this is not equivalent to database encryption. Protect the device with Android's lock screen, and do not import data you are not authorized to use.

Training quality depends on coverage and representative examples. This engine is not a general-purpose chatbot, cannot fact-check, and should not answer emergencies, financial decisions, or other high-stakes messages. Auto-reply is a user-controlled convenience feature, not an unattended agent.
