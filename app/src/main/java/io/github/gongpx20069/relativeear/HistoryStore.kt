package io.github.gongpx20069.relativeear

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.TrainingSetup
import io.github.gongpx20069.relativeear.core.ToneVoice
import io.github.gongpx20069.relativeear.core.DetectionTake
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.NoteEvent
import io.github.gongpx20069.relativeear.core.PitchPoint
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

data class DetectionSummary(
    val id: String, val title: String, val timeMs: Long, val durationMs: Long, val a4: Double,
    val noteCount: Int, val lowest: Int?, val highest: Int?,
    val preview: List<NoteEvent> = emptyList(),
)
data class SavedDetection(val summary: DetectionSummary, val take: DetectionTake)

class HistoryStore(context: Context, databaseName: String = "training.db") :
    SQLiteOpenHelper(context, databaseName, null, 3) {
    private val preferences = context.getSharedPreferences("training-settings", Context.MODE_PRIVATE)
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
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
        createDetections(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..2 && newVersion in 2..3 && newVersion > oldVersion) {
            "Missing database migration: $oldVersion -> $newVersion"
        }
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE attempts ADD COLUMN training_notes TEXT")
            db.execSQL("ALTER TABLE attempts ADD COLUMN notation TEXT")
            db.execSQL("ALTER TABLE attempts ADD COLUMN bpm INTEGER")
        }
        if (newVersion >= 3) createDetections(db)
    }
    private fun createDetections(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE detection_takes (
            id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, time_ms INTEGER NOT NULL,
            duration_ms INTEGER NOT NULL, a4 REAL NOT NULL, note_count INTEGER NOT NULL,
            lowest INTEGER, highest INTEGER, data_version INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE detection_notes (
            take_id TEXT NOT NULL REFERENCES detection_takes(id) ON DELETE CASCADE,
            position INTEGER NOT NULL, midi INTEGER NOT NULL, start_ms INTEGER NOT NULL,
            duration_ms INTEGER NOT NULL, PRIMARY KEY (take_id, position)
        )""".trimIndent())
        db.execSQL("""CREATE TABLE detection_points (
            take_id TEXT NOT NULL REFERENCES detection_takes(id) ON DELETE CASCADE,
            position INTEGER NOT NULL, time_ms INTEGER NOT NULL, midi REAL, confidence REAL NOT NULL,
            PRIMARY KEY (take_id, position)
        )""".trimIndent())
    }
    fun saveDetection(id: String, timeMs: Long, take: DetectionTake) {
        require(id.isNotBlank() && take.hasPitch)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("detection_takes", null, ContentValues().apply {
                put("id", id); put("title", ""); put("time_ms", timeMs)
                put("duration_ms", take.clip.durationMs); put("a4", take.clip.a4)
                put("note_count", take.clip.notes.size)
                put("lowest", take.clip.notes.minOfOrNull { it.midi })
                put("highest", take.clip.notes.maxOfOrNull { it.midi }); put("data_version", 1)
            })
            take.clip.notes.forEachIndexed { index, note ->
                db.insertOrThrow("detection_notes", null, ContentValues().apply {
                    put("take_id", id); put("position", index); put("midi", note.midi)
                    put("start_ms", note.startMs); put("duration_ms", note.durationMs)
                })
            }
            take.points.forEachIndexed { index, point ->
                db.insertOrThrow("detection_points", null, ContentValues().apply {
                    put("take_id", id); put("position", index); put("time_ms", point.timeMs)
                    put("midi", point.midi); put("confidence", point.confidence)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    private fun detectionSummary(cursor: Cursor): DetectionSummary {
        fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
        fun nullableInt(name: String): Int? = cursor.getColumnIndexOrThrow(name).let {
            if (cursor.isNull(it)) null else cursor.getInt(it)
        }
        return DetectionSummary(cursor.getString(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("title")), long("time_ms"), long("duration_ms"),
            cursor.getDouble(cursor.getColumnIndexOrThrow("a4")),
            cursor.getInt(cursor.getColumnIndexOrThrow("note_count")), nullableInt("lowest"), nullableInt("highest"))
    }
    fun detections(): List<DetectionSummary> {
        val summaries = mutableListOf<DetectionSummary>()
        readableDatabase.query("detection_takes", null, null, null, null, null, "time_ms DESC, rowid DESC").use {
            while (it.moveToNext()) summaries.add(detectionSummary(it))
        }
        return summaries.map { summary ->
            val preview = mutableListOf<NoteEvent>()
            readableDatabase.query("detection_notes", arrayOf("midi", "start_ms", "duration_ms"),
                "take_id = ?", arrayOf(summary.id), null, null, "position ASC", "24").use {
                while (it.moveToNext()) preview.add(NoteEvent(it.getInt(0), it.getLong(1), it.getLong(2)))
            }
            summary.copy(preview = preview)
        }
    }
    fun detection(id: String): SavedDetection {
        val db = readableDatabase
        val summary = db.query("detection_takes", null, "id = ?", arrayOf(id), null, null, null).use {
            if (!it.moveToFirst()) throw IOException("Saved detection clip no longer exists")
            if (it.getInt(it.getColumnIndexOrThrow("data_version")) != 1) throw IOException("Unsupported detection data version")
            detectionSummary(it)
        }
        val notes = mutableListOf<NoteEvent>()
        db.query("detection_notes", null, "take_id = ?", arrayOf(id), null, null, "position ASC",
            (DetectionTake.MAX_NOTES + 1).toString()).use {
            while (it.moveToNext()) {
                notes.add(NoteEvent(it.getInt(it.getColumnIndexOrThrow("midi")),
                    it.getLong(it.getColumnIndexOrThrow("start_ms")), it.getLong(it.getColumnIndexOrThrow("duration_ms"))))
            }
        }
        val points = mutableListOf<PitchPoint>()
        try {
            db.query("detection_points", null, "take_id = ?", arrayOf(id), null, null, "position ASC",
                (DetectionTake.MAX_POINTS + 1).toString()).use {
                while (it.moveToNext()) {
                    val midiIndex = it.getColumnIndexOrThrow("midi")
                    points.add(PitchPoint(it.getLong(it.getColumnIndexOrThrow("time_ms")),
                        if (it.isNull(midiIndex)) null else it.getDouble(midiIndex),
                        it.getDouble(it.getColumnIndexOrThrow("confidence"))))
                }
            }
            val take = DetectionTake(MelodyClip(notes, summary.durationMs, summary.a4), points)
            require(take.hasPitch && summary.noteCount == notes.size &&
                summary.lowest == notes.minOfOrNull { it.midi } && summary.highest == notes.maxOfOrNull { it.midi })
            return SavedDetection(summary, take)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid saved detection data", error)
        }
    }
    fun renameDetection(id: String, title: String) {
        require(title.trim().isNotEmpty() && title.trim().length <= 80)
        val changed = writableDatabase.update("detection_takes", ContentValues().apply {
            put("title", title.trim())
        }, "id = ?", arrayOf(id))
        if (changed != 1) throw IOException("Saved detection clip no longer exists")
    }
    fun deleteDetection(id: String) {
        if (writableDatabase.delete("detection_takes", "id = ?", arrayOf(id)) != 1) {
            throw IOException("Saved detection clip no longer exists")
        }
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
