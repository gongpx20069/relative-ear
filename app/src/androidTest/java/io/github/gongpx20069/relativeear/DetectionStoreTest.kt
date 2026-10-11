package io.github.gongpx20069.relativeear

import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import io.github.gongpx20069.relativeear.core.DetectionTake
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.NoteEvent
import io.github.gongpx20069.relativeear.core.PitchPoint
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DetectionStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun take() = DetectionTake(MelodyClip(listOf(NoteEvent(61, 128, 640),
        NoteEvent(72, 90_000, 800)), 180_000, 415.0),
        listOf(PitchPoint(128, 61.2, 0.99), PitchPoint(768, null, 0.0),
            PitchPoint(90_000, 72.0, 0.97), PitchPoint(90_800, null, 0.0),
            PitchPoint(180_000, null, 0.0)))

    @Test fun reopenedClipKeepsFullDurationCurvePausesTuningAndRenameAndDeleteAreIndependentOfScores() {
        val name = "detection-roundtrip-test.db"
        context.deleteDatabase(name)
        try {
            HistoryStore(context, name).use {
                it.save("practice", Attempt("fixed_note", 60, 60, 60, true), Settings())
                it.saveDetection("take-one", 1234, take())
            }
            HistoryStore(context, name).use {
                val summary = it.detections().single()
                assertEquals(180_000L, summary.durationMs)
                assertEquals(61, summary.lowest)
                assertEquals(72, summary.highest)
                assertEquals(2, summary.noteCount)
                assertEquals(take().clip.notes, summary.preview)
                assertEquals(take(), it.detection(summary.id).take)
                val history = it.history()
                it.renameDetection(summary.id, "  Melody 'C#'  ")
                assertEquals("Melody 'C#'", it.detection(summary.id).summary.title)
                assertThrows(IllegalArgumentException::class.java) { it.renameDetection(summary.id, " ") }
                assertThrows(IllegalArgumentException::class.java) { it.renameDetection(summary.id, "a".repeat(81)) }
                assertEquals(history, it.history())
                it.clear()
                assertEquals(0, it.history().total)
                assertEquals(take(), it.detection(summary.id).take)
                it.save("practice-two", Attempt("sing_fixed", 60, 64, 64, true), Settings())
                it.deleteDetection(summary.id)
                assertTrue(it.detections().isEmpty())
                assertEquals(1, it.history().total)
                for (table in listOf("detection_notes", "detection_points")) {
                    it.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                        cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
                    }
                }
                assertThrows(IOException::class.java) { it.detection(summary.id) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun partialWriteRollsBackAndDuplicateSaveCannotOverwriteAnExistingClip() {
        val name = "detection-atomic-test.db"
        context.deleteDatabase(name)
        try {
            HistoryStore(context, name).use { store ->
                store.saveDetection("first", 1, take())
                assertThrows(SQLiteException::class.java) { store.saveDetection("first", 2, take()) }
                assertEquals(1L, store.detection("first").summary.timeMs)
                store.writableDatabase.execSQL("""CREATE TRIGGER fail_point_write
                    BEFORE INSERT ON detection_points WHEN NEW.take_id = 'failed'
                    BEGIN SELECT RAISE(ABORT, 'Injected point write failure'); END""".trimIndent())
                assertThrows(SQLiteException::class.java) { store.saveDetection("failed", 3, take()) }
                assertEquals(listOf("first"), store.detections().map { it.id })
                store.readableDatabase.rawQuery("SELECT COUNT(*) FROM detection_notes WHERE take_id = ?", arrayOf("failed")).use {
                    it.moveToFirst(); assertEquals(0, it.getInt(0))
                }
                assertEquals(take(), store.detection("first").take)
                store.writableDatabase.execSQL("UPDATE detection_points SET midi = -1 WHERE take_id = 'first' AND position = 0")
                assertThrows(IOException::class.java) { store.detection("first") }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionTwoUpgradeCreatesOnlyAdditiveTablesAndPreservesPracticeAndVoiceSettings() {
        val name = "detection-v2-migration-test.db"
        context.deleteDatabase(name)
        try {
            val voice = HistoryStore(context).use { it.pianoVoice() }
            HistoryStore(context, name).use {
                it.save("existing", Attempt("fixed_note", 60, 64, 64, true), Settings())
                for (table in listOf("detection_points", "detection_notes", "detection_takes")) it.writableDatabase.execSQL("DROP TABLE $table")
                it.writableDatabase.version = 2
            }
            HistoryStore(context, name).use {
                assertEquals(3, it.readableDatabase.version)
                assertEquals(1, it.history().total)
                assertEquals("existing", it.history().practices.single().session)
                assertEquals(voice, it.pianoVoice())
                assertTrue(it.detections().isEmpty())
                it.saveDetection("new", 1234, take())
                assertEquals(take(), it.detection("new").take)
                assertEquals(1, it.history().correct)
            }
        } finally { context.deleteDatabase(name) }
    }
}
