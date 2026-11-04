package com.chorockkim.rokidyoutube.player

import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FineVolumeTest {
    // An uneven 15-step curve like real devices: big jumps near the bottom, small ones on top.
    private val uneven = listOf(Float.NEGATIVE_INFINITY, -58f, -50f, -44f, -38f, -33f, -28f, -24f,
        -20f, -16f, -13f, -10f, -7f, -5f, -2f, 0f)

    private fun effectiveDb(curve: FineVolume.Curve, percent: Int, db: List<Float>): Float {
        val (index, gain) = curve.split(percent)
        return db[index] + 20f * log10(gain)
    }

    @Test
    fun everyStepIsTheSameSmallLoudnessChange() {
        val curve = FineVolume.Curve.of(uneven)!!
        val levels = (5..100 step 5).map { effectiveDb(curve, it, uneven) }
        val deltas = levels.zipWithNext { a, b -> b - a }
        // (0 - (-58 - 6)) / 19 steps = ~3.4 dB each, never a jump of a whole system step.
        deltas.forEach { assertEquals(deltas.first(), it, 0.05f) }
        assertTrue(deltas.first() < 3.5f)
        assertEquals(0f, levels.last(), 0.01f)
    }

    @Test
    fun readingBackGivesTheSamePercent() {
        val curve = FineVolume.Curve.of(uneven)!!
        for (p in 0..100 step 5) {
            val (index, gain) = curve.split(p)
            assertEquals(p, curve.percentOf(index, gain))
        }
    }

    @Test
    fun aSystemStepSetElsewhereIsReadAsItsLevel() {
        val curve = FineVolume.Curve.of(uneven)!!
        // Hardware keys outside the app left the system at step 8 (-20 dB), player gain 1.
        val (index, gain) = curve.split(curve.next(8, 1f, 1))
        val before = uneven[8]
        val after = uneven[index] + 20f * log10(gain)
        assertTrue("one press = small change, was ${after - before} dB", after > before && after - before <= 3.5f)
        val (downIndex, downGain) = curve.split(curve.next(8, 1f, -1))
        val down = uneven[downIndex] + 20f * log10(downGain)
        assertTrue("one press down = small change, was ${before - down} dB", down < before && before - down <= 3.5f)
    }

    @Test
    fun unusableDeviceCurvesAreRejected() {
        assertNull(FineVolume.Curve.of(listOf(Float.NEGATIVE_INFINITY, Float.NaN, 0f)))
        assertNull(FineVolume.Curve.of(listOf(Float.NEGATIVE_INFINITY, -10f, -20f)))
        assertNotNull(FineVolume.Curve.fallback(15))
    }
}
