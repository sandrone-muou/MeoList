package com.meolist.core.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/** The languages the app ships; Chinese is the default for new installs. */
enum class AppLanguage(val tag: String, val locale: Locale) {
    ZH("zh-CN", Locale.SIMPLIFIED_CHINESE),
    EN("en", Locale.ENGLISH),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ZH
    }
}

/**
 * Remembers the in-app language in plain SharedPreferences so it can be read synchronously in
 * `attachBaseContext`, before Hilt or DataStore are available. Chinese is used when nothing was
 * chosen, regardless of the system language.
 */
object LanguageStore {
    private const val PREFS = "meolist_ui"
    private const val KEY = "language"

    fun current(context: Context): AppLanguage =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.tag)
            .apply()
    }

    /** A context whose resources resolve [language]. Everything else (dark mode, font scale) is kept. */
    fun wrap(base: Context, language: AppLanguage = current(base)): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(language.locale)
        configuration.setLayoutDirection(language.locale)
        return base.createConfigurationContext(configuration)
    }
}

/** A context using the language chosen in settings. Call where strings are produced outside an activity. */
fun Context.localized(): Context = LanguageStore.wrap(this)

/** The Activity behind this context, used to recreate it after a language change. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
