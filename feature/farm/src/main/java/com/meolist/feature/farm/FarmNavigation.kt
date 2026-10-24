package com.meolist.feature.farm

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.farmGraph(navController: NavController) {
    composable(Routes.FARM) {
        FarmRoute(onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) })
    }
    composable(Routes.ACHIEVEMENTS) {
        AchievementsRoute(onBack = { navController.popBackStack() })
    }
}
