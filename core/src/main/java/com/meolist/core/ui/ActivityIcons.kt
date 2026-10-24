package com.meolist.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons an activity type can use, stored by key so the database never holds drawable ids. */
object ActivityIcons {
    const val DEFAULT = "timer"

    val all: List<Pair<String, ImageVector>> = listOf(
        "check" to Icons.Outlined.CheckCircle,
        "timer" to Icons.Outlined.Timer,
        "work" to Icons.Outlined.Work,
        "code" to Icons.Outlined.Code,
        "study" to Icons.Outlined.School,
        "read" to Icons.Outlined.MenuBook,
        "write" to Icons.Outlined.Edit,
        "language" to Icons.Outlined.Language,
        "art" to Icons.Outlined.Brush,
        "music" to Icons.Outlined.MusicNote,
        "sport" to Icons.Outlined.FitnessCenter,
        "run" to Icons.Outlined.DirectionsRun,
        "walk" to Icons.Outlined.DirectionsWalk,
        "meditate" to Icons.Outlined.SelfImprovement,
        "water" to Icons.Outlined.WaterDrop,
        "sun" to Icons.Outlined.WbSunny,
        "clean" to Icons.Outlined.CleaningServices,
        "sleep" to Icons.Outlined.Bedtime,
        "meal" to Icons.Outlined.Restaurant,
        "coffee" to Icons.Outlined.LocalCafe,
        "home" to Icons.Outlined.Home,
        "shopping" to Icons.Outlined.ShoppingCart,
        "commute" to Icons.Outlined.DirectionsCar,
        "social" to Icons.Outlined.People,
        "movie" to Icons.Outlined.Movie,
        "game" to Icons.Outlined.Gamepad,
    )

    private val byKey = all.toMap()

    fun of(key: String): ImageVector = byKey[key] ?: Icons.Outlined.Timer
}
