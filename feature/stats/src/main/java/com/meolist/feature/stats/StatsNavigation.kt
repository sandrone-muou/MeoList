package com.meolist.feature.stats

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.statsGraph(navController: NavController) {
    composable(Routes.STATS) { PlaceholderScreen(stringResource(R.string.stats_title), onBack = { navController.popBackStack() }) }
}
