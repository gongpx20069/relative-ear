package io.github.gongpx20069.relativeear.core

import kotlin.math.roundToInt
import kotlin.random.Random

enum class AnswerNotation { SOLFEGE, NOTE_NAME }

data class TrainingSetup(
    val notes: List<Int> = FixedTraining.beginner,
    val notation: AnswerNotation = AnswerNotation.SOLFEGE,
    val bpm: Int = 80,
) {
    init {
        require(notes.isNotEmpty() && notes == notes.distinct().sorted() &&
            notes.all { it in FixedTraining.notes }) { "Choose an ordered, nonempty set of C4-C5 natural notes" }
        require(bpm in FixedTraining.tempos) { "Unsupported training tempo" }
    }
    val timing: EighthTiming get() = EighthTiming(bpm)
}

data class EighthTiming(val bpm: Int) {
    init { require(bpm in FixedTraining.tempos) }
    val slotMs: Int get() = (30_000.0 / bpm).roundToInt()
    val soundMs: Int get() = (slotMs * 0.85).roundToInt()
    val gapMs: Long get() = (slotMs - soundMs).toLong()
}

data class NoteQuestion(val target: Int) {
    init { require(target in FixedTraining.notes) }
    val root: Int get() = 60
    val answer: Int get() = target
}

object FixedTraining {
    val notes = listOf(60, 62, 64, 65, 67, 69, 71, 72)
    val beginner = notes.take(3)
    val fiveNotes = notes.take(5)
    val tempos = listOf(60, 80, 100, 120)
    fun question(setup: TrainingSetup, random: Random = Random.Default) = NoteQuestion(setup.notes.random(random))
    fun degree(midi: Int): Int? = notes.indexOf(midi).takeIf { it >= 0 }
    fun syllable(midi: Int): String {
        val index = requireNotNull(degree(midi)) { "Not a C4-C5 natural note" }
        return Solfege.syllables[index % 7]
    }
    fun reference(setup: TrainingSetup): List<Int> = (listOf(60) + setup.notes).distinct() + 60
}
