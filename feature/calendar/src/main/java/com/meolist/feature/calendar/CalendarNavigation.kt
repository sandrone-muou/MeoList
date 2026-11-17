package com.meolist.feature.calendar

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.calendarGraph(navController: NavController, isTopLevel: (String) -> Boolean = { false }) {
    composable(Routes.CALENDAR) {
        CalendarRoute(
            onBack = if (isTopLevel(Routes.CALENDAR)) null else ({ navController.popBackStack(); Unit }),
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onOpenHabit = { navController.navigate(Routes.habitDetail(it)) },
            onOpenCountdown = { navController.navigate(Routes.countdownEdit(it)) },
            onCreateTask = { navController.navigate(Routes.taskCreate(it.toEpochDay())) },
        )
    }
}
