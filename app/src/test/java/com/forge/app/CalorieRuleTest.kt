package com.forge.app

import com.forge.app.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieRuleTest {
    /** 2,700 target: the window is 2,430 to 3,105. */
    @Test fun windowEdges() {
        assertFalse(Stats.kcalHit(2429, 2700))
        assertTrue(Stats.kcalHit(2430, 2700))
        assertTrue(Stats.kcalHit(3105, 2700))
        assertFalse(Stats.kcalHit(3106, 2700))
    }

    /** The day from the bug report: 3,322 of 2,700 is over, not a hit. */
    @Test fun overIsNotAHit() {
        assertTrue(Stats.kcalOver(3322, 2700))
        assertFalse(Stats.kcalHit(3322, 2700))
        assertEquals(217, Stats.kcalOverBy(3322, 2700))
        assertFalse(Stats.kcalOver(3000, 2700))
    }
}
