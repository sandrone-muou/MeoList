package com.meolist.domain.tracker

enum class RecordProblem {
    /** No activity chosen, or the activity no longer exists. */
    NO_TYPE,
    /** End is not after the start. */
    NOT_POSITIVE,
    /** Ends in the future; time can only be recorded once it has passed. */
    IN_FUTURE,
}

sealed interface RecordSaveResult {
    data class Saved(val id: Long) : RecordSaveResult
    data class Invalid(val problem: RecordProblem) : RecordSaveResult
}

object RecordRules {
    /** Grace period so an end picked in the current minute still counts as "now". */
    private const val FUTURE_TOLERANCE_MS = 60_000L

    fun validate(record: TimeRecord, typeExists: Boolean, now: Long): RecordProblem? = when {
        !typeExists -> RecordProblem.NO_TYPE
        record.endAt <= record.startAt -> RecordProblem.NOT_POSITIVE
        record.endAt > now + FUTURE_TOLERANCE_MS -> RecordProblem.IN_FUTURE
        else -> null
    }
}
