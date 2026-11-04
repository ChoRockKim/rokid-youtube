package com.chorockkim.rokidyoutube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFilterTest {
    @Test
    fun levelZeroChangesNothing() {
        for (v in listOf(0f, 0.3f, 1f)) assertEquals(v, PrivacyFilter.apply(0, v), 1e-6f)
    }

    @Test
    fun higherLevelsCrushMoreDarksAndDimTheBrightest() {
        for (level in 1..PrivacyFilter.MAX_LEVEL) {
            // Darks up to the black point go fully black: fewer lit pixels.
            assertEquals(0f, PrivacyFilter.apply(level, PrivacyFilter.blackPoint(level)), 1e-6f)
            // White is capped.
            assertEquals(PrivacyFilter.maxBrightness(level), PrivacyFilter.apply(level, 1f), 1e-6f)
            // Every level is darker than the one before for a mid grey.
            assertTrue(PrivacyFilter.apply(level, 0.5f) < PrivacyFilter.apply(level - 1, 0.5f))
        }
        // Strongest level still shows something.
        assertTrue(PrivacyFilter.apply(PrivacyFilter.MAX_LEVEL, 1f) >= 0.4f - 1e-6f)
    }
}
