package com.meolist.feature.farm

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.farmGraph(navController: NavController) {
    composable(Routes.FARM) { PlaceholderScreen(stringResource(R.string.farm_title)) }
    composable(Routes.ACHIEVEMENTS) { PlaceholderScreen(stringResource(R.string.farm_achievements_title), onBack = { navController.popBackStack() }) }
}
