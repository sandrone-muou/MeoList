package com.meolist.feature.calendar

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.calendarGraph(navController: NavController) {
    composable(Routes.CALENDAR) { PlaceholderScreen(stringResource(R.string.calendar_title), onBack = { navController.popBackStack() }) }
}
