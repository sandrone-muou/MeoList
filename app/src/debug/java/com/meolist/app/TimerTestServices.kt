package com.meolist.app

import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.tracker.TimeRecordRepository
import com.meolist.domain.tracker.TimerInteractor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Instrumentation fixtures use the same scoped interactors as the debug application. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimerTestServices {
    fun preferences(): AppPreferences
    fun timers(): TimerInteractor
    fun records(): TimeRecordRepository
}
