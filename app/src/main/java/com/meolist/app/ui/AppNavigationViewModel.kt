package com.meolist.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppNavigationViewModel @Inject constructor(prefs: AppPreferences) : ViewModel() {
    val settings: StateFlow<AppSettings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
