package com.meolist.feature.tasks

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.tasksGraph(navController: NavController) {
    composable(Routes.TASKS) { PlaceholderScreen(stringResource(R.string.tasks_title)) }
    composable(Routes.QUADRANT) { PlaceholderScreen(stringResource(R.string.tasks_quadrant_title), onBack = { navController.popBackStack() }) }
}
