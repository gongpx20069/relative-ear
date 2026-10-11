package io.github.gongpx20069.relativeear.core

data class PitchPoint(val timeMs: Long, val midi: Double?, val confidence: Double) {
    init {
        require(timeMs in 0..MelodyClip.MAX_DURATION_MS && confidence.isFinite() && confidence in 0.0..1.0)
        require(midi == null || (midi.isFinite() && midi in 0.0..127.0 && confidence >= 0.85))
    }
}

data class DetectionTake(val clip: MelodyClip, val points: List<PitchPoint>) {
    init {
        require(points.size <= MAX_POINTS && clip.notes.size <= MAX_NOTES)
        require(points.all { it.timeMs <= clip.durationMs })
        require(points.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs })
    }
    val hasPitch: Boolean get() = clip.notes.isNotEmpty() || points.any { it.midi != null }

    companion object {
        const val MAX_POINTS = 6000
        const val MAX_NOTES = 2000
    }
}

class DetectionRecorder(private val a4: Double) {
    private val segmenter = NoteSegmenter(a4)
    private val ended = mutableListOf<NoteEvent>()
    private val sampled = mutableListOf<PitchPoint>()
    private var last: PitchPoint? = null
    var elapsedMs: Long = 0
        private set
    init { require(a4 in 415.0..466.0) }

    fun accept(frame: PitchFrame): Boolean {
        require(frame.timeMs > elapsedMs && frame.confidence.isFinite() && frame.confidence in 0.0..1.0)
        require(frame.frequency == null || (frame.frequency.isFinite() && frame.frequency > 0))
        val time = frame.timeMs.coerceAtMost(MelodyClip.MAX_DURATION_MS)
        val bounded = frame.copy(timeMs = time)
        elapsedMs = time
        segmenter.accept(bounded)?.let { ended.add(it) }
        val midi = frame.frequency?.takeIf { frame.confidence >= 0.85 }?.let { Music.midi(it, a4) }
        val point = PitchPoint(time, midi, frame.confidence)
        val previous = sampled.lastOrNull()
        if (previous == null || time - previous.timeMs >= 96 || (previous.midi == null) != (midi == null)) {
            if (previous == null || time - previous.timeMs >= 32) {
                sampled.add(point)
            } else if (midi == null && previous.midi != null) {
                // Short device reads must not create an unbounded number of gap markers.
                sampled[sampled.lastIndex] = previous.copy(midi = null, confidence = point.confidence)
            }
        }
        last = point
        return time < MelodyClip.MAX_DURATION_MS
    }

    fun snapshot(): DetectionTake {
        require(elapsedMs > 0)
        val events = ended + listOfNotNull(segmenter.current(elapsedMs))
        val points = if (last != null && sampled.lastOrNull()?.timeMs != last?.timeMs) sampled + checkNotNull(last) else sampled.toList()
        val notes = events.mapNotNull {
            val end = minOf(it.startMs + it.durationMs, elapsedMs)
            if (end > it.startMs) it.copy(durationMs = end - it.startMs) else null
        }
        return DetectionTake(MelodyClip(notes, elapsedMs, a4), points)
    }
}

data class TimelineWindow(val startMs: Long) {
    val endMs: Long get() = startMs + WIDTH_MS
    fun fraction(timeMs: Long): Float = (timeMs - startMs).toFloat() / WIDTH_MS
    companion object {
        const val WIDTH_MS = 10_000L
        fun live(elapsedMs: Long) = TimelineWindow(elapsedMs - WIDTH_MS)
        fun browse(startMs: Long, durationMs: Long) =
            TimelineWindow(startMs.coerceIn(0, (durationMs - WIDTH_MS).coerceAtLeast(0)))
        fun following(positionMs: Long, durationMs: Long) =
            browse(positionMs - WIDTH_MS / 2, durationMs)
    }
}
