package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SolfegeTest {
    @Test fun beginnerQuestionsUseOnlyDoReMiAndKeepTheirEstablishedRoot() {
        val random = Random(27)
        for (root in 48..60) {
            val observed = mutableSetOf<Int>()
            repeat(100) {
                val question = Solfege.question(root, SolfegeLesson.THREE_NOTES, random)
                assertEquals(QuestionMode.DEGREE, question.mode)
                assertEquals(root, question.root)
                assertTrue(question.answer in 0..2)
                assertEquals(root + Music.major[question.answer], question.target)
                assertEquals(Solfege.context(root) + listOf(listOf(question.target)), question.playback())
                observed.add(question.answer)
            }
            assertEquals(setOf(0, 1, 2), observed)
        }
    }

    @Test fun fullLessonIncludesAllSevenSyllables() {
        val random = Random(19)
        val observed = mutableSetOf<Int>()
        repeat(200) {
            val question = Solfege.question(53, SolfegeLesson.SEVEN_NOTES, random)
            assertEquals(question.answer, Solfege.degree(question.target, question.root))
            observed.add(question.answer)
        }
        assertEquals((0..6).toSet(), observed)
        assertEquals(listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si"), Solfege.syllables)
    }

    @Test fun movableDoIsNotFixedToCAndWorksAcrossOctaves() {
        assertEquals(0, Solfege.degree(50, 50))
        assertEquals(1, Solfege.degree(52, 50))
        assertEquals(2, Solfege.degree(54, 50))
        assertEquals(2, Solfege.degree(66, 50))
        assertEquals(2, Solfege.degree(42, 50))
        assertEquals(6, Solfege.degree(49, 50))
        assertNull(Solfege.degree(51, 50))
    }

    @Test fun primerEstablishesDoThenPlaysTheSelectedSyllablesAndReturnsToDo() {
        for (lesson in SolfegeLesson.entries) {
            val reference = Solfege.reference(57, lesson)
            val context = Solfege.context(57)
            assertEquals(listOf(57), context.last())
            assertEquals(context, reference.take(context.size))
            assertEquals(lesson.degrees.map { listOf(57 + Music.major[it]) },
                reference.subList(context.size, reference.lastIndex))
            assertEquals(listOf(57), reference.last())
        }
    }

    @Test fun allTrainingRootsAndTargetsFitTheSupportedDetectionRange() {
        val random = Random(93)
        repeat(1000) {
            val root = Solfege.chooseRoot(random)
            assertTrue(root in 48..60)
            val question = Solfege.question(root, SolfegeLesson.SEVEN_NOTES, random)
            assertTrue(Music.frequency(question.target.toDouble()) in 65.0..1000.0)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTrainingRootIsRejected() { Solfege.question(20, SolfegeLesson.THREE_NOTES) }
}
