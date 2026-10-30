package com.meolist.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons an activity type can use, stored by key so the database never holds drawable ids. */
object ActivityIcons {
    const val DEFAULT = "timer"

    private val sport = ImageVector.Builder(
        name = "RoundedDumbbell",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5.75f, 6f)
            lineTo(7.25f, 6f)
            quadTo(8.5f, 6f, 8.5f, 7.25f)
            lineTo(8.5f, 16.75f)
            quadTo(8.5f, 18f, 7.25f, 18f)
            lineTo(5.75f, 18f)
            quadTo(4.5f, 18f, 4.5f, 16.75f)
            lineTo(4.5f, 7.25f)
            quadTo(4.5f, 6f, 5.75f, 6f)
            close()
            moveTo(16.75f, 6f)
            lineTo(18.25f, 6f)
            quadTo(19.5f, 6f, 19.5f, 7.25f)
            lineTo(19.5f, 16.75f)
            quadTo(19.5f, 18f, 18.25f, 18f)
            lineTo(16.75f, 18f)
            quadTo(15.5f, 18f, 15.5f, 16.75f)
            lineTo(15.5f, 7.25f)
            quadTo(15.5f, 6f, 16.75f, 6f)
            close()
            moveTo(4.5f, 8.5f)
            lineTo(3.25f, 8.5f)
            quadTo(2f, 8.5f, 2f, 9.75f)
            lineTo(2f, 14.25f)
            quadTo(2f, 15.5f, 3.25f, 15.5f)
            lineTo(4.5f, 15.5f)
            moveTo(19.5f, 8.5f)
            lineTo(20.75f, 8.5f)
            quadTo(22f, 8.5f, 22f, 9.75f)
            lineTo(22f, 14.25f)
            quadTo(22f, 15.5f, 20.75f, 15.5f)
            lineTo(19.5f, 15.5f)
            moveTo(8.5f, 10.5f)
            lineTo(15.5f, 10.5f)
            moveTo(8.5f, 13.5f)
            lineTo(15.5f, 13.5f)
        }
    }.build()

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
        "sport" to sport,
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
