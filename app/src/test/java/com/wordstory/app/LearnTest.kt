package com.wordstory.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnTest {
    @Test
    fun boxes() {
        assertEquals(2, nextBox(1, true))
        assertEquals(MASTERED_BOX, nextBox(MASTERED_BOX, true))   // 掌握后不再往上
        assertEquals(1, nextBox(5, false))                          // 答错回第一格
        assertEquals(101L, dueDay(100, 1))
        assertEquals(115L, dueDay(100, 5))
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        assertEquals(3, streak(setOf(8, 9, 10), 10))
        assertEquals(3, streak(setOf(7, 8, 9), 10))      // 今天还没学，连胜不断
        assertEquals(0, streak(setOf(7, 8), 10))         // 昨天没学，断了
        assertEquals(0, streak(emptySet(), 10))
    }

    private fun q(word: String) = MeaningQuestion(Word(word, null, "m", null, 1, 1), listOf("a", "b"), 0)

    @Test
    fun wrongAnswerGoesToBackOfQueueAndOnlyFirstTryCounts() {
        val s = QuizSession(listOf(q("one"), q("two")))
        val firstTries = mutableListOf<Pair<String, Boolean>>()
        val record = { w: Word, ok: Boolean -> firstTries += w.word to ok }

        s.next(s.check(1, record))   // one 答错 -> 放回队尾
        assertEquals("two", s.current!!.word.word)
        s.next(s.check(0, record))   // two 答对
        assertEquals("one", s.current!!.word.word)
        s.next(s.check(0, record))   // one 重做答对，但不算第一次
        assertNull(s.current)

        assertEquals(listOf("one" to false, "two" to true), firstTries)
        assertEquals(1, s.firstTryCorrect)
        assertEquals(2, s.solved)
        assertEquals(3, s.step)
    }
}
