package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test

class DetectionTakeTest {
    @Test fun fullThreeMinutesKeepEarlyNotesRestBoundariesAndTuningWithoutCropping() {
        val recorder = DetectionRecorder(415.0)
        var keep = true
        for (time in 128L..180_032L step 32) {
            val midi = when (time) { in 128L..736L -> 61; in 90_016L..90_656L -> 72; else -> null }
            keep = recorder.accept(PitchFrame(time, midi?.let { Music.frequency(it.toDouble(), 415.0) },
                if (midi != null) 0.99 else 0.0, 0.1))
            if (!keep) break
        }
        assertFalse(keep)
        val take = recorder.snapshot()
        assertEquals(180_000L, take.clip.durationMs)
        assertEquals(415.0, take.clip.a4, 0.0)
        assertEquals(listOf(NoteEvent(61, 128, 640), NoteEvent(72, 90_016, 672)), take.clip.notes)
        assertTrue(take.points.size < 2000)
        assertEquals(180_000L, take.points.last().timeMs)
        assertTrue(take.points.any { it.midi == null && it.timeMs in 768..90_000 })
        assertEquals(61.0, take.points.first().midi!!, 0.001)
        assertEquals(2_880_000, ToneSynthesis.render(take.clip).size)
    }

    @Test fun currentSustainedNoteAppearsBeforeItEndsAndShortSoundsCanKeepOnlyACurve() {
        val recorder = DetectionRecorder(440.0)
        for (time in 128L..1024L step 32) recorder.accept(PitchFrame(time, 440.0, 0.99, 0.1))
        assertEquals(listOf(NoteEvent(69, 128, 896)), recorder.snapshot().clip.notes)
        val short = DetectionRecorder(440.0)
        short.accept(PitchFrame(128, 261.63, 0.99, 0.1))
        assertTrue(short.snapshot().hasPitch)
        assertTrue(short.snapshot().clip.notes.isEmpty())
        val silent = DetectionRecorder(440.0)
        silent.accept(PitchFrame(128, 440.0, 0.4, 0.1))
        assertFalse(silent.snapshot().hasPitch)
        assertNull(silent.snapshot().points.single().midi)
    }

    @Test fun downsamplingKeepsImmediateSilenceAndRecoveryAndLastSample() {
        val recorder = DetectionRecorder(440.0)
        for (time in 128L..512L step 32) recorder.accept(PitchFrame(time,
            if (time == 160L) null else 440.0, if (time == 160L) 0.0 else 0.99, 0.1))
        val points = recorder.snapshot().points
        assertNull(points.first { it.timeMs == 160L }.midi)
        assertNotNull(points.first { it.timeMs == 192L }.midi)
        assertEquals(512L, points.last().timeMs)
        assertTrue(points.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs })
        assertThrows(IllegalArgumentException::class.java) {
            recorder.accept(PitchFrame(512, 440.0, 0.99, 0.1))
        }
    }

    @Test fun windowHasConstantScaleLiveRightEdgeAndBoundedReviewAndPlaybackPositions() {
        for (elapsed in listOf(128L, 5000L, 30_000L, 180_000L)) {
            val window = TimelineWindow.live(elapsed)
            assertEquals(1f, window.fraction(elapsed), 0f)
            assertEquals(0.1f, window.fraction(elapsed) - window.fraction(elapsed - 1000), 0.00001f)
        }
        assertEquals(0L, TimelineWindow.browse(-20, 3000).startMs)
        assertEquals(170_000L, TimelineWindow.browse(190_000, 180_000).startMs)
        assertEquals(85_000L, TimelineWindow.following(90_000, 180_000).startMs)
        assertEquals(0L, TimelineWindow.following(200, 180_000).startMs)
        assertEquals(170_000L, TimelineWindow.following(180_000, 180_000).startMs)
    }

    @Test fun malformedOrUnboundedStoredTakesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PitchPoint(0, Double.NaN, 0.99) }
        assertThrows(IllegalArgumentException::class.java) { PitchPoint(0, 60.0, 0.4) }
        assertThrows(IllegalArgumentException::class.java) {
            DetectionTake(MelodyClip(emptyList(), 1000), listOf(PitchPoint(1001, null, 0.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetectionTake(MelodyClip(emptyList(), 1000), listOf(PitchPoint(128, null, 0.0), PitchPoint(128, null, 0.0)))
        }
    }
}
