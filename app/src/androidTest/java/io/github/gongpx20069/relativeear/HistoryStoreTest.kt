package io.github.gongpx20069.relativeear

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.TrainingSetup
import org.junit.Assert.*
import org.junit.Test

class HistoryStoreTest {
    @Test fun preservesCustomTrainingAndExplicitOctavePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HistoryStore(context)
        val originalSettings = store.settings()
        val originalTraining = store.training()
        try {
            store.clear()
            val training = TrainingSetup(listOf(64, 67, 72), AnswerNotation.NOTE_NAME, 120)
            store.saveTraining(training)
            store.saveSettings(Settings(ignoreOctave = true))
            assertEquals(training, store.training())
            assertTrue(store.settings().ignoreOctave)
            store.save("test", Attempt("fixed_note", 60, 72, 72, true, training = training), Settings())
            assertEquals(training, store.history().attempts.single().training)
        } finally {
            store.saveSettings(originalSettings)
            store.saveTraining(originalTraining)
            store.clear()
            store.close()
        }
    }
    @Test fun additiveMigrationPreservesLegacyRowsAndLeavesTheirMetadataUnknown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HistoryStore(context)
        val db = SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE attempts (id INTEGER PRIMARY KEY, mode TEXT, target INTEGER)")
            db.execSQL("INSERT INTO attempts VALUES (1, 'degree', 64)")
            store.onUpgrade(db, 1, 2)
            db.rawQuery("SELECT mode, target, training_notes, notation, bpm FROM attempts", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("degree", it.getString(0))
                assertEquals(64, it.getInt(1))
                assertTrue(it.isNull(2) && it.isNull(3) && it.isNull(4))
                assertFalse(it.moveToNext())
            }
        } finally {
            db.close()
            store.close()
        }
    }
    @Test fun preservesNullableTimeoutAndSettingsSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HistoryStore(context)
        try {
            store.clear()
            store.save("test", Attempt("sing", 60, 64, 4, false, timeout = true), Settings())
            store.save("test", Attempt("interval", 60, 67, 7, true), Settings())
            val history = store.history()
            assertEquals(2, history.total)
            assertEquals(1, history.correct)
            assertTrue(history.attempts.last().timeout)
            assertNull(history.attempts.last().cents)
            store.clear()
            assertEquals(0, store.history().total)
        } finally {
            store.close()
        }
    }
}
