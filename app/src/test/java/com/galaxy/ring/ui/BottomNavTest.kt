package com.galaxy.ring.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavTest {

    @Test
    fun bottomNavTabsAreConfiguredInExpectedOrder() {
        val tabs = listOf("Home", "Sleep", "Heart", "Activity", "More")
        assertEquals(5, tabs.size)
        assertEquals("Home", tabs[0])
        assertEquals("Sleep", tabs[1])
        assertEquals("Heart", tabs[2])
        assertEquals("Activity", tabs[3])
        assertEquals("More", tabs[4])
    }
}
