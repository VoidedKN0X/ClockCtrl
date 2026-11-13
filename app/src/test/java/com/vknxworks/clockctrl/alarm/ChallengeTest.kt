package com.vknxworks.clockctrl.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChallengeTest {

    @Test
    fun evaluatesWithTimesFirst() {
        assertEquals(19, Sum(7, Op.PLUS, 12).answer)
        assertEquals(15, Sum(23, Op.MINUS, 8).answer)
        assertEquals(56, Sum(7, Op.TIMES, 8).answer)
        assertEquals(136, Sum(7, Op.TIMES, 14, Op.PLUS, 38).answer)
        assertEquals(111, Sum(12, Op.TIMES, 13, Op.MINUS, 45).answer)
        assertEquals(158, Sum(56, Op.PLUS, 6, Op.TIMES, 17).answer)
    }

    @Test
    fun everyLevelGivesWholeAnswersThatFitTheKeypad() {
        val r = Random(42)
        for (level in Level.ALL) {
            repeat(5_000) {
                val s = SumGenerator.generate(level, r)
                assertTrue("$s", s.answer in 0..999)
                assertTrue("$s", s.answer.toString().length <= ChallengeSession.MAX_DIGITS)
                assertTrue("$s", s.a >= 0 && s.b >= 0 && s.c >= 0)
            }
        }
    }

    @Test
    fun levelsHaveTheirShape() {
        val r = Random(7)
        repeat(2_000) {
            val easy = SumGenerator.generate(Level.EASY, r)
            assertTrue(easy.op2 == null && easy.op1 != Op.TIMES)
            assertTrue(easy.answer <= 40)

            val medium = SumGenerator.generate(Level.MEDIUM, r)
            assertTrue(medium.op2 == null)
            if (medium.op1 == Op.TIMES) assertTrue(medium.a in 3..9 && medium.b in 6..12)
            else assertTrue(medium.a >= 12 && medium.b >= 11)

            val hard = SumGenerator.generate(Level.HARD, r)
            assertTrue(hard.op2 != null)
            assertTrue(hard.op1 == Op.TIMES || hard.op2 == Op.TIMES)
            assertTrue(hard.answer > 0)
        }
    }

    @Test
    fun allOperatorsTurnUp() {
        val r = Random(1)
        val ops = (1..500).flatMap { listOfNotNull(SumGenerator.generate(Level.MEDIUM, r).op1, SumGenerator.generate(Level.HARD, r).op2) }.toSet()
        assertEquals(Op.entries.toSet(), ops)
    }

    @Test
    fun outOfRangeLevelsAreClamped() {
        val r = Random(3)
        assertTrue(SumGenerator.generate(0, r).op2 == null)
        assertTrue(SumGenerator.generate(9, r).op2 != null)
    }

    @Test
    fun sameSeedSameSums() {
        val a = (1..20).map { SumGenerator.generate(Level.HARD, Random(99 + it)) }
        val b = (1..20).map { SumGenerator.generate(Level.HARD, Random(99 + it)) }
        assertEquals(a, b)
    }

    @Test
    fun rightAnswerSolves() {
        val s = ChallengeSession(Level.MEDIUM, Random(5))
        assertEquals(ChallengeSession.Result.Correct, s.submit(s.sum.answer))
    }

    @Test
    fun threeWrongAnswersBringANewSum() {
        val s = ChallengeSession(Level.HARD, Random(11))
        val first = s.sum
        val wrong = first.answer + 1
        assertEquals(ChallengeSession.Result.Wrong(2), s.submit(wrong))
        assertEquals(ChallengeSession.Result.Wrong(1), s.submit(null)) // OK with nothing typed counts as wrong
        assertEquals(first, s.sum)
        assertEquals(ChallengeSession.Result.NewSum, s.submit(wrong))
        assertEquals(0, s.wrong)
        // The new sum can be solved.
        assertEquals(ChallengeSession.Result.Correct, s.submit(s.sum.answer))
    }

    @Test
    fun newSumsKeepComing() {
        val s = ChallengeSession(Level.EASY, Random(2))
        val seen = mutableSetOf(s.sum)
        repeat(10) {
            repeat(ChallengeSession.MAX_WRONG) { s.submit(-1) }
            seen += s.sum
        }
        assertTrue(seen.size > 5)
        assertNotEquals(ChallengeSession.Result.Correct, s.submit(-1))
    }
}
