package com.meolist.feature.farm

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.farmGraph(navController: NavController, isTopLevel: (String) -> Boolean = { false }) {
    composable(Routes.FARM) {
        FarmRoute(onBack = if (isTopLevel(Routes.FARM)) null else ({ navController.popBackStack(); Unit }), onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) })
    }
    composable(Routes.ACHIEVEMENTS) {
        AchievementsRoute(onBack = { navController.popBackStack() })
    }
}
