package com.meolist.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meolist.app.R
import com.meolist.core.navigation.Routes
import com.meolist.feature.calendar.calendarGraph
import com.meolist.feature.countdown.countdownGraph
import com.meolist.feature.farm.farmGraph
import com.meolist.feature.habits.habitsGraph
import com.meolist.feature.settings.settingsGraph
import com.meolist.feature.stats.statsGraph
import com.meolist.feature.tasks.tasksGraph
import com.meolist.feature.tracker.trackerGraph
import com.meolist.notification.DeepLink

private enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Tasks(Routes.TASKS, R.string.nav_tasks, Icons.Outlined.CheckCircle),
    Tracker(Routes.TRACKER, R.string.nav_tracker, Icons.Outlined.Timer),
    Habits(Routes.HABITS, R.string.nav_habits, Icons.Outlined.Repeat),
    Farm(Routes.FARM, R.string.nav_farm, Icons.Outlined.Grass),
    More(Routes.MORE, R.string.nav_more, Icons.Outlined.MoreHoriz),
}

@Composable
fun MeoAppRoot(deepLink: DeepLink? = null, onDeepLinkHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    LaunchedEffect(deepLink) {
        when (deepLink) {
            null -> return@LaunchedEffect
            is DeepLink.Task -> navController.navigate(Routes.taskDetail(deepLink.taskId)) { launchSingleTop = true }
            DeepLink.Tracker -> navController.navigate(Routes.TRACKER) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        onDeepLinkHandled()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevel.entries.any { it.route == destination?.route }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.TASKS) {
                tasksGraph(navController)
                trackerGraph(navController)
                habitsGraph(navController)
                farmGraph(navController)
                countdownGraph(navController)
                statsGraph(navController)
                calendarGraph(navController)
                settingsGraph(navController)
                composable(Routes.MORE) { MoreScreen(onNavigate = { navController.navigate(it) }) }
            }
        }
    }
}
