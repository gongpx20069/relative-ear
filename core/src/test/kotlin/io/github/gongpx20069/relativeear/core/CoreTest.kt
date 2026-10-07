package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class CoreTest {
    @Test fun musicConversionsAndOctaves() {
        assertEquals(440.0, Music.frequency(69.0), 0.0001)
        assertEquals(69.0, Music.midi(442.0, 442.0), 0.0001)
        assertEquals("C4", Music.name(60))
        assertEquals("B3", Music.name(59))
        assertEquals("B-2", Music.name(-1))
        assertEquals(1200.0, Music.error(72.0, 60, false), 0.0001)
        assertEquals(0.0, Music.error(72.0, 60, true), 0.0001)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFrequency() { Music.midi(0.0) }

    @Test fun generatedQuestionsMatchPlayback() {
        val random = Random(42)
        repeat(1000) {
            for (mode in QuestionMode.entries) for (down in listOf(false, true)) {
                val question = Questions.create(mode, down, random)
                assertTrue(question.root in 48..60)
                assertTrue(question.target in 36..72)
                assertEquals(listOf(question.target), question.playback().last())
                if (mode == QuestionMode.INTERVAL) {
                    assertEquals(question.answer, abs(question.target - question.root))
                } else assertEquals(Music.major[question.answer], question.target - question.root)
            }
        }
    }

    private fun waveform(frequency: Double, harmonics: Boolean = false): FloatArray = FloatArray(2048) {
        val phase = 2 * PI * frequency * it / 16_000
        (0.3 * sin(phase) + if (harmonics) 0.15 * sin(2 * phase) + 0.1 * sin(3 * phase) else 0.0).toFloat()
    }
    @Test fun cleanPitchMeetsPrecisionAndCoverageTargets() {
        val detector = PitchDetector()
        val frequencies = (36..82).map { Music.frequency(it.toDouble()) }.filter { it in 65.0..1000.0 } +
            listOf(65.0, 1000.0, 442.0)
        var valid = 0
        for (frequency in frequencies) for (harmonics in listOf(false, true)) {
            val result = detector.detect(waveform(frequency, harmonics), 0)
            val detected = result.frequency
            assertNotNull("Missing pitch at $frequency", detected)
            if (detected != null) {
                valid++
                val error = abs((Music.midi(detected) - Music.midi(frequency)) * 100)
                assertTrue("$frequency detected as $detected ($error cents)", error <= 10)
            }
        }
        assertEquals(frequencies.size * 2, valid)
    }
    @Test fun silenceAndWhiteNoiseAreRejected() {
        val detector = PitchDetector()
        assertNull(detector.detect(FloatArray(2048), 0).frequency)
        assertNull(detector.detect(FloatArray(2048) { 0.5f }, 0).frequency)
        val random = Random(71)
        val segmenter = NoteSegmenter()
        var events = 0
        repeat(1875) {
            val frame = detector.detect(FloatArray(2048) { random.nextFloat() - 0.5f }, it * 32L)
            if (segmenter.accept(frame) != null) events++
        }
        assertEquals(0, events)
    }
    @Test fun sixtySecondsOfSilenceNeverProducesStablePitch() {
        val detector = PitchDetector()
        val segmenter = NoteSegmenter()
        val scorer = SingingScorer(60)
        val silence = FloatArray(2048)
        repeat(1875) {
            val frame = detector.detect(silence, it * 32L)
            assertNull(frame.frequency)
            assertNull(segmenter.accept(frame))
            assertNull(scorer.accept(frame))
        }
        assertNull(segmenter.flush())
    }
    private fun frame(time: Long, midi: Double?, a4: Double = 440.0): PitchFrame =
        PitchFrame(time, midi?.let { Music.frequency(it, a4) }, if (midi == null) 0.0 else 0.99, 0.2)

    @Test fun scorerUsesElapsedTimeAndFirstStableAnswer() {
        val scorer = SingingScorer(64)
        for (time in 0L..384L step 32) assertNull(scorer.accept(frame(time, 64.1)))
        val result = scorer.accept(frame(416, 64.1))!!
        assertTrue(result.correct)
        assertEquals(10.0, result.cents, 0.001)
        assertEquals(result, scorer.accept(frame(448, 60.0)))
    }
    @Test fun scorerDoesNotAcceptSparseOrUnstableFrames() {
        val sparse = SingingScorer(64)
        assertNull(sparse.accept(frame(0, 64.0)))
        assertNull(sparse.accept(frame(1000, 64.0)))
        val unstable = SingingScorer(64)
        for (time in 0L..800L step 32) {
            assertNull(unstable.accept(frame(time, if ((time / 32) % 2 == 0L) 63.0 else 65.0)))
        }
    }
    @Test fun scorerRespectsToleranceAndOctaveMode() {
        fun score(midi: Double, ignore: Boolean): SingingResult {
            val scorer = SingingScorer(60, tolerance = 35, ignoreOctave = ignore)
            var result: SingingResult? = null
            for (time in 0L..448L step 32) result = scorer.accept(frame(time, midi))
            return result!!
        }
        assertTrue(score(60.349, false).correct)
        assertTrue(score(60.35, false).correct)
        assertTrue(score(59.65, false).correct)
        assertFalse(score(60.351, false).correct)
        assertFalse(score(72.0, false).correct)
        assertTrue(score(72.0, true).correct)
    }
    @Test fun segmenterSeparatesNotesAndRepeatedNotesAfterSilence() {
        val segmenter = NoteSegmenter()
        val events = mutableListOf<NoteEvent>()
        for (time in 0L..320L step 32) segmenter.accept(frame(time, 60.0))?.let(events::add)
        for (time in 352L..512L step 32) segmenter.accept(frame(time, null))?.let(events::add)
        for (time in 544L..864L step 32) segmenter.accept(frame(time, 60.0))?.let(events::add)
        for (time in 896L..1216L step 32) segmenter.accept(frame(time, 64.0))?.let(events::add)
        segmenter.flush()?.let(events::add)
        assertEquals(listOf(60, 60, 64), events.map { it.midi })
        assertTrue(events.all { it.durationMs >= 96 })
        assertEquals(0L, events.first().startMs)
    }
    @Test fun segmenterRejectsShortBlips() {
        val segmenter = NoteSegmenter()
        segmenter.accept(frame(0, 60.0))
        segmenter.accept(frame(32, 60.0))
        segmenter.accept(frame(64, null))
        assertNull(segmenter.flush())
    }
}
