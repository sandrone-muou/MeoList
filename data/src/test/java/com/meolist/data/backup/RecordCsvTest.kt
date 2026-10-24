package com.meolist.data.backup

import com.meolist.data.db.entity.RecordEntity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCsvTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, 24, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun rendersChronologicalSpreadsheetRows() {
        val records = listOf(
            RecordEntity(2, 1, null, at(10), at(10, 30), "", false),
            RecordEntity(1, 1, 5, at(8), at(9, 30), "deep, \"focused\" work", true),
        )
        val csv = RecordCsv.render(
            records = records,
            typeNames = mapOf(1L to "Work"),
            taskTitles = mapOf(5L to "Write report"),
            zone = zone,
        )

        assertTrue(csv.startsWith("\uFEFFdate,start,end,minutes,activity,task,note,manual\n"))
        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertEquals(3, lines.size)
        assertEquals("2026-10-24,08:00,09:30,90,Work,Write report,\"deep, \"\"focused\"\" work\",yes", lines[1])
        assertEquals("2026-10-24,10:00,10:30,30,Work,,,no", lines[2])
    }

    @Test
    fun negativeDurationsCountAsZero() {
        val csv = RecordCsv.render(
            records = listOf(RecordEntity(1, 1, null, at(10), at(9), "", false)),
            typeNames = emptyMap(),
            taskTitles = emptyMap(),
            zone = zone,
        )
        assertTrue(csv, csv.contains(",0,,,,no"))
    }
}
