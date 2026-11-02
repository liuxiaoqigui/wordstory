package com.wordstory.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.io.File

// 分级来自 ECDICT 的考试标签：1 中考、2 高考、3 四级、4 六级、5 不在这四类里
val LEVEL_NAMES = mapOf(1 to "入门", 2 to "简单", 3 to "困难", 4 to "地狱", 5 to "其他")

data class Example(val en: String, val zh: String, val blank: String)

data class Word(
    val word: String,
    val phonetic: String?,
    val meaning: String?,
    val pos: String?,
    val level: Int,
    val lesson: Int,
    val sectionsJson: String = "[]",
    val examplesJson: String = "[]",
) {
    /** [(小节名, 正文)]；小节名为空的是开头没有标题的那段 */
    fun sections(): List<Pair<String, String>> = JSONArray(sectionsJson).let { a ->
        List(a.length()) { i -> a.getJSONObject(i).let { it.getString("t") to it.getString("b") } }
    }

    fun examples(): List<Example> = JSONArray(examplesJson).let { a ->
        List(a.length()) { i -> a.getJSONObject(i).let { Example(it.getString("en"), it.getString("zh"), it.getString("blank")) } }
    }
}

data class LessonInfo(val lesson: Int, val level: Int, val words: List<String>)

/** 点词查询的结果：head 是原形（spiders -> spider），story 是单词书里能看故事的词条 */
data class Lookup(val head: String, val phonetic: String?, val meaning: String?, val story: String?)

/** 只读词库：APK 里的 assets/words.db 在首次启动或 App 更新后拷到 databases/ 再打开。 */
class WordDb(context: Context) {
    private val db: SQLiteDatabase

    init {
        val file = context.getDatabasePath("words.db")
        val prefs = context.getSharedPreferences("wordsdb", Context.MODE_PRIVATE)
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (!file.exists() || prefs.getLong("copied_for", 0) != installed) {
            file.parentFile!!.mkdirs()
            val tmp = File(file.path + ".tmp")
            context.assets.open("words.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            // 先写临时文件再改名：拷到一半被杀也不会留下半截的库
            check(tmp.renameTo(file)) { "rename words.db failed" }
            prefs.edit().putLong("copied_for", installed).apply()
        }
        db = SQLiteDatabase.openDatabase(
            file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
    }

    private fun query(sql: String, vararg args: String): List<Word> = db.rawQuery(sql, args).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Word(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getInt(4), c.getInt(5), c.getString(6), c.getString(7)),
            )
        }
    }

    /** 按前缀查，不分大小写；完全相同的排最前，然后短的在前 */
    fun search(prefix: String): List<Word> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()
        val like = p.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return query("SELECT $COLS FROM words WHERE word LIKE ? ESCAPE '\\' " +
            "ORDER BY lower(word) = lower(?) DESC, length(word), word LIMIT 50", like, p)
    }

    fun word(word: String): Word? = query("SELECT $COLS FROM words WHERE word = ?", word).firstOrNull()

    /** 句子里点到的词：先整词查，he's 这种查不到再查撇号前的 he */
    fun lookup(surface: String): Lookup? {
        val token = surface.lowercase().replace('’', '\'')
        return findToken(token) ?: token.substringBefore('\'').takeIf { it != token }?.let(::findToken)
    }

    private fun findToken(token: String): Lookup? =
        db.rawQuery("SELECT head, phonetic, meaning, story FROM lookup WHERE token = ?", arrayOf(token)).use { c ->
            if (c.moveToFirst()) Lookup(c.getString(0), c.getString(1), c.getString(2), c.getString(3)) else null
        }

    fun lessonWords(lesson: Int): List<Word> =
        query("SELECT $COLS FROM words WHERE lesson = ? ORDER BY id", lesson.toString())

    fun words(list: Collection<String>): List<Word> = list.chunked(500).flatMap { part ->
        query("SELECT $COLS FROM words WHERE word IN (${part.joinToString(",") { "?" }})", *part.toTypedArray())
    }

    // lesson = 0 是只供查词的词条（About/about 这类大小写重复）
    fun lessons(): List<LessonInfo> = db.rawQuery("SELECT lesson, level, word FROM words WHERE lesson > 0 ORDER BY id", null).use { c ->
        val map = LinkedHashMap<Int, Pair<Int, MutableList<String>>>()
        while (c.moveToNext()) map.getOrPut(c.getInt(0)) { c.getInt(1) to mutableListOf() }.second += c.getString(2)
        map.map { (lesson, v) -> LessonInfo(lesson, v.first, v.second) }
    }

    fun distractorWords(w: Word, n: Int) = pick("word", w, n)
    fun distractorMeanings(w: Word, n: Int) = pick("meaning", w, n)

    /** 干扰项：优先同词性同等级，不够再放宽；排除和答案同形（March/march）或同义的词 */
    private fun pick(col: String, w: Word, n: Int): List<String> {
        val out = LinkedHashSet<String>()
        fun run(extra: String, vararg args: String) {
            if (out.size >= n) return
            db.rawQuery(
                "SELECT DISTINCT $col FROM words WHERE lesson > 0 AND meaning IS NOT NULL AND lower(word) != lower(?) " +
                    "AND meaning != ? $extra ORDER BY random() LIMIT ${n * 2}",
                arrayOf(w.word, w.meaning ?: "", *args),
            ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        }
        if (w.pos != null) run("AND pos = ? AND level = ?", w.pos, w.level.toString())
        run("AND level = ?", w.level.toString())
        run("")
        return out.take(n)
    }

    private companion object {
        const val COLS = "word, phonetic, meaning, pos, level, lesson, sections, examples"
    }
}

/** 学习进度，按单词记（不按 id），以后重新生成词库也不会对错号。 */
class UserDb(context: Context) : SQLiteOpenHelper(context, "user.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE progress(word TEXT PRIMARY KEY, box INTEGER NOT NULL, due INTEGER NOT NULL, " +
            "hits INTEGER NOT NULL DEFAULT 0, misses INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX progress_due ON progress(due)")
        db.execSQL("CREATE TABLE daily(day INTEGER PRIMARY KEY, xp INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun longs(sql: String, vararg args: String): List<Long> = readableDatabase.rawQuery(sql, args).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    }

    fun learned(): Set<String> = readableDatabase.rawQuery("SELECT word FROM progress", null).use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun due(today: Long, limit: Int): List<String> =
        readableDatabase.rawQuery("SELECT word FROM progress WHERE due <= ? ORDER BY due, random() LIMIT ?",
            arrayOf(today.toString(), limit.toString())).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun dueCount(today: Long) = longs("SELECT count(*) FROM progress WHERE due <= ?", today.toString()).first().toInt()
    fun masteredCount() = longs("SELECT count(*) FROM progress WHERE box >= $MASTERED_BOX").first().toInt()
    fun xpDays(): Set<Long> = longs("SELECT day FROM daily WHERE xp > 0").toSet()
    fun xpOn(day: Long) = longs("SELECT xp FROM daily WHERE day = ?", day.toString()).firstOrNull()?.toInt() ?: 0

    /** 学完一关：新词进第 1 格，明天复习；学过的词不动 */
    fun learn(words: List<String>, today: Long) = writableDatabase.run {
        beginTransaction()
        try {
            words.forEach { execSQL("INSERT OR IGNORE INTO progress(word, box, due) VALUES(?, 1, ?)", arrayOf<Any>(it, today + 1)) }
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    /** 复习时第一次作答的结果：答对升一格，答错回第 1 格 */
    fun review(word: String, correct: Boolean, today: Long) {
        val box = longs("SELECT box FROM progress WHERE word = ?", word).firstOrNull()?.toInt() ?: return
        val next = nextBox(box, correct)
        writableDatabase.execSQL(
            "UPDATE progress SET box = ?, due = ?, ${if (correct) "hits = hits" else "misses = misses"} + 1 WHERE word = ?",
            arrayOf<Any>(next, dueDay(today, next), word),
        )
    }

    // 不用 UPSERT：Android 8~10 自带的 SQLite 还不支持
    fun addXp(day: Long, xp: Int) = writableDatabase.run {
        execSQL("INSERT OR IGNORE INTO daily(day, xp) VALUES(?, 0)", arrayOf<Any>(day))
        execSQL("UPDATE daily SET xp = xp + ? WHERE day = ?", arrayOf<Any>(xp, day))
    }
}
