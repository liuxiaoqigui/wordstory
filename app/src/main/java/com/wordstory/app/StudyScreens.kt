package com.wordstory.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ZeroInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

/** 只处理 **加粗**：标题、列表在生成词库时已经整理成纯文本 */
fun boldMarkdown(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(part) } else append(part)
    }
}

@Composable
private fun TopBar(onClose: () -> Unit, closeAsX: Boolean = false, progress: Float? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (closeAsX) "✕" else "←", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DuoWolf)
        }
        if (progress != null) DuoProgress(progress, Modifier.weight(1f).padding(end = 4.dp))
    }
}

@Composable
private fun Loading(pad: PaddingValues = PaddingValues()) {
    Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DuoGreen, strokeWidth = 4.dp)
    }
}

@Composable
fun WordHeader(w: Word) {
    val name = LEVEL_NAMES[w.level]
    PronounceableWord(w.word)
    Row(
        Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        w.phonetic?.let {
            Text("/$it/", color = DuoBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        if (name != null) {
            Text(
                name,
                modifier = Modifier
                    .background(levelPalette(w.level).face, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
            )
        }
    }
    w.meaning?.let {
        Text(it, modifier = Modifier.padding(top = 10.dp), color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

/** 单词背后的故事：词义、例句、词根词缀、历史文化、变形、记忆、小故事 */
@Composable
fun WordSections(w: Word) {
    val sections = remember(w) { w.sections() }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sections.forEach { (title, body) ->
                DuoCard {
                    if (title.isNotEmpty()) {
                        Text(title, color = DuoGreenDark, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    TappableText(body)
                }
            }
        }
    }
}

@Composable
fun DetailScreen(db: WordDb, word: String, onBack: () -> Unit) {
    val w by produceState<Word?>(null, word) { value = withContext(Dispatchers.IO) { db.word(word) } }
    Scaffold(containerColor = DuoWhite, contentWindowInsets = ZeroInsets, topBar = { TopBar(onBack) }) { pad ->
        val x = w ?: return@Scaffold Loading(pad)
        Column(
            Modifier
                .padding(top = pad.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            WordHeader(x)
            Spacer(Modifier.height(16.dp))
            WordSections(x)
        }
    }
}

@Composable
fun LessonScreen(r: Repo, info: LessonInfo, onExit: () -> Unit) {
    val words by produceState(emptyList<Word>(), info.lesson) {
        value = withContext(Dispatchers.IO) { r.words.lessonWords(info.lesson) }
    }
    var card by remember { mutableIntStateOf(0) }
    var session by remember { mutableStateOf<QuizSession?>(null) }
    var xp by remember { mutableStateOf<Int?>(null) }   // 不为空就是已结算
    val speech = LocalWordSpeech.current
    LaunchedEffect(words, card, speech) {
        speech?.prefetch(words.drop(card).take(3).map { it.word })
    }
    val scope = rememberCoroutineScope()
    val s = session
    val progress = when {
        words.isEmpty() -> 0f
        s == null -> (card + 1f) / words.size / 2   // 前一半进度条是学习卡片，后一半是测验
        else -> 0.5f + s.solved.toFloat() / s.total.coerceAtLeast(1) / 2
    }
    Scaffold(
        containerColor = DuoWhite,
        contentWindowInsets = ZeroInsets,
        topBar = { TopBar(onExit, closeAsX = true, progress) },
    ) { pad ->
        when {
            words.isEmpty() -> Loading(pad)
            xp != null -> Result(pad, "过关！", xp!!, s!!, onExit)
            s != null -> QuizView(s, pad) {
                val today = today()
                val earned = LESSON_XP + s.firstTryCorrect
                r.user.learn(words.map { it.word }, today)
                r.user.addXp(today, earned)
                xp = earned
            }
            else -> key(card) {
                LearnCard(words[card], pad, last = card == words.lastIndex) {
                    if (card < words.lastIndex) card++
                    else scope.launch { session = withContext(Dispatchers.IO) { QuizSession(lessonQuiz(r.words, words)) } }
                }
            }
        }
    }
}

@Composable
private fun LearnCard(w: Word, pad: PaddingValues, last: Boolean, onNext: () -> Unit) {
    var showStory by remember { mutableStateOf(false) }
    val example = remember(w) { w.examples().firstOrNull() }
    Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            WordHeader(w)
            example?.let {
                DuoCard(Modifier.padding(top = 16.dp)) {
                    TappableText(it.en, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(it.zh, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DuoWolf, lineHeight = 22.sp)
                }
            }
            DuoButton(
                if (showStory) "收起故事" else "看看这个词背后的故事",
                onClick = { showStory = !showStory },
                modifier = Modifier.padding(top = 12.dp),
                face = DuoWhite,
                lip = DuoSwan,
                contentColor = DuoBlue,
                outline = true,
            )
            if (showStory) {
                Spacer(Modifier.height(12.dp))
                WordSections(w)
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp).navigationBarsPadding()) {
            DuoButton(if (last) "开始测验" else "下一个", onNext)
        }
    }
}

@Composable
private fun QuizView(
    s: QuizSession, pad: PaddingValues, onFirstTry: (Word, Boolean) -> Unit = { _, _ -> }, onFinish: () -> Unit,
) {
    val q = s.current
    if (q == null) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }
    var picked by remember(s.step) { mutableStateOf<Int?>(null) }
    Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Prompt(if (q is ClozeQuestion) "选词填空" else "选出正确的意思")
            when (q) {
                is MeaningQuestion -> {
                    PronounceableWord(q.word.word, fontSize = 36.sp, lineHeight = 40.sp)
                    q.word.phonetic?.let {
                        Text("/$it/", color = DuoBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
                is ClozeQuestion -> {
                    TappableText(
                        if (picked == null) q.example.blank else q.example.en,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                    )
                    if (picked != null) {
                        Text(q.example.zh, color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
                    }
                }
            }
            q.options.forEachIndexed { i, option ->
                val tone = when {
                    picked == null -> ChoiceTone.Idle
                    i == q.answer -> ChoiceTone.Correct
                    i == picked -> ChoiceTone.Wrong
                    else -> ChoiceTone.Dim
                }
                DuoChoice(option, tone) {
                    if (picked == null) {
                        picked = i
                        s.check(i, onFirstTry)
                    }
                }
            }
        }
        val choice = picked
        if (choice == null) {
            Spacer(Modifier.navigationBarsPadding())
        } else {
            val ok = choice == q.answer
            FeedbackBar(
                ok = ok,
                title = if (ok) "太棒了！" else "正确答案",
                detail = if (ok) null else q.options[q.answer],
                button = if (ok) "继续" else "知道了",
                onClick = { s.next(ok) },
            )
        }
    }
}

@Composable
private fun Prompt(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(68.dp)) {
            Box(Modifier.fillMaxSize().background(DuoGreenDark, CircleShape))
            Box(
                Modifier.fillMaxSize().padding(bottom = 4.dp).background(DuoGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("🦉", fontSize = 32.sp) }
        }
        Text(
            text,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
                .bordered()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = DuoEel,
        )
    }
}

private fun Modifier.bordered(): Modifier {
    val shape = RoundedCornerShape(16.dp)
    return clip(shape).background(DuoWhite).border(2.dp, DuoSwan, shape)
}

@Composable
private fun FeedbackBar(ok: Boolean, title: String, detail: String?, button: String, onClick: () -> Unit) {
    val bg = if (ok) DuoGreenSoft else DuoRedSoft
    val fg = if (ok) DuoGreenDark else DuoRed
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        Text(title, color = fg, fontWeight = FontWeight.Black, fontSize = 22.sp)
        if (detail != null) {
            Text(detail, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        DuoButton(
            button,
            onClick,
            face = if (ok) DuoGreen else DuoRed,
            lip = if (ok) DuoGreenDark else DuoRedDark,
        )
    }
}

@Composable
private fun Result(pad: PaddingValues, title: String, xp: Int, s: QuizSession, onExit: () -> Unit) {
    BoxWithConstraints(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(120.dp)) {
                Box(Modifier.fillMaxSize().background(DuoYellowLip, CircleShape))
                Box(
                    Modifier.fillMaxSize().padding(bottom = 6.dp).background(DuoYellow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("★", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black) }
            }
            Text(title, modifier = Modifier.padding(top = 16.dp), fontSize = 32.sp, fontWeight = FontWeight.Black, color = DuoEel)
            Text("+$xp XP", modifier = Modifier.padding(top = 4.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = DuoYellowDark)
            Text(
                "一次答对 ${s.firstTryCorrect} / ${s.total}",
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DuoWolf,
            )
            DuoButton("继续", onExit)
        }
    }
}

@Composable
fun ReviewScreen(r: Repo, onExit: () -> Unit) {
    val session by produceState<QuizSession?>(null) {
        value = withContext(Dispatchers.IO) { QuizSession(reviewQuiz(r.words, r.words.words(r.user.due(today(), 20)))) }
    }
    var done by remember { mutableStateOf(false) }
    val s = session
    Scaffold(
        containerColor = DuoWhite,
        contentWindowInsets = ZeroInsets,
        topBar = { TopBar(onExit, closeAsX = true, s?.let { it.solved.toFloat() / it.total.coerceAtLeast(1) }) },
    ) { pad ->
        when {
            s == null -> Loading(pad)
            done -> Result(pad, "复习完成！", s.firstTryCorrect, s, onExit)
            // 每个词第一次作答就记进度和 XP，中途退出也不白复习
            else -> QuizView(s, pad, onFirstTry = { w, ok ->
                val today = today()
                r.user.review(w.word, ok, today)
                if (ok) r.user.addXp(today, 1)
            }) { done = true }
        }
    }
}
