package io.github.gongpx20069.relativeear.core

import kotlin.math.sqrt
import kotlin.math.pow

data class PitchFrame(val timeMs: Long, val frequency: Double?, val confidence: Double, val rms: Double)

class PitchDetector(
    private val sampleRate: Int = 16_000,
    private val windowSize: Int = 2048,
) {
    private val minLag = (sampleRate / 1000.0).toInt()
    private val maxLag = (sampleRate / 65.0).toInt() + 1
    private val differences = DoubleArray(maxLag + 1)
    init {
        require(sampleRate >= 8000 && windowSize > maxLag * 2)
    }

    fun detect(samples: FloatArray, timeMs: Long): PitchFrame {
        require(samples.size == windowSize)
        var mean = 0.0
        for (value in samples) mean += value
        mean /= windowSize
        var energy = 0.0
        for (value in samples) energy += (value - mean) * (value - mean)
        val rms = sqrt(energy / windowSize)
        if (rms < 0.008) return PitchFrame(timeMs, null, 0.0, rms)

        val comparisonSize = windowSize / 2
        differences[0] = 1.0
        var sum = 0.0
        for (lag in 1..maxLag) {
            var difference = 0.0
            for (index in 0 until comparisonSize) {
                val delta = (samples[index] - samples[index + lag]).toDouble()
                difference += delta * delta
            }
            sum += difference
            differences[lag] = if (sum > 0) difference * lag / sum else 1.0
        }
        var lag = minLag
        while (lag < maxLag) {
            if (differences[lag] < 0.15) {
                while (lag + 1 <= maxLag && differences[lag + 1] < differences[lag]) lag++
                val left = differences[lag - 1]
                val center = differences[lag]
                val right = differences.getOrElse(lag + 1) { center }
                val divisor = left - 2 * center + right
                val correction = if (divisor != 0.0) (0.5 * (left - right) / divisor).coerceIn(-1.0, 1.0) else 0.0
                val frequency = sampleRate / (lag + correction)
                val boundaryRatio = 2.0.pow(10.0 / 1200)
                return PitchFrame(timeMs, frequency.takeIf { it in (65.0 / boundaryRatio)..(1000.0 * boundaryRatio) }, 1 - center, rms)
            }
            lag++
        }
        return PitchFrame(timeMs, null, 0.0, rms)
    }
}
