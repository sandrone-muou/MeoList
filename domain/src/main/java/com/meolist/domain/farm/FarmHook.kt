package com.meolist.domain.farm

/**
 * Signals that something which feeds the farm changed: a record was saved, a task completed, a
 * habit checked in or a goal reached. Implemented by [FarmInteractor]; the default does nothing so
 * the other interactors stay usable without a farm (tests, previews).
 */
fun interface FarmHook {
    suspend fun onNutrientsMayHaveChanged()

    companion object {
        val None = FarmHook { }
    }
}
