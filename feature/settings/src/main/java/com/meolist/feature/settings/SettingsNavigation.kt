package com.meolist.feature.settings

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.PlaceholderScreen

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable(Routes.SETTINGS) { PlaceholderScreen(stringResource(R.string.settings_title), onBack = { navController.popBackStack() }) }
}
