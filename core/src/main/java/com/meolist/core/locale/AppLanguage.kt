package com.meolist.core.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/** The languages the app ships. */
enum class AppLanguage(val tag: String, val locale: Locale) {
    ZH("zh-CN", Locale.SIMPLIFIED_CHINESE),
    EN("en", Locale.ENGLISH),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** Chinese uses the Chinese resources; every other system language falls back to English. */
        fun fromSystemLanguage(language: String?): AppLanguage =
            if (language?.startsWith("zh") == true) ZH else EN
    }
}

/**
 * Remembers the in-app language in plain SharedPreferences so it can be read synchronously in
 * `attachBaseContext`, before Hilt or DataStore are available. No stored choice means "follow the
 * system language"; unsupported system languages resolve to English.
 */
object LanguageStore {
    private const val PREFS = "meolist_ui"
    private const val KEY = "language"
    private const val SYSTEM = "system"

    /** The explicit user choice, or null when the app follows the system language. */
    fun selected(context: Context): AppLanguage? =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    /** The language actually used for resources right now. */
    fun current(context: Context): AppLanguage =
        selected(context) ?: systemLanguage(context)

    fun save(context: Context, language: AppLanguage?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language?.tag ?: SYSTEM)
            .apply()
    }

    private fun systemLanguage(context: Context): AppLanguage {
        val locales = context.resources.configuration.locales
        val language = if (locales.isEmpty) Locale.getDefault().language else locales[0].language
        return AppLanguage.fromSystemLanguage(language)
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
