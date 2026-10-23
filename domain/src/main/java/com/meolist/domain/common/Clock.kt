package com.meolist.domain.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Source of "now". Injected everywhere so time-dependent logic stays testable. */
interface Clock {
    fun now(): Long
    fun zone(): ZoneId
}

class SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun Clock.today(): LocalDate = LocalDate.now(java.time.Clock.fixed(Instant.ofEpochMilli(now()), zone()))

fun Clock.todayEpochDay(): Long = today().toEpochDay()

fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun Long.toLocalDate(zone: ZoneId): LocalDate = toLocalDateTime(zone).toLocalDate()

fun LocalDate.startOfDayMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun LocalDateTime.toMillis(zone: ZoneId): Long = ZonedDateTime.of(this, zone).toInstant().toEpochMilli()

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS
