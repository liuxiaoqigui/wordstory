package com.wordstory.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            xp != null -> Result(pad, "过关！", xp!!, s!!.summary(), onExit)
            s != null -> QuizView(s, pad) {
                val today = today()
                val earned = LESSON_XP + s.firstTryCorrect
                r.user.learn(words.map { it.word }, today)
                s.missedWords.forEach { r.user.toughAnswer(it, false) }   // 闯关答错的词进错词本
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
    var revealed by remember(s.step) { mutableStateOf(false) }   // 听音题：听不了时可以改看单词
    val listening = q is MeaningQuestion && q.listen && picked == null && !revealed
    val speech = LocalWordSpeech.current
    LaunchedEffect(s.step) { if (listening) speech?.speak(q.word.word) }
    Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Prompt(
                when {
                    q is ClozeQuestion -> "选词填空"
                    q is PhoneticQuestion -> "看音标，选出单词"
                    listening -> "听发音，选出意思"
                    else -> "选出正确的意思"
                },
            )
            when (q) {
                is MeaningQuestion -> if (listening) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DuoButton("🔊 再听一遍", onClick = { speech?.speak(q.word.word) }, modifier = Modifier.weight(1f), face = DuoBlue, lip = DuoBlueDark)
                        Text("显示单词", modifier = Modifier.clickable { revealed = true }.padding(8.dp),
                            color = DuoWolf, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                } else {
                    PronounceableWord(q.word.word, fontSize = 36.sp, lineHeight = 40.sp)
                    q.word.phonetic?.let {
                        Text("/$it/", color = DuoBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
                is PhoneticQuestion -> {
                    Text("/${q.word.phonetic}/", color = DuoBlue, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp)
                    if (picked != null) q.word.meaning?.let {
                        Text(it, color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
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
                title = if (!ok) "正确答案" else if (s.combo >= 3) "连对 ×${s.combo}！" else "太棒了！",
                detail = if (ok) null else q.options[q.answer],
                button = if (ok) "继续" else "知道了",
                onClick = { s.next(ok) },
            )
        }
    }
}

@Composable
private fun Prompt(text: String, face: String = "🦉") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(68.dp)) {
            Box(Modifier.fillMaxSize().background(DuoGreenDark, CircleShape))
            Box(
                Modifier.fillMaxSize().padding(bottom = 4.dp).background(DuoGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(face, fontSize = 32.sp) }
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
private fun QuizSession.summary() = "一次答对 $firstTryCorrect / $total" + if (maxCombo >= 3) " · 最高连对 $maxCombo" else ""

@Composable
private fun Result(pad: PaddingValues, title: String, xp: Int, detail: String, onExit: () -> Unit) {
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
                detail,
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
fun ReviewScreen(r: Repo, tough: Boolean = false, onExit: () -> Unit) {
    val session by produceState<QuizSession?>(null) {
        value = withContext(Dispatchers.IO) {
            QuizSession(reviewQuiz(r.words, r.words.words(if (tough) r.user.tough(10) else r.user.due(today(), 20))))
        }
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
            done -> Result(pad, if (tough) "错词练完了！" else "复习完成！", s.firstTryCorrect, s.summary(), onExit)
            // 每个词第一次作答就记进度和 XP，中途退出也不白复习
            else -> QuizView(s, pad, onFirstTry = { w, ok ->
                val today = today()
                // 错词本只动错误次数，不动复习的格子和日期
                if (tough) r.user.toughAnswer(w.word, ok) else r.user.review(w.word, ok, today)
                if (ok) r.user.addXp(today, 1)
            }) { done = true }
        }
    }
}

/** 闪电挑战：限时选义，答对一题 1 XP，最高分记在设置里 */
@Composable
fun BlitzScreen(r: Repo, level: Int, onExit: () -> Unit) {
    val questions by produceState<List<Question>?>(null) {
        value = withContext(Dispatchers.IO) {
            r.words.randomWords(level, 60).mapNotNull { buildQuestion(r.words, it, preferCloze = false) }
        }
    }
    val best = remember { r.prefs.getInt("blitz_best", 0) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(BLITZ_SECONDS) }
    var picked by remember(index) { mutableStateOf<Int?>(null) }
    val qs = questions
    val over = qs != null && (left == 0 || index >= qs.size)
    LaunchedEffect(qs != null) { if (qs != null) while (left > 0) { delay(1000); left-- } }
    LaunchedEffect(over) {
        if (over && score > 0) {
            r.user.addXp(today(), score)
            if (score > best) r.prefs.edit().putInt("blitz_best", score).apply()
        }
    }
    // 答对很快跳下一题；答错多停一会儿，看清正确答案
    LaunchedEffect(picked) { picked?.let { delay(if (it == qs!![index].answer) 250 else 1000); index++ } }
    Scaffold(
        containerColor = DuoWhite,
        contentWindowInsets = ZeroInsets,
        topBar = { TopBar(onExit, closeAsX = true, left.toFloat() / BLITZ_SECONDS) },
    ) { pad ->
        when {
            qs == null -> Loading(pad)
            over -> Result(pad, if (score > best) "新纪录！" else "时间到！", score,
                "答对 $score 题 · 最高纪录 ${maxOf(score, best)} 题", onExit)
            else -> {
                val q = qs[index]
                Column(
                    Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())
                        .navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⏱ $left 秒", color = DuoWolf, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("⚡ $score", color = DuoYellowDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Text(q.word.word, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, color = DuoEel)
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
                                if (i == q.answer) score++
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 课本上的 48 个国际音标，每个配两个词库里的例词 */
// 方括号里是发这个音的字母，显示时标蓝
val PHONETIC_GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "长元音" to listOf("iː" to "s[ee] t[ea]", "ɜː" to "h[er] f[ir]st", "ɑː" to "c[ar] f[a]ther", "ɔː" to "[a]ll m[ore]",
        "uː" to "d[o] bl[ue]"),
    "短元音" to listOf("i" to "s[i]t b[i]g", "e" to "g[e]t r[e]d", "æ" to "c[a]t [a]nd", "ʌ" to "[u]p b[u]t", "ɒ" to "n[o]t h[o]t",
        "u" to "g[oo]d b[oo]k", "ə" to "[a]bout [a]go"),
    "双元音" to listOf("ei" to "s[ay] m[a]ke", "ai" to "m[y] t[i]me", "ɔi" to "b[oy] v[oi]ce", "əu" to "g[o] kn[ow]",
        "au" to "n[ow] [ou]t", "iə" to "h[ere] y[ear]", "eə" to "[air] th[ere]", "uə" to "s[ure] p[oor]"),
    "清辅音" to listOf("p" to "[p]en ma[p]", "t" to "[t]en [t]wo", "k" to "[k]ey boo[k]", "f" to "[f]ive li[f]e",
        "θ" to "[th]ink [th]ree", "s" to "[s]ee bu[s]", "ʃ" to "[sh]e fi[sh]", "tʃ" to "[ch]ild mu[ch]", "h" to "[h]e [h]ome",
        "tr" to "[tr]ee [tr]y", "ts" to "i[ts] pan[ts]"),
    "浊辅音" to listOf("b" to "[b]ig jo[b]", "d" to "[d]ay re[d]", "g" to "[g]o bi[g]", "v" to "[v]ery fi[v]e",
        "ð" to "[th]at [th]ey", "z" to "[z]oo hi[s]", "ʒ" to "u[s]ually televi[si]on", "dʒ" to "[j]ob lar[ge]",
        "r" to "[r]ed [r]ight", "dr" to "[dr]ink [dr]y", "dz" to "besi[des] o[dds]"),
    "鼻音和其他" to listOf("m" to "[m]e ti[m]e", "n" to "[n]o te[n]", "ŋ" to "si[ng] lo[ng]", "l" to "[l]ike a[ll]",
        "j" to "[y]es [y]ou", "w" to "[w]e [w]ay"),
)

/** 台湾课本的 41 个 KK 音标 */
val KK_GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "单元音" to listOf("i" to "s[ee] t[ea]", "ɪ" to "s[i]t b[i]g", "e" to "s[ay] m[a]ke", "ɛ" to "g[e]t r[e]d", "æ" to "c[a]t [a]nd",
        "ɑ" to "h[o]t f[a]ther", "ɔ" to "[a]ll d[o]g", "o" to "g[o] kn[ow]", "ʊ" to "g[oo]d b[oo]k", "u" to "d[o] bl[ue]",
        "ʌ" to "[u]p b[u]t", "ə" to "[a]bout [a]go", "ɝ" to "h[er] f[ir]st", "ɚ" to "teach[er] sist[er]"),
    "双元音" to listOf("aɪ" to "m[y] t[i]me", "aʊ" to "n[ow] [ou]t", "ɔɪ" to "b[oy] v[oi]ce"),
    "清辅音" to listOf("p" to "[p]en ma[p]", "t" to "[t]en [t]wo", "k" to "[k]ey boo[k]", "f" to "[f]ive li[f]e",
        "θ" to "[th]ink [th]ree", "s" to "[s]ee bu[s]", "ʃ" to "[sh]e fi[sh]", "tʃ" to "[ch]ild mu[ch]", "h" to "[h]e [h]ome"),
    "浊辅音" to listOf("b" to "[b]ig jo[b]", "d" to "[d]ay re[d]", "g" to "[g]o bi[g]", "v" to "[v]ery fi[v]e",
        "ð" to "[th]at [th]ey", "z" to "[z]oo hi[s]", "ʒ" to "u[s]ually televi[si]on", "dʒ" to "[j]ob lar[ge]"),
    "鼻音和其他" to listOf("m" to "[m]e ti[m]e", "n" to "[n]o te[n]", "ŋ" to "si[ng] lo[ng]", "l" to "[l]ike a[ll]",
        "r" to "[r]ed [r]ight", "j" to "[y]es [y]ou", "w" to "[w]e [w]ay"),
)

// shortcut: 没有单个音素的录音，只能点例词听这个音；有了音素音频再给音标本身加喇叭
@Composable
fun PhoneticsScreen(db: WordDb, onBack: () -> Unit) {
    val groups = if (Lang.hant) KK_GROUPS else PHONETIC_GROUPS
    val phonetic by produceState(emptyMap<String, String?>()) {
        val words = groups.flatMap { g -> g.second.flatMap { it.second.split(" ") } }.map { splitMark(it).word }.distinct()
        value = withContext(Dispatchers.IO) { db.words(words).associate { it.word to it.phonetic } }
    }
    Scaffold(containerColor = DuoWhite, contentWindowInsets = ZeroInsets, topBar = { TopBar(onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(top = pad.calculateTopPadding()).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("音标表", fontSize = 28.sp, fontWeight = FontWeight.Black, color = DuoEel)
                Text((if (Lang.hant) "41 个 KK 音标。" else "48 个国际音标。") + "例词里标蓝的字母就读这个音，点喇叭听。", modifier = Modifier.padding(top = 4.dp),
                    color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp)
            }
            groups.forEach { (group, symbols) ->
                item(key = group) {
                    Text(group, modifier = Modifier.padding(top = 8.dp), color = DuoGreenDark, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                items(symbols, key = { it.first }) { (symbol, words) ->
                    DuoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("/$symbol/", modifier = Modifier.width(92.dp), color = DuoBlue, fontWeight = FontWeight.Black, fontSize = 26.sp)
                            Column(Modifier.weight(1f)) {
                                words.split(" ").forEach { marked ->
                                    val m = splitMark(marked)
                                    val w = m.word
                                    PronounceableWord(w, fontSize = 20.sp, lineHeight = 24.sp, text = buildAnnotatedString {
                                        append(m.before)
                                        withStyle(SpanStyle(color = DuoBlue, textDecoration = TextDecoration.Underline)) { append(m.sound) }
                                        append(m.after)
                                    })
                                    phonetic[w]?.let { Text("/$it/", color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单词配对：左边单词、右边意思，各点一个配成对；每轮 5 对，共两轮 */
@Composable
fun MatchScreen(r: Repo, level: Int, onExit: () -> Unit) {
    val rounds by produceState<List<List<Word>>?>(null) {
        // 意思相同的词只留一个，免得一个意思能配两个词
        value = withContext(Dispatchers.IO) { r.words.randomWords(level, 10).distinctBy { shortMeaning(it.meaning ?: "") }.chunked(5) }
    }
    var round by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var matched by remember { mutableStateOf(emptySet<String>()) }   // 已配好的词，跨轮累计
    var left by remember(round) { mutableStateOf<Int?>(null) }
    var right by remember(round) { mutableStateOf<Int?>(null) }
    var wrong by remember(round) { mutableStateOf<Pair<Int, Int>?>(null) }
    var cheer by remember(round) { mutableStateOf(false) }   // 刚配对成功，猫头鹰庆祝一下
    val scope = rememberCoroutineScope()
    val all = rounds
    val over = all != null && round >= all.size
    LaunchedEffect(over) { if (over) r.user.addXp(today(), MATCH_XP) }
    Scaffold(
        containerColor = DuoWhite,
        contentWindowInsets = ZeroInsets,
        topBar = { TopBar(onExit, closeAsX = true, all?.let { matched.size.toFloat() / it.sumOf { w -> w.size }.coerceAtLeast(1) }) },
    ) { pad ->
        when {
            all == null -> Loading(pad)
            over -> Result(pad, "配对完成！", MATCH_XP, "配对 ${matched.size} 组 · 配错 $mistakes 次", onExit)
            else -> {
                val words = all[round]
                val meanings = remember(round) { words.shuffled() }
                fun check() {
                    val l = left ?: return
                    val m = right ?: return
                    left = null; right = null
                    if (words[l].word == meanings[m].word) {
                        matched = matched + words[l].word
                        cheer = true
                        if (words.all { it.word in matched }) scope.launch { delay(500); round++ }
                    } else {
                        mistakes++
                        cheer = false
                        wrong = l to m
                        scope.launch { delay(600); wrong = null }
                    }
                }
                Column(
                    Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())
                        .navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        wrong != null -> Prompt("不对哦，再试试！", "🙈")
                        cheer -> Prompt("配对成功，真棒！", "🥳")
                        else -> Prompt("把单词和意思配成对")
                    }
                    val done = words.count { it.word in matched }
                    Text("⭐".repeat(done) + "☆".repeat(words.size - done), modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, fontSize = 26.sp, color = DuoHare)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            words.forEachIndexed { i, w ->
                                val tone = when {
                                    w.word in matched -> ChoiceTone.Correct
                                    wrong?.first == i -> ChoiceTone.Wrong
                                    left == i -> ChoiceTone.Selected
                                    else -> ChoiceTone.Idle
                                }
                                val (face, lip) = MatchCandy[i % MatchCandy.size]
                                MatchCard(w.word, tone, face, lip, DuoWhite) { if (wrong == null) { left = if (left == i) null else i; check() } }
                            }
                        }
                        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            meanings.forEachIndexed { i, w ->
                                val tone = when {
                                    w.word in matched -> ChoiceTone.Correct
                                    wrong?.second == i -> ChoiceTone.Wrong
                                    right == i -> ChoiceTone.Selected
                                    else -> ChoiceTone.Idle
                                }
                                MatchCard(shortMeaning(w.meaning ?: ""), tone, DuoWhite, DuoSwan, DuoEel) { if (wrong == null) { right = if (right == i) null else i; check() } }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 单词卡每张一个糖果色
private val MatchCandy = listOf(
    DuoGreen to DuoGreenDark, DuoBlue to DuoBlueDark, DuoPurple to DuoPurpleDark,
    DuoOrange to DuoOrangeDark, Color(0xFFFF86D0) to Color(0xFFE05FB0),
)

/** 配对卡片：选中变黄并放大，配对成功缩一下变绿，配错左右抖 */
@Composable
private fun MatchCard(text: String, tone: ChoiceTone, face: Color, lip: Color, fg: Color, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        when (tone) { ChoiceTone.Selected -> 1.06f; ChoiceTone.Correct -> 0.94f; else -> 1f },
        spring(dampingRatio = 0.35f), label = "pop",
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(tone) {
        if (tone == ChoiceTone.Wrong) {
            repeat(3) { shake.animateTo(8f, tween(40)); shake.animateTo(-8f, tween(40)) }
            shake.animateTo(0f, tween(40))
        }
    }
    val (f, l, c) = when (tone) {
        ChoiceTone.Selected -> Triple(DuoYellow, DuoYellowLip, DuoEel)
        ChoiceTone.Correct -> Triple(DuoGreenSoft, DuoGreen, DuoGreenDark)
        ChoiceTone.Wrong -> Triple(DuoRedSoft, DuoRed, DuoRedDark)
        else -> Triple(face, lip, fg)
    }
    DuoTile(
        f, l, onClick,
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; translationX = shake.value.dp.toPx() },
        enabled = tone == ChoiceTone.Idle || tone == ChoiceTone.Selected,   // 选中的可以再点一下取消
    ) {
        Text(
            if (tone == ChoiceTone.Correct) "⭐ $text" else text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            color = c, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
    }
}

/** 中译英：看中文释义，输入英文单词。每题只答一次，答对一题 1 XP。tiles = 拼写积木，点打乱的字母块代替键盘 */
@Composable
fun SpellScreen(r: Repo, level: Int, tiles: Boolean, onExit: () -> Unit) {
    val words by produceState<List<Word>?>(null) {
        value = withContext(Dispatchers.IO) { r.words.randomWords(level, SPELL_QUESTIONS) }
    }
    var index by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var input by remember(index) { mutableStateOf("") }
    var ok by remember(index) { mutableStateOf<Boolean?>(null) }   // 不为空就是已经检查过
    val all = words
    val over = all != null && index >= all.size
    LaunchedEffect(over) { if (over && correct > 0) r.user.addXp(today(), correct) }
    val focus = remember { FocusRequester() }
    Scaffold(
        containerColor = DuoWhite,
        contentWindowInsets = ZeroInsets,
        topBar = { TopBar(onExit, closeAsX = true, all?.let { index.toFloat() / it.size.coerceAtLeast(1) }) },
    ) { pad ->
        when {
            all == null -> Loading(pad)
            over -> Result(pad, if (tiles) "拼写完成！" else "翻译完成！", correct, "答对 $correct / ${all.size}", onExit)
            else -> {
                val w = all[index]
                val check = { if (ok == null && input.isNotBlank()) { ok = spelledRight(input, w.word); if (ok == true) correct++ } }
                if (!tiles) LaunchedEffect(index) { focus.requestFocus() }
                Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().imePadding()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Prompt(if (tiles) "点字母，拼出这个意思的单词" else "写出这个意思的英文单词")
                        Text(shortMeaning(w.meaning ?: ""), color = DuoEel, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 32.sp)
                        // 一个中文意思可能对应好几个词，给出首字母和长度，答案才唯一
                        if (tiles) {
                            val letters = remember(index) { w.word.toList().shuffled() }
                            val picked = remember(index) { mutableStateListOf<Int>() }   // 按点的顺序记字母块的下标
                            fun sync() { input = picked.joinToString("") { letters[it].toString() } }
                            // 拼出来的词，点一下退掉最后一个字母
                            Text(
                                input.ifEmpty { " " },
                                modifier = Modifier.fillMaxWidth().bordered()
                                    .clickable(enabled = ok == null && picked.isNotEmpty()) { picked.removeAt(picked.lastIndex); sync() }
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                color = DuoEel, fontWeight = FontWeight.Black, fontSize = 28.sp, textAlign = TextAlign.Center,
                                letterSpacing = 3.sp,
                            )
                            Text("点上面的框可以退掉一个字母", color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            letters.indices.chunked(6).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { i ->
                                        val used = i in picked
                                        DuoTile(
                                            if (used) DuoPolar else DuoWhite, DuoSwan, { picked += i; sync() },
                                            Modifier.width(50.dp), enabled = !used && ok == null,
                                        ) {
                                            Text(letters[i].toString(), color = if (used) DuoSwan else DuoEel, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                        Text("提示：以 ${w.word.first()} 开头，共 ${w.word.length} 个字母", color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { if (ok == null) input = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            singleLine = true,
                            placeholder = { Text("输入英文", color = DuoHare, fontWeight = FontWeight.Bold) },
                            textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = DuoEel),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { check() }),
                        )
                        }
                    }
                    val result = ok
                    if (result == null) {
                        Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp).navigationBarsPadding()) {
                            DuoButton("检查", check, enabled = input.isNotBlank())
                        }
                    } else {
                        FeedbackBar(
                            ok = result,
                            title = if (result) "太棒了！" else "正确答案",
                            detail = if (result) null else w.word,
                            button = if (result) "继续" else "知道了",
                            onClick = { index++ },
                        )
                    }
                }
            }
        }
    }
}
