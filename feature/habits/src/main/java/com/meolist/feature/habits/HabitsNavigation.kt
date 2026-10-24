package com.meolist.feature.habits

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meolist.core.navigation.Routes

fun NavGraphBuilder.habitsGraph(navController: NavController) {
    composable(Routes.HABITS) {
        HabitsRoute(
            onOpen = { navController.navigate(Routes.habitDetail(it)) },
            onAdd = { navController.navigate(Routes.habitEdit()) },
        )
    }
    composable(
        Routes.HABIT_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
    ) {
        HabitDetailRoute(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.habitEdit(it)) },
        )
    }
    composable(
        Routes.HABIT_EDIT,
        arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
    ) {
        HabitEditRoute(onBack = { navController.popBackStack() })
    }
}
