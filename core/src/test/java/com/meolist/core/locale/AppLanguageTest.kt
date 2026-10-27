package com.meolist.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun chineseSystemLanguagesUseChinese() {
        assertEquals(AppLanguage.ZH, AppLanguage.fromSystemLanguage("zh"))
        assertEquals(AppLanguage.ZH, AppLanguage.fromSystemLanguage("zh-Hans"))
        assertEquals(AppLanguage.ZH, AppLanguage.fromSystemLanguage("zh-TW"))
    }

    @Test
    fun unsupportedSystemLanguagesFallBackToEnglish() {
        assertEquals(AppLanguage.EN, AppLanguage.fromSystemLanguage("en"))
        assertEquals(AppLanguage.EN, AppLanguage.fromSystemLanguage("fr"))
        assertEquals(AppLanguage.EN, AppLanguage.fromSystemLanguage(null))
    }

    @Test
    fun onlyStoredTagsAreExplicitChoices() {
        assertEquals(AppLanguage.ZH, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("en"))
        assertNull(AppLanguage.fromTag("system"))
        assertNull(AppLanguage.fromTag(null))
    }
}
