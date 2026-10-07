package io.github.gongpx20069.relativeear.core

import kotlin.random.Random

enum class SolfegeLesson(val degrees: List<Int>) {
    THREE_NOTES(listOf(0, 1, 2)),
    SEVEN_NOTES((0..6).toList()),
}

object Solfege {
    val syllables = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

    fun degree(midi: Int, root: Int): Int? =
        Music.major.indexOf(Math.floorMod(midi - root, 12)).takeIf { it >= 0 }

    fun chooseRoot(random: Random = Random.Default): Int = random.nextInt(48, 61)

    fun question(root: Int, lesson: SolfegeLesson, random: Random = Random.Default): Question {
        require(root in 48..60) { "Training Do must be between C3 and C4" }
        val degree = lesson.degrees.random(random)
        return Question(QuestionMode.DEGREE, root, root + Music.major[degree], degree)
    }

    fun context(root: Int): List<List<Int>> = listOf(
        listOf(root, root + 4, root + 7),
        listOf(root + 5, root + 9, root + 12),
        listOf(root + 7, root + 11, root + 14),
        listOf(root, root + 4, root + 7),
        listOf(root),
    )

    fun reference(root: Int, lesson: SolfegeLesson): List<List<Int>> =
        context(root) + lesson.degrees.map { listOf(root + Music.major[it]) } + listOf(listOf(root))
}
