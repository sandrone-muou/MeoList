package com.meolist.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoreCardLayoutTest {

    @Test
    fun compactPhonesUseShorterTiles() {
        assertEquals(128f, moreCardHeight(compact = true, fontScale = 1.0f).value)
    }

    @Test
    fun largeFontsKeepRoomForTwoLineDescriptions() {
        assertEquals(152f, moreCardHeight(compact = true, fontScale = 1.3f).value)
        assertEquals(152f, moreCardHeight(compact = true, fontScale = 1.16f).value)
    }

    @Test
    fun tabletsKeepTheRegularHeight() {
        assertEquals(152f, moreCardHeight(compact = false, fontScale = 1.0f).value)
    }
}
