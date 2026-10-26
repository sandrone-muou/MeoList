package com.meolist.feature.farm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.domain.farm.FarmInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How many crops are ripe right now; the app shell shows it as a badge on the farm tab. */
@HiltViewModel
class FarmAlertsViewModel @Inject constructor(
    farm: FarmInteractor,
) : ViewModel() {
    val readyCount: StateFlow<Int> = farm.observe()
        .map { snapshot ->
            snapshot.activePlots.count { plot -> snapshot.growth(plot) >= plot.crop.need }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@Composable
fun rememberFarmReadyCount(): Int {
    val vm: FarmAlertsViewModel = hiltViewModel()
    return vm.readyCount.collectAsStateWithLifecycle().value
}
