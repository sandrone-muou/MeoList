package com.meolist.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.meolist.core.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

/** "Today", "Tomorrow", a weekday within the coming week, or a short date (with year if needed). */
@Composable
@ReadOnlyComposable
fun relativeDateLabel(date: LocalDate, today: LocalDate): String {
    val locale = currentLocale()
    return when {
        date == today -> stringResource(R.string.core_today)
        date == today.plusDays(1) -> stringResource(R.string.core_tomorrow)
        date == today.minusDays(1) -> stringResource(R.string.core_yesterday)
        date > today && date <= today.plusDays(6) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        else -> shortDate(date, today, locale)
    }
}

fun shortDate(date: LocalDate, today: LocalDate, locale: Locale): String {
    val skeleton = if (date.year == today.year) "MMMd" else "yMMMd"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
}

fun longDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMdEEE"), locale).format(date)

@Composable
@ReadOnlyComposable
fun timeLabel(time: LocalTime): String {
    val context = LocalContext.current
    val locale = currentLocale()
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(time)
}
