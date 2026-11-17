package com.meolist.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable(Routes.BOTTOM_NAV_SETTINGS) { BottomNavigationRoute(onBack = { navController.popBackStack() }) }
    composable(Routes.SETTINGS) {
        SettingsRoute(onBack = { navController.popBackStack() }, onBottomNavigation = { navController.navigate(Routes.BOTTOM_NAV_SETTINGS) })
    }
}
