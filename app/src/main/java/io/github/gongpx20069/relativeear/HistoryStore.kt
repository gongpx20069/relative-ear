package io.github.gongpx20069.relativeear

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.TrainingSetup
import io.github.gongpx20069.relativeear.core.ToneVoice
import java.io.IOException

data class Settings(val a4: Int = 440, val tolerance: Int = 35, val ignoreOctave: Boolean = false)
data class Attempt(
    val mode: String,
    val root: Int,
    val target: Int,
    val answer: Int,
    val correct: Boolean,
    val timeout: Boolean = false,
    val cents: Double? = null,
    val reactionMs: Long = 0,
    val replays: Int = 0,
    val timeMs: Long = System.currentTimeMillis(),
    val training: TrainingSetup? = null,
)
data class PracticeSummary(
    val session: String,
    val mode: String,
    val timeMs: Long,
    val total: Int,
    val correct: Int,
    val training: TrainingSetup? = null,
) {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100L / total).toInt()
}
data class History(
    val practices: List<PracticeSummary> = emptyList(),
    val total: Int = 0,
    val correct: Int = 0,
    val practiceCount: Int = 0,
) {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100L / total).toInt()
}

class HistoryStore(context: Context, databaseName: String = "training.db") :
    SQLiteOpenHelper(context, databaseName, null, 2) {
    private val preferences = context.getSharedPreferences("training-settings", Context.MODE_PRIVATE)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT, session TEXT NOT NULL, mode TEXT NOT NULL,
                root INTEGER NOT NULL, target INTEGER NOT NULL, answer INTEGER NOT NULL,
                correct INTEGER NOT NULL, timeout INTEGER NOT NULL, cents REAL,
                reaction_ms INTEGER NOT NULL, replays INTEGER NOT NULL, time_ms INTEGER NOT NULL,
                a4 INTEGER NOT NULL, tolerance INTEGER NOT NULL, ignore_octave INTEGER NOT NULL,
                scoring_version INTEGER NOT NULL,
                training_notes TEXT, notation TEXT, bpm INTEGER
            )""".trimIndent(),
        )
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE attempts ADD COLUMN training_notes TEXT")
            db.execSQL("ALTER TABLE attempts ADD COLUMN notation TEXT")
            db.execSQL("ALTER TABLE attempts ADD COLUMN bpm INTEGER")
            return
        }
        throw IllegalStateException("Missing database migration: $oldVersion -> $newVersion")
    }
    fun settings(): Settings = Settings(
        preferences.getInt("a4", 440), preferences.getInt("tolerance", 35),
        preferences.getBoolean("ignoreOctave", false),
    )
    fun training(): TrainingSetup = decodeTraining(
        preferences.getString("trainingNotes", TrainingSetup().notes.joinToString(",")),
        preferences.getString("answerNotation", AnswerNotation.SOLFEGE.name),
        preferences.getInt("trainingBpm", 80),
    )
    fun pianoVoice(): ToneVoice = try {
        ToneVoice.valueOf(requireNotNull(preferences.getString("pianoVoice", ToneVoice.PIANO.name)))
    } catch (error: IllegalArgumentException) {
        throw IOException("Invalid saved piano voice", error)
    }
    fun savePianoVoice(voice: ToneVoice) {
        if (!preferences.edit().putString("pianoVoice", voice.name).commit()) {
            throw IOException("Piano voice write failed")
        }
    }
    private fun decodeTraining(notes: String?, notation: String?, bpm: Int): TrainingSetup = try {
        TrainingSetup(requireNotNull(notes).split(",").map(String::toInt),
            AnswerNotation.valueOf(requireNotNull(notation)), bpm)
    } catch (error: IllegalArgumentException) {
        throw IOException("Invalid saved training configuration", error)
    }
    fun saveTraining(training: TrainingSetup) {
        val saved = preferences.edit().putString("trainingNotes", training.notes.joinToString(","))
            .putString("answerNotation", training.notation.name).putInt("trainingBpm", training.bpm).commit()
        if (!saved) throw IOException("Training configuration write failed")
    }
    fun saveSettings(settings: Settings) {
        require(settings.a4 in 415..466 && settings.tolerance in listOf(20, 35, 50))
        val saved = preferences.edit().putInt("a4", settings.a4).putInt("tolerance", settings.tolerance)
            .putBoolean("ignoreOctave", settings.ignoreOctave).commit()
        if (!saved) throw IOException("Settings write failed")
    }
    fun save(session: String, attempt: Attempt, settings: Settings) {
        val values = ContentValues().apply {
            put("session", session); put("mode", attempt.mode); put("root", attempt.root)
            put("target", attempt.target); put("answer", attempt.answer)
            put("correct", if (attempt.correct) 1 else 0); put("timeout", if (attempt.timeout) 1 else 0)
            if (attempt.cents == null) putNull("cents") else put("cents", attempt.cents)
            put("reaction_ms", attempt.reactionMs); put("replays", attempt.replays)
            put("time_ms", attempt.timeMs); put("a4", settings.a4); put("tolerance", settings.tolerance)
            put("ignore_octave", if (settings.ignoreOctave) 1 else 0); put("scoring_version", 1)
            attempt.training?.let { setup ->
                put("training_notes", setup.notes.joinToString(",")); put("notation", setup.notation.name)
                put("bpm", setup.bpm)
            }
        }
        writableDatabase.insertOrThrow("attempts", null, values)
    }
    fun history(): History {
        var total: Int
        var correct: Int
        var practiceCount: Int
        readableDatabase.rawQuery(
            """SELECT COUNT(*), COALESCE(SUM(total), 0), COALESCE(SUM(correct), 0)
                FROM (SELECT COUNT(*) AS total, SUM(correct) AS correct
                    FROM attempts GROUP BY session, mode)""".trimIndent(), null,
        ).use {
            it.moveToFirst()
            practiceCount = it.getInt(0)
            total = it.getInt(1)
            correct = it.getInt(2)
        }
        val practices = mutableListOf<PracticeSummary>()
        readableDatabase.rawQuery(
            """SELECT a.session, a.mode, a.time_ms, a.training_notes, a.notation, a.bpm,
                    p.total, p.correct
                FROM attempts AS a JOIN (
                    SELECT COUNT(*) AS total, SUM(correct) AS correct,
                        MIN(id) AS first_id, MAX(id) AS last_id
                    FROM attempts GROUP BY session, mode
                    ORDER BY MAX(id) DESC LIMIT 100
                ) AS p ON a.id = p.first_id
                ORDER BY p.last_id DESC""".trimIndent(), null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                practices.add(PracticeSummary(
                    cursor.getString(cursor.getColumnIndexOrThrow("session")),
                    cursor.getString(cursor.getColumnIndexOrThrow("mode")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("time_ms")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("total")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("correct")),
                    readTraining(cursor),
                ))
            }
        }
        return History(practices, total, correct, practiceCount)
    }
    private fun readTraining(cursor: Cursor): TrainingSetup? {
        val notesIndex = cursor.getColumnIndexOrThrow("training_notes")
        val notationIndex = cursor.getColumnIndexOrThrow("notation")
        val bpmIndex = cursor.getColumnIndexOrThrow("bpm")
        return if (cursor.isNull(notesIndex) && cursor.isNull(notationIndex) && cursor.isNull(bpmIndex)) null
            else decodeTraining(cursor.getString(notesIndex), cursor.getString(notationIndex), cursor.getInt(bpmIndex))
    }
    fun attempts(practice: PracticeSummary): List<Attempt> {
        val attempts = mutableListOf<Attempt>()
        readableDatabase.query("attempts", null, "session = ? AND mode = ?",
            arrayOf(practice.session, practice.mode), null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                fun int(column: String) = cursor.getInt(cursor.getColumnIndexOrThrow(column))
                fun long(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                val centsIndex = cursor.getColumnIndexOrThrow("cents")
                attempts.add(
                    Attempt(
                        cursor.getString(cursor.getColumnIndexOrThrow("mode")),
                        int("root"), int("target"), int("answer"), int("correct") == 1, int("timeout") == 1,
                        if (cursor.isNull(centsIndex)) null else cursor.getDouble(centsIndex),
                        long("reaction_ms"), int("replays"), long("time_ms"), readTraining(cursor),
                    ),
                )
            }
        }
        return attempts
    }
    fun clear() { writableDatabase.delete("attempts", null, null) }
}
