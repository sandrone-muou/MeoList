package com.meolist.domain.backup

/** What a restore brought back, shown to the user afterwards. */
data class BackupSummary(
    val createdAt: Long,
    val lists: Int,
    val tasks: Int,
    val records: Int,
    val habits: Int,
    val countdowns: Int,
    val harvests: Int,
    val settingsRestored: Boolean,
)

/** The file is not a MeoList backup of a supported version. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Creates and restores complete snapshots of the local database, and exports time records as CSV.
 * Implemented in the data layer; the UI only moves the resulting text to and from a file.
 */
interface BackupService {
    /** A JSON document with every list, task, record, habit, countdown and farm row plus settings. */
    suspend fun createBackup(): String

    /**
     * Replaces all data with the backup and re-arms reminders, notifications and the farm.
     * Throws [BackupFormatException] when [json] is not a backup this version understands.
     */
    suspend fun restoreBackup(json: String): BackupSummary

    /** Time records as CSV with a UTF-8 BOM, ready for a spreadsheet. */
    suspend fun recordsCsv(): String
}
