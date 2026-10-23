package com.meolist.domain.testing

import com.meolist.domain.common.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Manually driven clock for tests. */
class FakeClock(
    var nowMs: Long = 0L,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) : Clock {
    override fun now(): Long = nowMs
    override fun zone(): ZoneId = zoneId

    fun set(dateTime: LocalDateTime) {
        nowMs = ZonedDateTime.of(dateTime, zoneId).toInstant().toEpochMilli()
    }

    fun advance(ms: Long) {
        nowMs += ms
    }
}
