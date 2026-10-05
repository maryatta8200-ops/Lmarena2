package com.localmed.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.localmed.protocol.AppPreferencesProto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Singleton
class AppPreferencesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val store: DataStore<AppPreferencesProto> = DataStoreFactory.create(
        serializer = AppPreferencesSerializer,
        produceFile = { File(context.filesDir, PREFERENCES_FILE) }
    )

    val data: Flow<AppPreferencesProto> = store.data

    suspend fun setWebSearchEnabled(enabled: Boolean) {
        store.updateData { current ->
            current.toBuilder()
                .setSchemaVersion(PREFERENCES_SCHEMA_VERSION)
                .setWebSearchEnabled(enabled)
                .build()
        }
    }

    suspend fun setActiveModelId(modelId: String) {
        store.updateData { current ->
            current.toBuilder()
                .setSchemaVersion(PREFERENCES_SCHEMA_VERSION)
                .setActiveModelId(modelId.take(128))
                .build()
        }
    }

    companion object {
        private const val PREFERENCES_FILE = "app_preferences.pb"
        private const val PREFERENCES_SCHEMA_VERSION = 1
    }
}

private object AppPreferencesSerializer : Serializer<AppPreferencesProto> {
    override val defaultValue: AppPreferencesProto = AppPreferencesProto.newBuilder()
        .setSchemaVersion(1)
        .build()

    override suspend fun readFrom(input: InputStream): AppPreferencesProto = try {
        AppPreferencesProto.parseFrom(input)
    } catch (exception: Exception) {
        throw CorruptionException("LocalMed preferences file is corrupt.", exception)
    }

    override suspend fun writeTo(t: AppPreferencesProto, output: OutputStream) {
        t.writeTo(output)
    }
}
