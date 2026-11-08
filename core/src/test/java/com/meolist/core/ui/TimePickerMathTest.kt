package com.meolist.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimePickerMathTest {
    @Test
    fun minutesContainEveryValueFromZeroThroughFiftyNine() {
        assertEquals(60, timePickerMinutes.size)
        assertEquals((0..59).toList(), timePickerMinutes)
        listOf(0, 1, 7, 29, 58, 59).forEach { minute ->
            assertEquals(minute, parseTimePickerMinute(minute.toString().padStart(2, '0')))
        }
    }

    @Test
    fun twentyFourHourWheelMapsEveryHourDirectly() {
        assertEquals((0..23).toList(), timePickerHours(true))
        (0..23).forEach { hour ->
            assertEquals(hour, timePickerHourIndex(hour, true))
            assertEquals(hour, timePickerHourForIndex(hour, true, isPm = hour >= 12))
        }
    }

    @Test
    fun twelveHourWheelKeepsMidnightAndNoonDistinct() {
        assertEquals(listOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), timePickerHours(false))
        assertEquals(0, timePickerHourForIndex(0, false, isPm = false))
        assertEquals(12, timePickerHourForIndex(0, false, isPm = true))
        assertEquals(1, timePickerHourForIndex(1, false, isPm = false))
        assertEquals(13, timePickerHourForIndex(1, false, isPm = true))
        assertEquals(11, timePickerHourForIndex(11, false, isPm = false))
        assertEquals(23, timePickerHourForIndex(11, false, isPm = true))
    }

    @Test
    fun textParsingRejectsValuesOutsideTheVisibleRanges() {
        assertEquals(0, parseTimePickerHour("00", true))
        assertEquals(23, parseTimePickerHour("23", true))
        assertNull(parseTimePickerHour("24", true))
        assertEquals(1, parseTimePickerHour("1", false))
        assertEquals(12, parseTimePickerHour("12", false))
        assertNull(parseTimePickerHour("0", false))
        assertNull(parseTimePickerHour("13", false))

        assertEquals(59, parseTimePickerMinute("59"))
        assertNull(parseTimePickerMinute("60"))
        assertNull(parseTimePickerMinute(""))
    }
}
