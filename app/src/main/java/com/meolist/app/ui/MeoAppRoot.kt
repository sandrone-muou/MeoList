package com.meolist.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberUpdatedState
import com.meolist.domain.settings.BottomDestination
import com.meolist.core.navigation.bottomDestinationIcon
import com.meolist.core.navigation.bottomDestinationLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

@Composable
fun MeoAppRoot(deepLink: DeepLink? = null, onDeepLinkHandled: () -> Unit = {}, vm: AppNavigationViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val items = settings?.bottomDestinations.orEmpty()
    val topLevelRoutes by rememberUpdatedState(items.map { it.key }.toSet())
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
    val showBottomBar = destination?.route in topLevelRoutes
    val farmReady = rememberFarmReadyCount()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    items.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.key } == true
                        NavigationBarItem(
                            modifier = Modifier.testTag("bottom-${item.key}"),
                            selected = selected,
                            onClick = {
                                navController.navigate(item.key) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (item == BottomDestination.FARM && farmReady > 0) {
                                            Badge { Text(farmReady.toString()) }
                                        }
                                    },
                                ) {
                                    Icon(
                                        bottomDestinationIcon(item, selected),
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = { Text(bottomDestinationLabel(item)) },
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
                tasksGraph(navController, isTopLevel = { it in topLevelRoutes })
                trackerGraph(navController, isTopLevel = { it in topLevelRoutes })
                habitsGraph(navController, isTopLevel = { it in topLevelRoutes })
                farmGraph(navController, isTopLevel = { it in topLevelRoutes })
                countdownGraph(navController, isTopLevel = { it in topLevelRoutes })
                statsGraph(navController, isTopLevel = { it in topLevelRoutes })
                calendarGraph(navController, isTopLevel = { it in topLevelRoutes })
                settingsGraph(navController)
                composable(Routes.MORE) {
                    MoreScreen(farmReady = farmReady, onNavigate = { route ->
                        navController.navigate(route) {
                            if (route in topLevelRoutes) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    })
                }
            }
        }
    }
}
