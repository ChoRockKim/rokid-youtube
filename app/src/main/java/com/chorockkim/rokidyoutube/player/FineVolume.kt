package com.chorockkim.rokidyoutube.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Volume in even 5% steps although the system media volume has only a few, uneven steps.
 *
 * Why: one system step is not a fixed loudness change. On most devices the steps near the
 * bottom (and on some devices others too) jump several dB at once, so "one step up" can suddenly
 * be much louder. Mixing system steps with a plain linear player gain does not fix that either,
 * because loudness is logarithmic.
 *
 * So every 5% is the same loudness change in dB ([Curve]): the target dB is reached with the
 * lowest system step that is at least that loud, and the player gain takes off the rest. The
 * system's own dB per step comes from [AudioManager.getStreamVolumeDb] (API 28+), with a typical
 * curve as fallback.
 */
class FineVolume(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    /** Gain to apply to the player (0..1). */
    var gain: Float = prefs.getFloat(KEY_GAIN, 1f).coerceIn(0f, 1f)
        private set

    private fun curve(): Curve {
        val am = audio ?: return Curve.fallback(15)
        val max = am.getStreamMaxVolume(STREAM).coerceAtLeast(1)
        if (Build.VERSION.SDK_INT < 28) return Curve.fallback(max)
        val db = (0..max).map { i ->
            if (i == 0) Float.NEGATIVE_INFINITY
            else runCatching { am.getStreamVolumeDb(STREAM, i, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }
                .getOrDefault(Float.NaN)
        }
        return Curve.of(db) ?: Curve.fallback(max)
    }

    /** Current level in percent (multiple of [STEP]). */
    val percent: Int
        get() = curve().percentOf(audio?.getStreamVolume(STREAM) ?: 0, gain)

    /** Moves the level by [steps] x 5% and returns the new percent. */
    fun change(steps: Int): Int {
        val curve = curve()
        val target = curve.next(audio?.getStreamVolume(STREAM) ?: 0, gain, steps)
        val (index, g) = curve.split(target)
        // flags = 0: no system volume panel over the HUD.
        audio?.setStreamVolume(STREAM, index, 0)
        gain = g
        prefs.edit().apply {
            putFloat(KEY_GAIN, g)
            apply()
        }
        return target
    }

    /**
     * dB of each system step ([db] index 0 = mute) and the even dB scale 0..100% laid over it:
     * 5% = [lowDb] (6 dB below the quietest system step), 100% = the loudest step.
     */
    class Curve private constructor(private val db: List<Float>) {
        val max = db.size - 1
        private val highDb = db[max]
        private val lowDb = db[1] - 6f

        fun dbOfPercent(percent: Int): Float =
            lowDb + (highDb - lowDb) * (percent - STEP) / (100f - STEP)

        /** System step + player gain for [percent]. */
        fun split(percent: Int): Pair<Int, Float> {
            if (percent <= 0) return 0 to 0f
            val target = dbOfPercent(percent)
            val index = (1..max).firstOrNull { db[it] >= target - 0.01f } ?: max
            val gain = 10f.pow((target - db[index]) / 20f).coerceIn(0f, 1f)
            return index to gain
        }

        /** Exact (unrounded) percent of a system step + gain; 0 when muted. */
        private fun exactPercent(index: Int, gain: Float): Float {
            if (index <= 0 || gain <= 0f) return 0f
            val current = db[index.coerceAtMost(max)] + 20f * log10(gain)
            return (STEP + (current - lowDb) / (highDb - lowDb) * (100f - STEP)).coerceIn(STEP.toFloat(), 100f)
        }

        /** Inverse of [split], rounded to the nearest step. */
        fun percentOf(index: Int, gain: Float): Int =
            ((exactPercent(index, gain) / STEP).roundToInt() * STEP).coerceIn(0, 100)

        /**
         * The level [steps] presses away. Starting off the 5% grid (the system step was changed
         * elsewhere), the first press only goes to the next grid point in that direction, so a
         * press is never louder than one 5% step.
         */
        fun next(index: Int, gain: Float, steps: Int): Int {
            val p = exactPercent(index, gain) / STEP
            val onGrid = kotlin.math.abs(p - p.roundToInt()) < 0.05f
            val base = when {
                onGrid -> p.roundToInt()
                steps > 0 -> kotlin.math.floor(p).toInt()
                else -> kotlin.math.ceil(p).toInt()
            }
            return ((base + steps) * STEP).coerceIn(0, 100)
        }

        companion object {
            /** Null if the device reports unusable values (NaN, not increasing). */
            fun of(db: List<Float>): Curve? {
                if (db.size < 2) return null
                val steps = db.drop(1)
                if (steps.any { it.isNaN() || it.isInfinite() }) return null
                if (steps.zipWithNext().any { (a, b) -> b < a }) return null
                if (steps.last() - steps.first() < 6f) return null
                return Curve(db)
            }

            /** Typical Android media curve: about -50 dB at the first step to 0 dB at the top. */
            fun fallback(max: Int): Curve = Curve(
                listOf(Float.NEGATIVE_INFINITY) + (1..max).map { i -> -50f * (1f - (i - 1f) / (max - 1f).coerceAtLeast(1f)) }
            )
        }
    }

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val STEP = 5
        private const val KEY_GAIN = "volumeGain"
    }
}
