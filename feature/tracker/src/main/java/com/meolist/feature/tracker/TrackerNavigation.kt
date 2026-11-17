package com.meolist.feature.tracker

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meolist.core.navigation.Routes
import java.time.LocalDate

fun NavGraphBuilder.trackerGraph(navController: NavController, isTopLevel: (String) -> Boolean = { false }) {
    composable(Routes.TRACKER) {
        TrackerRoute(
            onSession = { navController.navigate(Routes.stopwatch(it)) },
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
        RecordsRoute(
            onBack = if (isTopLevel(Routes.RECORDS)) null else ({ navController.popBackStack(); Unit }),
            onEditRecord = { navController.navigate(Routes.recordDetail(it)) },
            onAddRecord = { day: LocalDate -> navController.navigate(Routes.recordEdit(epochDay = day.toEpochDay())) },
            onStats = { navController.navigate(Routes.STATS) },
        )
    }
    composable(
        Routes.TIMER_SESSION,
        arguments = listOf(
            navArgument(Routes.ARG_ID) { type = NavType.LongType },
            navArgument(Routes.ARG_FINISHED) { type = NavType.BoolType; defaultValue = false },
        ),
    ) {
        TimerSessionRoute(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.recordEdit(recordId = it)) },
            onStopped = { id ->
                if (id == null) navController.popBackStack() else navController.navigate(Routes.recordDetail(id)) {
                    popUpTo(Routes.TIMER_SESSION) { inclusive = true }
                }
            },
        )
    }
    composable(
        Routes.RECORD_EDIT,
        arguments = listOf(
            navArgument(Routes.ARG_ID) { type = NavType.LongType },
            navArgument(Routes.ARG_EXTRA) { type = NavType.LongType; defaultValue = 0L },
            navArgument(Routes.ARG_DAY) { type = NavType.LongType; defaultValue = Routes.NO_DAY },
        ),
    ) {
        RecordEditRoute(
            onBack = { navController.popBackStack() },
            onAddActivityType = { navController.navigate(Routes.activityTypeEdit()) },
        )
    }
}
