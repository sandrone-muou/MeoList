package com.meolist.feature.tracker

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.trackerGraph(navController: NavController) {
    composable(Routes.TRACKER) { PlaceholderScreen(stringResource(R.string.tracker_title)) }
    composable(Routes.RECORDS) { PlaceholderScreen(stringResource(R.string.tracker_records_title), onBack = { navController.popBackStack() }) }
}
