package com.meolist.feature.calendar

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.calendarGraph(navController: NavController) {
    composable(Routes.CALENDAR) {
        CalendarRoute(
            onBack = { navController.popBackStack() },
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onOpenHabit = { navController.navigate(Routes.habitDetail(it)) },
            onOpenCountdown = { navController.navigate(Routes.countdownEdit(it)) },
        )
    }
}
