package com.meolist.feature.stats

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.statsGraph(navController: NavController) {
    composable(Routes.STATS) { StatsRoute(onBack = { navController.popBackStack() }) }
}
