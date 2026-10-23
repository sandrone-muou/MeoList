package com.meolist.domain.common

import javax.inject.Qualifier

/**
 * Marks the process-wide CoroutineScope used for writes that must outlive a screen
 * (e.g. flushing an edit when the user navigates away).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
