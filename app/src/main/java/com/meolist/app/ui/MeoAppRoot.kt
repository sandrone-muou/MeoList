package com.meolist.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.meolist.feature.farm.rememberFarmReadyCount
import com.meolist.feature.habits.habitsGraph
import com.meolist.feature.settings.settingsGraph
import com.meolist.feature.stats.statsGraph
import com.meolist.feature.tasks.tasksGraph
import com.meolist.feature.tracker.trackerGraph
import com.meolist.notification.DeepLink

private enum class TopLevel(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Tasks(Routes.TASKS, R.string.nav_tasks, Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    Tracker(Routes.TRACKER, R.string.nav_tracker, Icons.Outlined.Timer, Icons.Filled.Timer),
    Habits(Routes.HABITS, R.string.nav_habits, Icons.Outlined.Repeat, Icons.Filled.Repeat),
    Farm(Routes.FARM, R.string.nav_farm, Icons.Outlined.Grass, Icons.Filled.Grass),
    More(Routes.MORE, R.string.nav_more, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
}

private val topLevelRoutes = TopLevel.entries.map { it.route }.toSet()

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
    val farmReady = rememberFarmReadyCount()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
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
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (item == TopLevel.Farm && farmReady > 0) {
                                            Badge { Text(farmReady.toString()) }
                                        }
                                    },
                                ) {
                                    Icon(
                                        if (selected) item.selectedIcon else item.icon,
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.TASKS,
                enterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    if (from in topLevelRoutes && to in topLevelRoutes) {
                        fadeIn(tween(200))
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            tween(300),
                        ) + fadeIn(tween(300))
                    }
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = {
                    val topLevel = initialState.destination.route in topLevelRoutes &&
                        targetState.destination.route in topLevelRoutes
                    fadeIn(tween(if (topLevel) 200 else 220))
                },
                popExitTransition = {
                    if (initialState.destination.route in topLevelRoutes &&
                        targetState.destination.route in topLevelRoutes
                    ) {
                        fadeOut(tween(140))
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            tween(260),
                        ) + fadeOut(tween(220))
                    }
                },
            ) {
                tasksGraph(navController)
                trackerGraph(navController)
                habitsGraph(navController)
                farmGraph(navController)
                countdownGraph(navController)
                statsGraph(navController)
                calendarGraph(navController)
                settingsGraph(navController)
                composable(Routes.MORE) {
                    MoreScreen(onNavigate = { navController.navigate(it) })
                }
            }
        }
    }
}
