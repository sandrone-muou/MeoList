package com.meolist.data.backup

import com.meolist.domain.backup.BackupFormatException
import kotlinx.serialization.json.Json

/** JSON encoding for [BackupFile]. Unknown fields are ignored so newer files stay readable. */
object BackupCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (error: Exception) {
            throw BackupFormatException("Not a MeoList backup", error)
        }
        if (file.format != BackupFile.FORMAT) {
            throw BackupFormatException("Unsupported backup format ${file.format}")
        }
        return file
    }
}
