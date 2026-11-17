package com.meolist.feature.countdown

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.countdownGraph(navController: NavController, isTopLevel: (String) -> Boolean = { false }) {
    composable(Routes.COUNTDOWNS) {
        CountdownsRoute(
            onBack = if (isTopLevel(Routes.COUNTDOWNS)) null else ({ navController.popBackStack(); Unit }),
            onOpen = { navController.navigate(Routes.countdownEdit(it)) },
            onAdd = { navController.navigate(Routes.countdownEdit()) },
        )
    }
    composable(
        Routes.COUNTDOWN_EDIT,
        arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
    ) {
        CountdownEditRoute(onBack = { navController.popBackStack() })
    }
}
