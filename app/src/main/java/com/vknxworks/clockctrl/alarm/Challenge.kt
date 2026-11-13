package com.vknxworks.clockctrl.alarm

import kotlin.random.Random

/** The operators a dismiss sum uses. Answers are always whole numbers of 0 or more, so the keypad needs no minus. */
enum class Op { PLUS, MINUS, TIMES }

/**
 * A sum to solve before an alarm can be dismissed: `a op1 b`, or `a op1 b op2 c` with the usual order (times
 * before plus and minus).
 */
data class Sum(val a: Int, val op1: Op, val b: Int, val op2: Op? = null, val c: Int = 0) {
    val answer: Int get() = evaluate()

    fun evaluate(): Int {
        if (op2 == null) return apply(a, op1, b)
        // Times binds tighter than plus and minus.
        return if (op2 == Op.TIMES && op1 != Op.TIMES) apply(a, op1, apply(b, op2, c)) else apply(apply(a, op1, b), op2, c)
    }

    private fun apply(x: Int, op: Op, y: Int): Int = when (op) {
        Op.PLUS -> x + y
        Op.MINUS -> x - y
        Op.TIMES -> x * y
    }
}

/** Difficulty levels, as stored on the alarm (0 is off). */
object Level {
    const val OFF = 0
    const val EASY = 1
    const val MEDIUM = 2
    const val HARD = 3
    val ALL = listOf(EASY, MEDIUM, HARD)
}

/** Makes sums for each level. Every answer is 0..999, so it fits the 4-digit keypad. */
object SumGenerator {
    fun generate(level: Int, random: Random = Random.Default): Sum = when (level.coerceIn(Level.EASY, Level.HARD)) {
        Level.EASY -> easy(random)
        Level.MEDIUM -> medium(random)
        else -> hard(random)
    }

    /** One-step plus or minus with small numbers: 7 + 12, 23 − 8. */
    private fun easy(r: Random): Sum = if (r.nextBoolean()) {
        Sum(r.nextInt(2, 21), Op.PLUS, r.nextInt(2, 21))
    } else {
        val a = r.nextInt(10, 31)
        Sum(a, Op.MINUS, r.nextInt(2, a - 1))
    }

    /** Two-digit plus or minus, or a times table: 47 + 36, 82 − 29, 7 × 8. */
    private fun medium(r: Random): Sum = when (r.nextInt(3)) {
        0 -> Sum(r.nextInt(12, 90), Op.PLUS, r.nextInt(12, 90))
        1 -> {
            val a = r.nextInt(40, 100)
            Sum(a, Op.MINUS, r.nextInt(11, a - 9))
        }
        else -> Sum(r.nextInt(3, 10), Op.TIMES, r.nextInt(6, 13))
    }

    /** Two steps with a times: 7 × 14 + 38, 12 × 13 − 45, 56 + 6 × 17. */
    private fun hard(r: Random): Sum = when (r.nextInt(3)) {
        0 -> Sum(r.nextInt(6, 13), Op.TIMES, r.nextInt(11, 20), Op.PLUS, r.nextInt(11, 100))
        1 -> {
            val a = r.nextInt(6, 13)
            val b = r.nextInt(11, 20)
            Sum(a, Op.TIMES, b, Op.MINUS, r.nextInt(11, minOf(100, a * b)))
        }
        else -> Sum(r.nextInt(11, 100), Op.PLUS, r.nextInt(6, 13), Op.TIMES, r.nextInt(11, 20))
    }
}

/**
 * One attempt at dismissing: the current sum and the wrong answers given for it. After [MAX_WRONG] wrong answers
 * there's a fresh sum, so nobody is stuck on one they can't do.
 */
class ChallengeSession(private val level: Int, private val random: Random = Random.Default) {
    var sum: Sum = SumGenerator.generate(level, random)
        private set
    var wrong: Int = 0
        private set

    sealed interface Result {
        data object Correct : Result
        data class Wrong(val triesLeft: Int) : Result
        data object NewSum : Result
    }

    fun submit(answer: Int?): Result {
        if (answer != null && answer == sum.answer) return Result.Correct
        wrong++
        if (wrong >= MAX_WRONG) {
            sum = SumGenerator.generate(level, random)
            wrong = 0
            return Result.NewSum
        }
        return Result.Wrong(MAX_WRONG - wrong)
    }

    companion object {
        const val MAX_WRONG = 3
        const val MAX_DIGITS = 4
    }
}
