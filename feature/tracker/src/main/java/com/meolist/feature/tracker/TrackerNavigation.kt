package com.meolist.feature.tracker

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.trackerGraph(navController: NavController) {
    composable(Routes.TRACKER) {
        TrackerRoute(
            onEditType = { navController.navigate(Routes.activityTypeEdit(it)) },
            onRecords = { navController.navigate(Routes.RECORDS) },
        )
    }
    composable(
        Routes.ACTIVITY_TYPE_EDIT,
        arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
    ) {
        ActivityTypeEditRoute(onBack = { navController.popBackStack() })
    }
    composable(Routes.RECORDS) {
        RecordsRoute(onBack = { navController.popBackStack() })
    }
}
