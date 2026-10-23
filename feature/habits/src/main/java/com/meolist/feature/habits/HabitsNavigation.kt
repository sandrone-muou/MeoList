package com.meolist.feature.habits

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.habitsGraph(navController: NavController) {
    composable(Routes.HABITS) { PlaceholderScreen(stringResource(R.string.habits_title)) }
}
