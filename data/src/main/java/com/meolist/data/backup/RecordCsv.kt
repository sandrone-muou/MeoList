package com.meolist.data.backup

import com.meolist.data.db.entity.RecordEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Renders tracked time as a CSV a spreadsheet can open (UTF-8 BOM included for Excel). */
internal object RecordCsv {
    private val date = DateTimeFormatter.ISO_LOCAL_DATE
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun render(
        records: List<RecordEntity>,
        typeNames: Map<Long, String>,
        taskTitles: Map<Long, String>,
        zone: ZoneId,
    ): String {
        val builder = StringBuilder("\uFEFF")
        builder.append("date,start,end,minutes,activity,task,note,manual\n")
        records.sortedBy { it.startAt }.forEach { record ->
            val start = java.time.Instant.ofEpochMilli(record.startAt).atZone(zone)
            val end = java.time.Instant.ofEpochMilli(record.endAt).atZone(zone)
            builder
                .append(date.format(start.toLocalDate())).append(',')
                .append(time.format(start.toLocalTime())).append(',')
                .append(time.format(end.toLocalTime())).append(',')
                .append((record.endAt - record.startAt).coerceAtLeast(0) / 60_000).append(',')
                .append(escape(typeNames[record.typeId].orEmpty())).append(',')
                .append(escape(record.taskId?.let { taskTitles[it] }.orEmpty())).append(',')
                .append(escape(record.comment)).append(',')
                .append(if (record.isManual) "yes" else "no").append('\n')
        }
        return builder.toString()
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
