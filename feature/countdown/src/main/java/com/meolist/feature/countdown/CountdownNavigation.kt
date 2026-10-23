package com.meolist.feature.countdown

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.countdownGraph(navController: NavController) {
    composable(Routes.COUNTDOWNS) { PlaceholderScreen(stringResource(R.string.countdown_title), onBack = { navController.popBackStack() }) }
}
