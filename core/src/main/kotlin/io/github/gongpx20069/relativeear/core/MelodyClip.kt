package io.github.gongpx20069.relativeear.core

data class MelodyClip(val notes: List<NoteEvent>, val durationMs: Long, val a4: Double = 440.0) {
    init {
        require(durationMs in 1..WINDOW_MS && a4 in 415.0..466.0)
        var end = 0L
        notes.forEach {
            require(it.midi in 0..127 && it.startMs >= end && it.durationMs > 0)
            require(it.durationMs <= durationMs - it.startMs)
            end = it.startMs + it.durationMs
        }
    }
    fun noteAt(positionMs: Long): Int? = notes.firstOrNull {
        positionMs >= it.startMs && positionMs < it.startMs + it.durationMs
    }?.midi

    companion object {
        const val WINDOW_MS = 60_000L
        fun recent(notes: List<NoteEvent>, elapsedMs: Long, a4: Double): MelodyClip {
            require(elapsedMs > 0)
            val origin = (elapsedMs - WINDOW_MS).coerceAtLeast(0)
            val cropped = notes.mapNotNull {
                require(it.midi in 0..127 && it.startMs >= 0 && it.durationMs > 0 &&
                    it.startMs <= Long.MAX_VALUE - it.durationMs)
                val start = maxOf(it.startMs, origin)
                val end = minOf(it.startMs + it.durationMs, elapsedMs)
                if (end > start) NoteEvent(it.midi, start - origin, end - start) else null
            }
            return MelodyClip(cropped, elapsedMs - origin, a4)
        }
    }
}
