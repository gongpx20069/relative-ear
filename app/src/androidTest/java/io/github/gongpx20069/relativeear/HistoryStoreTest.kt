package io.github.gongpx20069.relativeear

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.TrainingSetup
import io.github.gongpx20069.relativeear.core.ToneVoice
import org.junit.Assert.*
import org.junit.Test

class HistoryStoreTest {
    @Test fun pianoVoicePersistsIndependentlyOfTrainingSettingsAndScores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = HistoryStore(context).use { it.pianoVoice() }
        try {
            val settings = HistoryStore(context).use { it.settings() }
            val training = HistoryStore(context).use { it.training() }
            val count = HistoryStore(context).use { it.history().total }
            for (voice in ToneVoice.entries) {
                HistoryStore(context).use { it.savePianoVoice(voice) }
                HistoryStore(context).use {
                    assertEquals(voice, it.pianoVoice())
                    assertEquals(settings, it.settings())
                    assertEquals(training, it.training())
                    assertEquals(count, it.history().total)
                }
            }
        } finally {
            HistoryStore(context).use { it.savePianoVoice(original) }
        }
    }
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
            val practice = store.history().practices.single()
            assertEquals(training, practice.training)
            assertEquals(training, store.attempts(practice).single().training)
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
            assertEquals(2, history.practiceCount)
            val timeout = store.attempts(history.practices.first { it.mode == "sing" }).single()
            assertTrue(timeout.timeout)
            assertNull(timeout.cents)
            store.clear()
            assertEquals(0, store.history().total)
        } finally {
            store.close()
        }
    }
    @Test fun groupsRoundsAndModesWithoutLosingAnswerDetailsOrPartialPractices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            HistoryStore(context, "history-grouping-test.db").use { store ->
                store.clear()
                val training = TrainingSetup()
                val answers = (0 until 10).map { index ->
                    Attempt("fixed_note", 60, training.notes[index % 3], 60, index < 6,
                        reactionMs = index * 1000L, replays = index, timeMs = 10_000L - index,
                        training = training)
                }
                answers.forEach { store.save("round-one", it, Settings()) }
                store.save("round-two", Attempt("fixed_note", 60, 64, 62, false, training = training), Settings())
                store.save("round-two", Attempt("sing_fixed", 60, 64, 64, true, cents = -12.0,
                    training = training), Settings())
                val history = store.history()
                assertEquals(3, history.practiceCount)
                assertEquals(12, history.total)
                assertEquals(7, history.correct)
                assertEquals(listOf("sing_fixed", "fixed_note", "fixed_note"), history.practices.map { it.mode })
                val complete = history.practices.last()
                assertEquals(10, complete.total)
                assertEquals(6, complete.correct)
                assertEquals(60, complete.accuracy)
                assertEquals(10_000L, complete.timeMs)
                assertEquals(training, complete.training)
                assertEquals(answers, store.attempts(complete))
                assertEquals(1, history.practices[1].total)
                assertEquals(-12.0, store.attempts(history.practices.first()).single().cents!!, 0.0)
                store.clear()
                assertEquals(History(), store.history())
                assertTrue(store.attempts(complete).isEmpty())
            }
        } finally {
            context.deleteDatabase("history-grouping-test.db")
        }
    }
    @Test fun limitsRecentPracticesRatherThanIndividualNotesAndLoadsUntruncatedDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            HistoryStore(context, "history-limit-test.db").use { store ->
                store.clear()
                store.writableDatabase.beginTransaction()
                val longPractice: PracticeSummary
                try {
                    repeat(125) { index ->
                        store.save("long-legacy-round", Attempt("degree", 60, 64, 2, false,
                            replays = index, timeMs = index.toLong()), Settings())
                    }
                    longPractice = store.history().practices.single()
                    repeat(105) { round ->
                        repeat(3) {
                            store.save("round-$round", Attempt("fixed_note", 60, 60, 60, true), Settings())
                        }
                    }
                    store.writableDatabase.setTransactionSuccessful()
                } finally {
                    store.writableDatabase.endTransaction()
                }
                val history = store.history()
                assertEquals(106, history.practiceCount)
                assertEquals(440, history.total)
                assertEquals(315, history.correct)
                assertEquals(100, history.practices.size)
                assertEquals("round-104", history.practices.first().session)
                assertEquals("round-5", history.practices.last().session)
                assertTrue(history.practices.all { it.total == 3 && it.correct == 3 })
                assertEquals((0 until 125).toList(), store.attempts(longPractice).map { it.replays })
            }
        } finally {
            context.deleteDatabase("history-limit-test.db")
        }
    }
    @Test fun upgradedVersionOneDatabaseKeepsExistingSessionGroupsAndNullableMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("""CREATE TABLE attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT, session TEXT NOT NULL, mode TEXT NOT NULL,
                root INTEGER NOT NULL, target INTEGER NOT NULL, answer INTEGER NOT NULL,
                correct INTEGER NOT NULL, timeout INTEGER NOT NULL, cents REAL,
                reaction_ms INTEGER NOT NULL, replays INTEGER NOT NULL, time_ms INTEGER NOT NULL,
                a4 INTEGER NOT NULL, tolerance INTEGER NOT NULL, ignore_octave INTEGER NOT NULL,
                scoring_version INTEGER NOT NULL
            )""".trimIndent())
            repeat(2) { index ->
                db.execSQL("""INSERT INTO attempts (
                    session, mode, root, target, answer, correct, timeout, cents,
                    reaction_ms, replays, time_ms, a4, tolerance, ignore_octave, scoring_version
                ) VALUES ('legacy', 'degree', 60, 64, 2, ?, 0, NULL, 1000, 0, ?, 440, 35, 0, 1)""",
                    arrayOf<Any>(index, index * 1000L))
            }
            db.version = 1
        }
        try {
            HistoryStore(context, name).use { store ->
                val history = store.history()
                assertEquals(1, history.practiceCount)
                assertEquals(2, history.total)
                assertEquals(1, history.correct)
                val legacy = history.practices.single()
                assertEquals("degree", legacy.mode)
                assertNull(legacy.training)
                assertTrue(store.attempts(legacy).all { it.training == null && it.cents == null })
                store.save("new", Attempt("fixed_note", 60, 60, 60, true, training = TrainingSetup()), Settings())
                assertEquals(2, store.history().practiceCount)
                assertEquals(2, store.attempts(legacy).size)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
