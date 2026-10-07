package io.github.gongpx20069.relativeear.core

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

object Music {
    private val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val major = listOf(0, 2, 4, 5, 7, 9, 11)
    fun frequency(midi: Double, a4: Double = 440.0): Double {
        require(a4 in 415.0..466.0 && midi.isFinite())
        return a4 * 2.0.pow((midi - 69.0) / 12.0)
    }
    fun midi(frequency: Double, a4: Double = 440.0): Double {
        require(frequency > 0 && frequency.isFinite() && a4 in 415.0..466.0)
        return 69 + 12 * ln(frequency / a4) / ln(2.0)
    }
    fun name(midi: Int): String = names[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)
    fun nearest(frequency: Double, a4: Double = 440.0): Int = midi(frequency, a4).roundToInt()
    fun error(midi: Double, target: Int, ignoreOctave: Boolean): Double {
        val cents = (midi - target) * 100
        return if (ignoreOctave) cents - (cents / 1200).roundToInt() * 1200 else cents
    }
}

enum class QuestionMode { INTERVAL, DEGREE }

data class Question(val mode: QuestionMode, val root: Int, val target: Int, val answer: Int) {
    fun playback(): List<List<Int>> = when (mode) {
        QuestionMode.INTERVAL -> listOf(listOf(root), listOf(target))
        QuestionMode.DEGREE -> listOf(
            listOf(root, root + 4, root + 7),
            listOf(root + 5, root + 9, root + 12),
            listOf(root + 7, root + 11, root + 14),
            listOf(root, root + 4, root + 7),
            listOf(target),
        )
    }
}

object Questions {
    fun create(mode: QuestionMode, descending: Boolean, random: Random = Random.Default): Question {
        val root = random.nextInt(48, 61)
        return when (mode) {
            QuestionMode.INTERVAL -> {
                val distance = random.nextInt(0, 13)
                Question(mode, root, root + distance * if (descending) -1 else 1, distance)
            }
            QuestionMode.DEGREE -> {
                val degree = random.nextInt(7)
                Question(mode, root, root + Music.major[degree], degree)
            }
        }
    }
}
