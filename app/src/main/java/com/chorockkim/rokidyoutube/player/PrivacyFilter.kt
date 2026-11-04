package com.chorockkim.rokidyoutube.player

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * "가림": makes the video leak less light to the front of the waveguide.
 *
 * What people in front see is light from lit pixels, so each level both
 * - crushes the dark parts to black (black point [blackPoint]): fewer lit pixels, and
 * - lowers the brightest output ([maxBrightness]): the remaining pixels are dimmer.
 * The wearer's eyes adapt to the dimmer picture; the outward glow drops much more.
 *
 * out = (in - blackPoint) / (1 - blackPoint) * maxBrightness, clamped to 0..1 per channel.
 */
object PrivacyFilter {
    const val MAX_LEVEL = 5

    fun blackPoint(level: Int) = 0.08f * level.coerceIn(0, MAX_LEVEL)
    fun maxBrightness(level: Int) = 1f - 0.12f * level.coerceIn(0, MAX_LEVEL)

    /** Output (0..1) for an input channel value (0..1); what the color matrix computes. */
    fun apply(level: Int, value: Float): Float {
        val bp = blackPoint(level)
        return ((value - bp) / (1f - bp) * maxBrightness(level)).coerceIn(0f, 1f)
    }

    /** Null for level 0 (no filter at all, no extra GPU work). */
    fun colorFilter(level: Int): ColorMatrixColorFilter? {
        if (level <= 0) return null
        val bp = blackPoint(level)
        val scale = maxBrightness(level) / (1f - bp)
        val offset = -bp * scale * 255f
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, offset,
                    0f, scale, 0f, 0f, offset,
                    0f, 0f, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}
