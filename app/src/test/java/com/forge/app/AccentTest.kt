package com.forge.app

import com.forge.app.domain.Accent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccentTest {
    @Test fun unknownOrMissingFallsBackToGreen() {
        assertEquals(Accent.GREEN, Accent.of(null))
        assertEquals(Accent.GREEN, Accent.of("ORANGE"))
        assertEquals(Accent.PINK, Accent.of("PINK"))
    }

    @Test fun missesNeverLookLikeTheAccent() {
        Accent.entries.forEach { assertNotEquals(it.name, it.argb, it.miss) }
        assertNotEquals(Accent.RED_MISS, Accent.RED.miss)
    }

    @Test fun oldSettingsWithoutAColorStayGreenAndGetAsked() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(com.forge.app.data.Settings.serializer(), """{"onboarded":true}""")
        assertEquals("GREEN", s.accent)
        assertEquals(false, s.accentChosen)
    }
}
