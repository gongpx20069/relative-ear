package io.github.gongpx20069.relativeear.core

import kotlin.math.abs
import kotlin.math.roundToInt

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    return if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    else sorted[sorted.size / 2]
}

data class SingingResult(val correct: Boolean, val cents: Double, val deviation: Double)

class SingingScorer(
    private val target: Int,
    private val a4: Double = 440.0,
    private val tolerance: Int = 35,
    private val ignoreOctave: Boolean = false,
) {
    private val frames = ArrayDeque<Pair<Long, Double?>>()
    private var result: SingingResult? = null
    private var lastTime = Long.MIN_VALUE
    init { require(tolerance in 1..100) }
    fun accept(frame: PitchFrame): SingingResult? {
        require(frame.timeMs > lastTime) { "Pitch timestamps must increase" }
        lastTime = frame.timeMs
        result?.let { return it }
        val midi = frame.frequency?.takeIf { frame.confidence >= 0.85 }?.let { Music.midi(it, a4) }
        frames.addLast(frame.timeMs to midi)
        while (frames.size > 1 && frames[1].first <= frame.timeMs - 400) frames.removeFirst()
        if (frames.last().first - frames.first().first < 400) return null
        var validDuration = 0L
        val pitches = mutableListOf<Double>()
        for (index in 0 until frames.size - 1) {
            val entry = frames[index]
            val duration = frames[index + 1].first - entry.first
            if (entry.second != null && duration <= 100) {
                validDuration += duration
                pitches.add(entry.second!!)
            }
        }
        val duration = frames.last().first - frames.first().first
        if (validDuration.toDouble() / duration < 0.8 || pitches.isEmpty()) return null
        val center = median(pitches)
        val deviation = median(pitches.map { abs(it - center) * 100 })
        if (deviation > 20 || pitches.count { abs(it - center) * 100 <= 20 }.toDouble() / pitches.size < 0.8) return null
        val cents = Music.error(center, target, ignoreOctave)
        return SingingResult(abs(cents) <= tolerance + 1e-7, cents, deviation).also { result = it }
    }
}

data class NoteEvent(val midi: Int, val startMs: Long, val durationMs: Long)

class NoteSegmenter(private val a4: Double = 440.0) {
    private var active: Int? = null
    private var start = 0L
    private var lastValid = 0L
    private var pending: Int? = null
    private var pendingStart = 0L

    fun accept(frame: PitchFrame): NoteEvent? {
        val midi = frame.frequency?.takeIf { frame.confidence >= 0.85 }?.let { Music.midi(it, a4) }
        if (midi == null) {
            pending = null
            return if (active != null && frame.timeMs - lastValid >= 120) finish(lastValid + 32) else null
        }
        lastValid = frame.timeMs
        val note = midi.roundToInt()
        if (active == null) {
            if (pending != note) { pending = note; pendingStart = frame.timeMs }
            if (frame.timeMs - pendingStart >= 96) {
                active = note
                start = pendingStart
                pending = null
            }
            return null
        }
        if (abs(midi - active!!) <= 0.65) {
            pending = null
            return null
        }
        if (pending != note) { pending = note; pendingStart = frame.timeMs }
        if (frame.timeMs - pendingStart < 96) return null
        val nextStart = pendingStart
        val event = finish(nextStart)
        active = note
        start = nextStart
        return event
    }

    fun flush(): NoteEvent? = finish(lastValid + 32)

    private fun finish(end: Long): NoteEvent? {
        val note = active
        active = null
        pending = null
        return note?.let { NoteEvent(it, start, (end - start).coerceAtLeast(0)) }?.takeIf { it.durationMs >= 96 }
    }
}
