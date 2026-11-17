package com.meolist.feature.stats

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.statsGraph(navController: NavController, isTopLevel: (String) -> Boolean = { false }) {
    composable(Routes.STATS) { StatsRoute(onBack = if (isTopLevel(Routes.STATS)) null else ({ navController.popBackStack(); Unit })) }
}
