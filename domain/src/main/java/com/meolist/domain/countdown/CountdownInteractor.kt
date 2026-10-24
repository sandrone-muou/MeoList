package com.meolist.domain.countdown

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountdownInteractor @Inject constructor(
    private val countdowns: CountdownRepository,
) {
    suspend fun save(countdown: Countdown): Long {
        val clean = countdown.copy(title = countdown.title.trim(), note = countdown.note.trim())
        require(clean.title.isNotEmpty()) { "title must not be blank" }
        return countdowns.save(clean)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val countdown = countdowns.get(id) ?: return
        countdowns.save(countdown.copy(pinned = pinned))
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val countdown = countdowns.get(id) ?: return
        countdowns.save(countdown.copy(archived = archived))
    }

    suspend fun delete(id: Long) = countdowns.delete(id)
}
