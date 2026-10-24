package com.meolist.feature.tasks

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meolist.core.navigation.Routes
import com.meolist.feature.tasks.detail.TaskDetailRoute
import com.meolist.feature.tasks.manage.ListManageRoute
import com.meolist.feature.tasks.quadrant.QuadrantRoute
import com.meolist.feature.tasks.search.SearchRoute

fun NavGraphBuilder.tasksGraph(navController: NavController) {
    composable(Routes.TASKS) {
        TasksRoute(
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onSearch = { navController.navigate(Routes.TASK_SEARCH) },
            onManageLists = { navController.navigate(Routes.LIST_MANAGE) },
        )
    }
    composable(
        Routes.TASK_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
    ) {
        TaskDetailRoute(
            onBack = { navController.popBackStack() },
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onAddActivityType = { navController.navigate(Routes.activityTypeEdit()) },
        )
    }
    composable(Routes.TASK_SEARCH) {
        SearchRoute(
            onBack = { navController.popBackStack() },
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
        )
    }
    composable(Routes.LIST_MANAGE) {
        ListManageRoute(onBack = { navController.popBackStack() })
    }
    composable(Routes.QUADRANT) {
        QuadrantRoute(
            onBack = { navController.popBackStack() },
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
        )
    }
}
