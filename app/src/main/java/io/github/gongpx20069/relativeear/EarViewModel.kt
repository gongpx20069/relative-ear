package io.github.gongpx20069.relativeear

import android.app.Application
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import android.os.Build
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
import io.github.gongpx20069.relativeear.core.AppUpdate
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.ToneVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

enum class Screen { EAR, SING, LISTEN, PIANO, HISTORY, SETTINGS }
enum class Phase { IDLE, DEMONSTRATING, PLAYING, ANSWERING, LISTENING, REPLAYING, SAVING, FEEDBACK, COMPLETE }
data class UpdateState(
    val checking: Boolean = false, val checked: Boolean = false, val available: AppUpdate? = null,
    val prompt: Boolean = false, val error: String? = null,
)
data class UiState(
    val screen: Screen = Screen.EAR,
    val phase: Phase = Phase.IDLE,
    val settings: Settings = Settings(),
    val training: TrainingSetup = TrainingSetup(),
    val demoNote: Int? = null,
    val pianoNote: Int? = null,
    val pianoExpanded: Boolean = false,
    val pianoVoice: ToneVoice = ToneVoice.PIANO,
    val pianoVoiceSaving: Boolean = false,
    val question: NoteQuestion? = null,
    val count: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    val replays: Int = 0,
    val frame: PitchFrame? = null,
    val curve: List<PitchFrame> = emptyList(),
    val notes: List<NoteEvent> = emptyList(),
    val listeningMs: Long = 0,
    val replayPositionMs: Long = 0,
    val result: SingingResult? = null,
    val message: String? = null,
    val history: History = History(),
    val selectedPractice: PracticeSummary? = null,
    val practiceAttempts: List<Attempt> = emptyList(),
    val practiceLoading: Boolean = false,
    val loaded: Boolean = false,
    val update: UpdateState = UpdateState(),
) {
    val configurable: Boolean get() = loaded && phase in listOf(Phase.IDLE, Phase.COMPLETE)
    val canHearAnswer: Boolean get() = question != null && phase in listOf(Phase.FEEDBACK, Phase.COMPLETE)
    val canPreview: Boolean get() = loaded && screen == Screen.PIANO && (phase in listOf(Phase.IDLE, Phase.COMPLETE) ||
        (phase == Phase.DEMONSTRATING && pianoNote != null))
    val listeningClip: MelodyClip? get() = if (listeningMs > 0) MelodyClip.recent(notes, listeningMs, settings.a4.toDouble()) else null
    val canReplay: Boolean get() = loaded && screen == Screen.LISTEN && phase == Phase.IDLE &&
        listeningClip?.notes?.isNotEmpty() == true
}

class EarViewModel @JvmOverloads constructor(
    application: Application, private val updateClient: ReleaseUpdateClient = ReleaseUpdateClient(),
    audioOverride: AudioSession? = null,
) : AndroidViewModel(application) {
    private val store = HistoryStore(application)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private val audio: AudioSession = audioOverride ?: AudioEngine(application) { interrupt() }
    private var audioJob: Job? = null
    private val audioMutex = Mutex()
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
                val pianoVoice = store.pianoVoice()
                val history = store.history()
                mutable.update { it.copy(settings = settings, training = training, pianoVoice = pianoVoice,
                    history = history, loaded = true) }
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
    private suspend fun storage(errorMessage: Int = R.string.storage_error, action: suspend () -> Unit): Boolean = try {
        withContext(Dispatchers.IO) { action() }
        true
    } catch (error: SQLiteException) {
        storageError(error, errorMessage); false
    } catch (error: IOException) {
        storageError(error, errorMessage); false
    }
    private fun storageError(error: Exception, errorMessage: Int) {
        Log.e("RelativeEar", "Local storage operation failed", error)
        mutable.update { it.copy(message = text(errorMessage, error.message ?: error.javaClass.simpleName)) }
    }

    fun select(screen: Screen) {
        if (state.value.phase == Phase.SAVING) return
        viewModelScope.launch {
            audioJob?.cancelAndJoin()
            audioJob = null
            mutable.update {
                it.copy(screen = screen, phase = Phase.IDLE, question = null, frame = null, result = null,
                    curve = emptyList(), notes = emptyList(), message = null, count = 0, answered = 0, correct = 0,
                    demoNote = null, pianoNote = null, pianoExpanded = false, listeningMs = 0, replayPositionMs = 0,
                    selectedPractice = null, practiceAttempts = emptyList(), practiceLoading = false)
            }
        }
    }
    fun expandPiano(expanded: Boolean) {
        if (state.value.screen != Screen.PIANO || !state.value.loaded) return
        mutable.update { it.copy(pianoExpanded = expanded) }
    }
    fun pianoVoice(voice: ToneVoice) {
        val snapshot = state.value
        if (!snapshot.canPreview || snapshot.pianoVoiceSaving || snapshot.pianoVoice == voice) return
        mutable.update { it.copy(pianoVoiceSaving = true) }
        viewModelScope.launch {
            val saved = storage { store.savePianoVoice(voice) }
            mutable.update {
                if (saved) it.copy(pianoVoice = voice, pianoVoiceSaving = false)
                else it.copy(pianoVoiceSaving = false)
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
        if (state.value.phase !in listOf(Phase.DEMONSTRATING, Phase.PLAYING, Phase.LISTENING, Phase.ANSWERING, Phase.REPLAYING)) return
        viewModelScope.launch {
            val demonstrating = state.value.phase == Phase.DEMONSTRATING
            audioJob?.cancelAndJoin()
            audioJob = null
            mutable.update { it.copy(phase = if (demonstrating) referenceReturnPhase else Phase.IDLE,
                frame = null, demoNote = null, pianoNote = null, message = text(
                    if (it.screen == Screen.PIANO) R.string.piano_interrupted else R.string.interrupted)) }
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
        if (!snapshot.canPreview) return
        require(note in FixedTraining.notes)
        if (snapshot.phase != Phase.DEMONSTRATING) referenceReturnPhase = snapshot.phase
        audioJob?.cancel()
        mutable.update { it.copy(phase = Phase.DEMONSTRATING, demoNote = note, pianoNote = note, message = null) }
        launchAudio {
            playNotes(listOf(note), snapshot, voice = snapshot.pianoVoice)
            mutable.update { it.copy(phase = referenceReturnPhase, demoNote = null, pianoNote = null) }
        }
    }

    private suspend fun playNotes(notes: List<Int>, snapshot: UiState, voice: ToneVoice = ToneVoice.PURE,
        onNote: (Int) -> Unit = {}) {
        val timing = snapshot.training.timing
        audio.play(notes.map { listOf(it) }, snapshot.settings.a4.toDouble(),
            timing.soundMs, timing.gapMs, voice, onNote)
    }
    private fun noteName(note: Int, notation: AnswerNotation): String {
        val degree = checkNotNull(FixedTraining.degree(note))
        val names = getApplication<Application>().resources.getStringArray(R.array.fixed_solfege_names)
        return if (notation == AnswerNotation.SOLFEGE) "${names[degree]} (${Music.name(note)})"
            else "${Music.name(note)} (${names[degree]})"
    }

    private fun launchAudio(action: suspend () -> Unit) {
        audioJob = viewModelScope.launch {
            // A cancelled intermediate preview must not bypass an older player's resource cleanup.
            audioMutex.withLock {
                currentCoroutineContext().ensureActive()
                try {
                    action()
                } catch (error: AudioFailure) {
                    currentCoroutineContext().ensureActive()
                    Log.e("RelativeEar", "Audio operation failed", error)
                    mutable.update { it.copy(phase = if (it.phase == Phase.DEMONSTRATING) referenceReturnPhase else Phase.IDLE, frame = null, demoNote = null, pianoNote = null,
                        message = text(R.string.audio_error, error.message ?: "unknown")) }
                } catch (error: SecurityException) {
                    currentCoroutineContext().ensureActive()
                    Log.w("RelativeEar", "Microphone access denied", error)
                    mutable.update { it.copy(phase = if (it.phase == Phase.DEMONSTRATING) referenceReturnPhase else Phase.IDLE,
                        frame = null, demoNote = null, pianoNote = null, message = text(R.string.permission_denied)) }
                }
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
        val snapshot = state.value
        if (!snapshot.loaded || snapshot.screen != Screen.LISTEN || snapshot.phase != Phase.IDLE || audioJob?.isActive == true) return
        val settings = state.value.settings
        mutable.update { it.copy(phase = Phase.LISTENING, frame = null, curve = emptyList(), notes = emptyList(),
            listeningMs = 0, replayPositionMs = 0, demoNote = null, pianoNote = null, message = null) }
        launchAudio {
            val segmenter = NoteSegmenter(settings.a4.toDouble())
            try {
                audio.capture { frame ->
                    showFrame(frame)
                    segmenter.accept(frame)?.let { note ->
                        rememberNote(note)
                    }
                    true
                }
            } finally {
                segmenter.flush()?.let { note ->
                    rememberNote(note)
                }
            }
            mutable.update { it.copy(phase = Phase.IDLE, frame = null) }
        }
    }
    private fun rememberNote(note: NoteEvent) {
        mutable.update {
            val origin = (it.listeningMs - MelodyClip.WINDOW_MS).coerceAtLeast(0)
            it.copy(notes = (it.notes + note).filter { event -> event.startMs + event.durationMs > origin })
        }
    }
    fun replayDetected() {
        val snapshot = state.value
        if (!snapshot.canReplay || audioJob?.isActive == true) return
        val clip = checkNotNull(snapshot.listeningClip)
        mutable.update { it.copy(phase = Phase.REPLAYING, replayPositionMs = 0, demoNote = null, frame = null, message = null) }
        launchAudio {
            audio.playMelody(clip) { position ->
                mutable.update { it.copy(replayPositionMs = position, demoNote = clip.noteAt(position)) }
            }
            mutable.update { it.copy(phase = Phase.IDLE, replayPositionMs = clip.durationMs, demoNote = null) }
        }
    }
    fun stopReplay() {
        if (state.value.phase != Phase.REPLAYING) return
        viewModelScope.launch {
            audioJob?.cancelAndJoin()
            audioJob = null
            mutable.update { it.copy(phase = Phase.IDLE, demoNote = null) }
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
        mutable.update {
            val listening = it.screen == Screen.LISTEN
            val window = if (listening) MelodyClip.WINDOW_MS else 10_000L
            val origin = (frame.timeMs - window).coerceAtLeast(0)
            it.copy(frame = frame, listeningMs = if (listening) frame.timeMs else it.listeningMs,
                notes = if (listening) it.notes.filter { note -> note.startMs + note.durationMs > origin } else it.notes,
                curve = (it.curve + frame).filter { old -> old.timeMs >= origin }.takeLast(if (listening) 2000 else 320))
        }
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
                mutable.update { it.copy(history = History(), message = null, selectedPractice = null,
                    practiceAttempts = emptyList(), practiceLoading = false) }
            }
        }
    }
    fun openPractice(practice: PracticeSummary) {
        val snapshot = state.value
        if (snapshot.screen != Screen.HISTORY || snapshot.phase == Phase.SAVING || snapshot.practiceLoading) return
        if (practice !in snapshot.history.practices) {
            Log.w("RelativeEar", "Requested practice is not in loaded history")
            mutable.update { it.copy(message = text(R.string.practice_missing)) }
            return
        }
        mutable.update { it.copy(selectedPractice = practice, practiceAttempts = emptyList(),
            practiceLoading = true, message = null) }
        viewModelScope.launch {
            val loaded = storage(R.string.practice_load_error) {
                val attempts = store.attempts(practice)
                if (attempts.isEmpty()) throw IOException("Saved practice no longer exists")
                mutable.update {
                    if (it.selectedPractice == practice) it.copy(practiceAttempts = attempts, practiceLoading = false)
                    else it
                }
            }
            if (!loaded) mutable.update {
                if (it.selectedPractice == practice) it.copy(practiceLoading = false) else it
            }
        }
    }
    fun closePractice() {
        mutable.update { it.copy(selectedPractice = null, practiceAttempts = emptyList(),
            practiceLoading = false, message = null) }
    }
    fun checkForUpdates() {
        if (state.value.update.checking) return
        mutable.update { it.copy(update = UpdateState(checking = true)) }
        viewModelScope.launch {
            try {
                val available = withContext(Dispatchers.IO) {
                    updateClient.check(BuildConfig.VERSION_CODE, Build.SUPPORTED_ABIS.toList())
                }
                mutable.update { it.copy(update = UpdateState(checked = true, available = available, prompt = available != null)) }
            } catch (error: IOException) {
                Log.w("RelativeEar", "Release update check failed", error)
                val message = text(when ((error as? UpdateCheckException)?.reason) {
                    UpdateFailure.INVALID_RELEASE -> R.string.update_invalid_release
                    UpdateFailure.ACCESS_RESTRICTED -> R.string.update_restricted
                    null -> R.string.update_network_error
                })
                mutable.update { it.copy(update = UpdateState(error = message)) }
            }
        }
    }
    fun dismissUpdatePrompt() { mutable.update { it.copy(update = it.update.copy(prompt = false)) } }
    fun updateOpenFailed() {
        mutable.update { it.copy(update = it.update.copy(error = text(R.string.update_open_error))) }
    }
}
