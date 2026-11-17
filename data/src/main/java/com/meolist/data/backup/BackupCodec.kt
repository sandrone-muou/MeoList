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

    fun encodeLegacyFarm(archive: LegacyFarmArchiveDto): String =
        json.encodeToString(LegacyFarmArchiveDto.serializer(), archive)

    fun decodeLegacyFarm(archive: String): LegacyFarmArchiveDto =
        json.decodeFromString(LegacyFarmArchiveDto.serializer(), archive)

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (error: Exception) {
            throw BackupFormatException("Not a MeoList backup", error)
        }
        if (file.format !in setOf(BackupFile.LEGACY_FORMAT, BackupFile.FORMAT)) {
            throw BackupFormatException("Unsupported backup format ${file.format}")
        }
        if (file.format == BackupFile.FORMAT && file.farmState == null) {
            throw BackupFormatException("Farm state is missing")
        }
        if (file.format == BackupFile.FORMAT) validateFarm(file)
        validateTimerStages(file)
        return file
    }

    private fun validateTimerStages(file: BackupFile) {
        fun invalid(): Nothing = throw BackupFormatException("Timer stages are inconsistent")
        val types = file.activityTypes.map { it.id }.toSet()
        if (file.subprojects.map { it.id }.distinct().size != file.subprojects.size ||
            file.subprojects.any { it.id <= 0 || it.typeId !in types || it.name.isBlank() }) invalid()
        val running = file.runningTimers.associateBy { it.id }
        val records = file.records.associateBy { it.id }
        val projects = file.subprojects.associateBy { it.id }
        file.timerStages.groupBy { it.runningId to it.recordId }.forEach { (owner, list) ->
            if ((owner.first == null) == (owner.second == null)) invalid()
            val timer = owner.first?.let { running[it] ?: invalid() }
            val record = owner.second?.let { records[it] ?: invalid() }
            val typeId = timer?.typeId ?: requireNotNull(record).typeId
            val ordered = list.sortedBy { it.position }
            if (ordered.map { it.position } != (1..ordered.size).toList()) invalid()
            var cursor = timer?.startedAt ?: requireNotNull(record).startAt
            var openSeen = false
            ordered.forEach { stage ->
                stage.subprojectId?.let { projects[it]?.let { project -> if (project.typeId != typeId) invalid() } }
                if (stage.startAt == null) {
                    if (timer == null || !openSeen || stage.endAt != null) invalid()
                } else {
                    if (openSeen || stage.startAt != cursor) invalid()
                    if (stage.endAt == null) {
                        if (timer == null) invalid()
                        openSeen = true
                    } else {
                        if (stage.endAt <= stage.startAt) invalid()
                        cursor = stage.endAt
                    }
                }
            }
            if (timer != null && !openSeen) invalid()
            if (record != null && cursor != record.endAt) invalid()
        }
    }

    private fun validateFarm(file: BackupFile) {
        val state = requireNotNull(file.farmState)
        val invalid = state.rulesVersion != 2 || state.nextSequence < 0 ||
            file.farmFields.map { it.slot }.distinct().size != file.farmFields.size ||
            file.farmFields.map { it.slot }.sorted() != (0 until file.farmFields.size).toList() ||
            file.farmFields.size !in 3..5 ||
            file.farmFields.any { it.sourceCutoff !in 0..state.nextSequence } ||
            file.nutrients.any { it.amount < 0 || it.sequence !in 1..state.nextSequence } ||
            file.nutrients.map { it.sequence }.distinct().size != file.nutrients.size ||
            file.nutrients.map { it.sourceKey }.distinct().size != file.nutrients.size ||
            file.harvests.any { harvest -> file.farmPlots.none { it.id == harvest.plotId } } ||
            file.farmPlots.any { plot -> file.farmFields.none { it.slot == plot.slot } }
        if (invalid) throw BackupFormatException("Farm data is inconsistent")
    }
}
