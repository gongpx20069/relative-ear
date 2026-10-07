package io.github.gongpx20069.relativeear

import android.app.Application
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.NoteEvent
import io.github.gongpx20069.relativeear.core.NoteSegmenter
import io.github.gongpx20069.relativeear.core.PitchFrame
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.NoteQuestion
import io.github.gongpx20069.relativeear.core.TrainingSetup
import io.github.gongpx20069.relativeear.core.SingingResult
import io.github.gongpx20069.relativeear.core.SingingScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

enum class Screen { EAR, SING, LISTEN, HISTORY, SETTINGS }
enum class Phase { IDLE, DEMONSTRATING, PLAYING, ANSWERING, LISTENING, SAVING, FEEDBACK, COMPLETE }
data class UiState(
    val screen: Screen = Screen.EAR,
    val phase: Phase = Phase.IDLE,
    val settings: Settings = Settings(),
    val training: TrainingSetup = TrainingSetup(),
    val demoNote: Int? = null,
    val question: NoteQuestion? = null,
    val count: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    val replays: Int = 0,
    val frame: PitchFrame? = null,
    val curve: List<PitchFrame> = emptyList(),
    val notes: List<NoteEvent> = emptyList(),
    val result: SingingResult? = null,
    val message: String? = null,
    val history: History = History(),
    val loaded: Boolean = false,
) {
    val configurable: Boolean get() = loaded && phase in listOf(Phase.IDLE, Phase.COMPLETE)
    val canHearAnswer: Boolean get() = question != null && phase in listOf(Phase.FEEDBACK, Phase.COMPLETE)
}

class EarViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HistoryStore(application)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private val audio = AudioEngine(application) { interrupt() }
    private var audioJob: Job? = null
    private var answerSince = 0L
    private var session = UUID.randomUUID().toString()
    private var referenceReturnPhase = Phase.IDLE

    init {
        reload()
    }
    fun reload() {
        viewModelScope.launch {
            storage {
                val settings = store.settings()
                val training = store.training()
                val history = store.history()
                mutable.update { it.copy(settings = settings, training = training, history = history, loaded = true) }
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
    private suspend fun storage(action: suspend () -> Unit): Boolean = try {
        withContext(Dispatchers.IO) { action() }
        true
    } catch (error: SQLiteException) {
        storageError(error); false
    } catch (error: IOException) {
        storageError(error); false
    }
    private fun storageError(error: Exception) {
        Log.e("RelativeEar", "Local storage operation failed", error)
        mutable.update { it.copy(message = text(R.string.storage_error, error.message ?: error.javaClass.simpleName)) }
    }

    fun select(screen: Screen) {
        if (state.value.phase == Phase.SAVING) return
        viewModelScope.launch {
            audioJob?.cancelAndJoin()
            audioJob = null
            mutable.update {
                it.copy(screen = screen, phase = Phase.IDLE, question = null, frame = null, result = null,
                    curve = emptyList(), notes = emptyList(), message = null, count = 0, answered = 0, correct = 0,
                    demoNote = null)
            }
        }
    }
    fun configure(training: TrainingSetup) {
        val snapshot = state.value
        if (!snapshot.configurable) return
        mutable.update { it.copy(phase = Phase.SAVING) }
        viewModelScope.launch {
            val saved = storage { store.saveTraining(training) }
            mutable.update {
                if (saved) it.copy(training = training, phase = Phase.IDLE, question = null, demoNote = null,
                    count = 0, answered = 0, correct = 0, result = null, message = null)
                else it.copy(phase = snapshot.phase)
            }
        }
    }
    fun permissionDenied() { mutable.update { it.copy(message = text(R.string.permission_denied)) } }
    fun interrupt() {
        if (state.value.phase !in listOf(Phase.DEMONSTRATING, Phase.PLAYING, Phase.LISTENING, Phase.ANSWERING)) return
        viewModelScope.launch {
            val demonstrating = state.value.phase == Phase.DEMONSTRATING
            audioJob?.cancelAndJoin()
            audioJob = null
            mutable.update { it.copy(phase = if (demonstrating) referenceReturnPhase else Phase.IDLE,
                frame = null, demoNote = null, message = text(R.string.interrupted)) }
        }
    }

    fun startQuestion() {
        if (!state.value.loaded || audioJob?.isActive == true) return
        val previous = state.value
        if (previous.phase !in listOf(Phase.IDLE, Phase.FEEDBACK, Phase.COMPLETE)) return
        if (previous.phase == Phase.IDLE && previous.question != null) {
            playQuestion()
            return
        }
        if (previous.phase == Phase.COMPLETE || previous.count == 0) {
            session = UUID.randomUUID().toString()
            mutable.update { it.copy(count = 0, answered = 0, correct = 0) }
        }
        val current = state.value
        val question = FixedTraining.question(current.training)
        mutable.update { it.copy(question = question, count = it.count + 1, replays = 0,
            result = null, frame = null, curve = emptyList(), message = null) }
        playQuestion()
    }

    fun replay() {
        if (state.value.phase != Phase.ANSWERING) return
        mutable.update { it.copy(replays = it.replays + 1) }
        playQuestion()
    }

    fun playReference(answer: Boolean = false) {
        val snapshot = state.value
        if (audioJob?.isActive == true || snapshot.phase !in listOf(Phase.IDLE, Phase.FEEDBACK, Phase.COMPLETE)) return
        val question = snapshot.question
        if (answer && question == null) return
        if (answer && !snapshot.canHearAnswer) return
        referenceReturnPhase = snapshot.phase
        mutable.update { it.copy(phase = Phase.DEMONSTRATING, demoNote = null) }
        launchAudio {
            val notes = if (answer) listOf(checkNotNull(question).target) else FixedTraining.reference(snapshot.training)
            playNotes(notes, snapshot) { index -> mutable.update { it.copy(demoNote = notes[index]) } }
            mutable.update { it.copy(phase = referenceReturnPhase, demoNote = null) }
        }
    }

    fun previewNote(note: Int) {
        val snapshot = state.value
        if (!snapshot.configurable || audioJob?.isActive == true) return
        require(note in FixedTraining.notes)
        referenceReturnPhase = snapshot.phase
        mutable.update { it.copy(phase = Phase.DEMONSTRATING, demoNote = note) }
        launchAudio {
            playNotes(listOf(note), snapshot)
            mutable.update { it.copy(phase = referenceReturnPhase, demoNote = null) }
        }
    }

    private suspend fun playNotes(notes: List<Int>, snapshot: UiState, onNote: (Int) -> Unit = {}) {
        val timing = snapshot.training.timing
        audio.play(notes.map { listOf(it) }, snapshot.settings.a4.toDouble(),
            timing.soundMs, timing.gapMs, onNote)
    }
    private fun noteName(note: Int, notation: AnswerNotation): String {
        val degree = checkNotNull(FixedTraining.degree(note))
        val names = getApplication<Application>().resources.getStringArray(R.array.fixed_solfege_names)
        return if (notation == AnswerNotation.SOLFEGE) "${names[degree]} (${Music.name(note)})"
            else "${Music.name(note)} (${names[degree]})"
    }

    private fun launchAudio(action: suspend () -> Unit) {
        audioJob = viewModelScope.launch {
            try {
                action()
            } catch (error: AudioFailure) {
                Log.e("RelativeEar", "Audio operation failed", error)
                mutable.update { it.copy(phase = if (it.phase == Phase.DEMONSTRATING) referenceReturnPhase else Phase.IDLE, frame = null, demoNote = null,
                    message = text(R.string.audio_error, error.message ?: "unknown")) }
            } catch (error: SecurityException) {
                Log.w("RelativeEar", "Microphone access denied", error)
                mutable.update { it.copy(phase = if (it.phase == Phase.DEMONSTRATING) referenceReturnPhase else Phase.IDLE,
                    frame = null, demoNote = null, message = text(R.string.permission_denied)) }
            }
        }
    }
    private fun playQuestion() {
        val snapshot = state.value
        val question = snapshot.question ?: return
        launchAudio {
            mutable.update { it.copy(phase = Phase.PLAYING, message = null) }
            playNotes(if (snapshot.screen == Screen.SING) listOf(60) else listOf(question.target), snapshot)
            if (snapshot.screen == Screen.EAR) {
                answerSince = SystemClock.elapsedRealtime()
                mutable.update { it.copy(phase = Phase.ANSWERING) }
            } else {
                delay(250)
                mutable.update { it.copy(phase = Phase.LISTENING) }
                val scorer = SingingScorer(question.target, snapshot.settings.a4.toDouble(),
                    snapshot.settings.tolerance, snapshot.settings.ignoreOctave)
                var result: SingingResult? = null
                val since = SystemClock.elapsedRealtime()
                audio.capture(8000) { frame ->
                    showFrame(frame)
                    result = scorer.accept(frame)
                    result == null
                }
                val final = result
                val message = if (final == null) text(R.string.timeout) else text(R.string.sing_result,
                    text(if (final.correct) R.string.correct else R.string.history_wrong),
                    noteName(question.target, snapshot.training.notation), final.cents)
                mutable.update { it.copy(result = final) }
                finish(Attempt("sing_fixed", question.root, question.target, question.answer, final?.correct == true,
                    timeout = final == null, cents = final?.cents, reactionMs = SystemClock.elapsedRealtime() - since),
                    message, snapshot.settings)
            }
        }
    }

    fun answer(answer: Int) {
        val snapshot = state.value
        if (snapshot.phase != Phase.ANSWERING) return
        val question = snapshot.question ?: return
        if (answer !in snapshot.training.notes) {
            Log.w("RelativeEar", "Answer outside selected training range")
            mutable.update { it.copy(message = text(R.string.invalid_answer)) }
            return
        }
        mutable.update { it.copy(phase = Phase.SAVING) }
        viewModelScope.launch {
            val correct = answer == question.answer
            val expected = noteName(question.target, snapshot.training.notation)
            finish(Attempt("fixed_note",
                question.root, question.target, answer, correct,
                reactionMs = SystemClock.elapsedRealtime() - answerSince, replays = snapshot.replays),
                if (correct) text(R.string.correct_solfege, expected) else text(R.string.wrong_answer, expected), snapshot.settings)
        }
    }
    private suspend fun finish(attempt: Attempt, message: String, settings: Settings) {
        mutable.update { it.copy(phase = Phase.SAVING, frame = null, answered = it.answered + 1,
            correct = it.correct + if (attempt.correct) 1 else 0, message = message) }
        val saved = storage {
            store.save(session, attempt.copy(training = state.value.training), settings)
            val history = store.history()
            mutable.update { it.copy(history = history, phase = if (it.count >= 10) Phase.COMPLETE else Phase.FEEDBACK) }
        }
        if (!saved) mutable.update { it.copy(phase = if (it.count >= 10) Phase.COMPLETE else Phase.FEEDBACK) }
    }
    fun listen() {
        if (audioJob?.isActive == true) return
        val settings = state.value.settings
        mutable.update { it.copy(phase = Phase.LISTENING, frame = null, curve = emptyList(), notes = emptyList(), message = null) }
        launchAudio {
            val segmenter = NoteSegmenter(settings.a4.toDouble())
            try {
                audio.capture { frame ->
                    showFrame(frame)
                    segmenter.accept(frame)?.let { note ->
                        mutable.update { it.copy(notes = (it.notes + note).takeLast(30)) }
                    }
                    true
                }
            } finally {
                segmenter.flush()?.let { note ->
                    mutable.update { it.copy(notes = (it.notes + note).takeLast(30)) }
                }
            }
        }
    }
    fun stopListening() {
        viewModelScope.launch {
            audioJob?.cancelAndJoin()
            audioJob = null
            mutable.update { it.copy(phase = Phase.IDLE, frame = null, message = null) }
        }
    }
    private fun showFrame(frame: PitchFrame) {
        mutable.update { it.copy(frame = frame,
            curve = (it.curve + frame).filter { old -> frame.timeMs - old.timeMs <= 10_000 }.takeLast(320)) }
    }
    fun settings(settings: Settings) {
        viewModelScope.launch {
            storage {
                store.saveSettings(settings)
                mutable.update { it.copy(settings = settings, message = null) }
            }
        }
    }
    fun clearHistory() {
        viewModelScope.launch {
            storage {
                store.clear()
                mutable.update { it.copy(history = History(), message = null) }
            }
        }
    }
}
