package com.relaymessages.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Imports user-selected, structured examples; it never evaluates SQL or runs imported code. */
object TrainingImporter {
    private const val JSON_LIMIT_BYTES = 5L * 1024 * 1024
    private const val SQLITE_LIMIT_BYTES = 20L * 1024 * 1024
    private const val MAX_EXAMPLES = 5_000
    private const val MAX_STORED_EXAMPLES = 10_000

    fun importFile(context: Context, database: AppDatabase, uri: Uri): ImportSummary {
        val name = displayName(context, uri).lowercase()
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase().orEmpty()
        val isSqlite = name.endsWith(".sqlite") || name.endsWith(".sqlite3") || name.endsWith(".db") ||
            mimeType in setOf("application/vnd.sqlite3", "application/x-sqlite3", "application/x-sqlite")
        return if (isSqlite) importSqlite(context, database, uri) else importJson(context, database, uri)
    }

    private fun importJson(context: Context, database: AppDatabase, uri: Uri): ImportSummary {
        val bytes = readBounded(context, uri, JSON_LIMIT_BYTES)
        val examples = try {
            val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
            if (root.optInt("version", -1) != 1) {
                throw ImportException("JSON version must be 1.")
            }
            val rows = root.optJSONArray("examples") ?: throw ImportException("JSON needs an examples array.")
            if (rows.length() !in 1..MAX_EXAMPLES) {
                throw ImportException("Include between 1 and $MAX_EXAMPLES examples per file.")
            }
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: throw ImportException("Example ${index + 1} must be an object.")
                    add(
                        validatedExample(
                            intent = row.optString("intent"),
                            input = row.optString("input"),
                            reply = row.optString("reply"),
                            rowNumber = index + 1
                        )
                    )
                }
            }
        } catch (exception: JSONException) {
            throw ImportException("JSON could not be read: ${exception.message ?: "invalid format"}")
        }
        return persistExamples(database, examples)
    }

    private fun importSqlite(context: Context, database: AppDatabase, uri: Uri): ImportSummary {
        val tempFile = File.createTempFile("relay-training-", ".sqlite", context.cacheDir)
        try {
            copyBounded(context, uri, tempFile, SQLITE_LIMIT_BYTES)
            val examples = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { importedDb ->
                val tables = mutableSetOf<String>()
                importedDb.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                    while (cursor.moveToNext()) tables += cursor.getString(0)
                }
                val table = listOf("training_examples", "examples").firstOrNull { it in tables }
                    ?: throw ImportException("SQLite needs an examples or training_examples table.")
                val columns = mutableSetOf<String>()
                importedDb.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
                }
                val required = setOf("intent", "input_text", "reply_text")
                if (!columns.containsAll(required)) {
                    throw ImportException("SQLite table $table needs intent, input_text, and reply_text text columns.")
                }
                val result = mutableListOf<TrainingExample>()
                importedDb.rawQuery(
                    "SELECT intent, input_text, reply_text FROM $table LIMIT ${MAX_EXAMPLES + 1}",
                    null
                ).use { cursor ->
                    if (cursor.count !in 1..MAX_EXAMPLES) {
                        throw ImportException("SQLite must contain between 1 and $MAX_EXAMPLES examples.")
                    }
                    var row = 0
                    while (cursor.moveToNext()) {
                        row++
                        if (cursor.getType(0) != android.database.Cursor.FIELD_TYPE_STRING ||
                            cursor.getType(1) != android.database.Cursor.FIELD_TYPE_STRING ||
                            cursor.getType(2) != android.database.Cursor.FIELD_TYPE_STRING
                        ) {
                            throw ImportException("SQLite row $row must contain text in all three columns.")
                        }
                        result += validatedExample(
                            intent = cursor.getString(0),
                            input = cursor.getString(1),
                            reply = cursor.getString(2),
                            rowNumber = row
                        )
                    }
                }
                result
            }
            return persistExamples(database, examples)
        } catch (exception: ImportException) {
            throw exception
        } catch (exception: Exception) {
            throw ImportException("SQLite import failed: ${exception.message ?: "unsupported database"}")
        } finally {
            tempFile.delete()
        }
    }

    private fun persistExamples(database: AppDatabase, examples: List<TrainingExample>): ImportSummary {
        val existing = database.trainingExamples()
        val existingSet = existing.toHashSet()
        val newUniqueExamples = examples.distinct().count { it !in existingSet }
        if (existing.size + newUniqueExamples > MAX_STORED_EXAMPLES) {
            throw ImportException("Relay can store at most $MAX_STORED_EXAMPLES training examples. Clear some data and retry.")
        }
        val inserted = database.addTrainingExamples(examples)
        return ImportSummary(read = examples.size, added = inserted, duplicates = examples.size - inserted)
    }

    private fun validatedExample(intent: String, input: String, reply: String, rowNumber: Int): TrainingExample {
        val cleanIntent = intent.trim()
        val cleanInput = input.trim()
        val cleanReply = reply.trim()
        if (cleanIntent.isEmpty() || cleanIntent.length > 64) {
            throw ImportException("Example $rowNumber intent must be 1–64 characters.")
        }
        if (cleanInput.isEmpty() || cleanInput.length > 500) {
            throw ImportException("Example $rowNumber input must be 1–500 characters.")
        }
        if (cleanReply.isEmpty() || cleanReply.length > 320) {
            throw ImportException("Example $rowNumber reply must be 1–320 characters.")
        }
        if (cleanIntent.contains('\u0000') || cleanInput.contains('\u0000') || cleanReply.contains('\u0000')) {
            throw ImportException("Example $rowNumber contains an unsupported control character.")
        }
        return TrainingExample(cleanIntent, cleanInput, cleanReply)
    }

    private fun readBounded(context: Context, uri: Uri, limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("The selected file could not be opened.")
        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw ImportException("File is larger than the ${limit / (1024 * 1024)} MiB limit.")
                output.write(buffer, 0, count)
            }
        }
        if (output.size() == 0) throw ImportException("The selected file is empty.")
        return output.toByteArray()
    }

    private fun copyBounded(context: Context, uri: Uri, target: File, limit: Long) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("The selected file could not be opened.")
        var total = 0L
        input.use { stream ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) throw ImportException("SQLite file is larger than 20 MiB.")
                    output.write(buffer, 0, count)
                }
            }
        }
        if (total == 0L) throw ImportException("The selected file is empty.")
    }

    private fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }
}

data class ImportSummary(val read: Int, val added: Int, val duplicates: Int)

class ImportException(message: String) : IOException(message)
