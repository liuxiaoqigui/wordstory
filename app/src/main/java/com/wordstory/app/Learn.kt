package com.wordstory.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlin.random.Random

const val MASTERED_BOX = 6
const val DAILY_GOAL = 20
const val LESSON_XP = 10
const val BLITZ_SECONDS = 60

// 下标 = 格子号：第 1 格隔 1 天复习，第 5 格隔 15 天，第 6 格算掌握，30 天后再看一眼
private val INTERVAL_DAYS = intArrayOf(0, 1, 2, 4, 7, 15, 30)

fun today() = LocalDate.now().toEpochDay()
fun nextBox(box: Int, correct: Boolean) = if (correct) minOf(box + 1, MASTERED_BOX) else 1
fun dueDay(today: Long, box: Int) = today + INTERVAL_DAYS[box]

/** 连续打卡天数；今天还没学不算断，从昨天往前数 */
fun streak(xpDays: Set<Long>, today: Long): Int {
    var day = if (today in xpDays) today else today - 1
    var n = 0
    while (day in xpDays) { n++; day-- }
    return n
}

sealed interface Question {
    val word: Word
    val options: List<String>
    val answer: Int
}

/** listen = 听音辨义：只播发音，不显示单词 */
data class MeaningQuestion(
    override val word: Word, override val options: List<String>, override val answer: Int, val listen: Boolean = false,
) : Question
data class ClozeQuestion(
    override val word: Word, val example: Example, override val options: List<String>, override val answer: Int,
) : Question

/** 看音标，选出单词 */
data class PhoneticQuestion(override val word: Word, override val options: List<String>, override val answer: Int) : Question

/** 一个词出一道题：有例句就可能出填空，有释义就可能出选义 */
fun buildQuestion(
    db: WordDb, w: Word, preferCloze: Boolean, listen: Boolean = false, phonetic: Boolean = false,
): Question? {
    val examples = w.examples()
    return when {
        phonetic && w.phonetic != null -> {
            val options = (db.distractorWords(w, 3) + w.word).shuffled()
            PhoneticQuestion(w, options, options.indexOf(w.word))
        }
        examples.isNotEmpty() && (preferCloze || w.meaning == null) -> {
            val options = (db.distractorWords(w, 3) + w.word).shuffled()
            ClozeQuestion(w, examples.random(), options, options.indexOf(w.word))
        }
        w.meaning != null -> {
            val options = (db.distractorMeanings(w, 3) + w.meaning).shuffled()
            MeaningQuestion(w, options, options.indexOf(w.meaning), listen)
        }
        else -> null
    }
}

fun lessonQuiz(db: WordDb, words: List<Word>) =
    words.mapIndexedNotNull { i, w -> buildQuestion(db, w, preferCloze = i % 2 == 1, listen = i % 8 == 0, phonetic = i % 8 == 4) }.shuffled()

fun reviewQuiz(db: WordDb, words: List<Word>) = words.mapNotNull {
    val kind = Random.nextInt(6)
    buildQuestion(db, it, preferCloze = Random.nextBoolean(), listen = kind == 0, phonetic = kind == 1)
}.shuffled()

/** 答错的题放回队尾，全部答对才算过关（多邻国的玩法）。复习只按每个词第一次作答升降格子。 */
class QuizSession(questions: List<Question>) {
    private val queue = ArrayDeque(questions)
    private val attempted = mutableSetOf<String>()
    private val missed = mutableSetOf<String>()
    val total = questions.size
    var current by mutableStateOf(queue.firstOrNull()); private set
    var solved by mutableIntStateOf(0); private set
    /** 每换一题加一，界面用它重置选中状态（同一道题答错后可能马上又轮到它） */
    var step by mutableIntStateOf(0); private set
    val firstTryCorrect get() = attempted.size - missed.size
    /** 当前连续答对的题数，答错清零 */
    var combo = 0; private set
    var maxCombo = 0; private set

    fun check(choice: Int, onFirstTry: (Word, Boolean) -> Unit = { _, _ -> }): Boolean {
        val q = current ?: return false
        val ok = choice == q.answer
        combo = if (ok) combo + 1 else 0
        maxCombo = maxOf(maxCombo, combo)
        if (attempted.add(q.word.word)) {
            if (!ok) missed += q.word.word
            onFirstTry(q.word, ok)
        }
        return ok
    }

    fun next(correct: Boolean) {
        val q = queue.removeFirst()
        if (correct) solved++ else queue.addLast(q)
        current = queue.firstOrNull()
        step++
    }
}
