package com.wordstory.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Stats(val learned: Set<String>, val due: Int, val mastered: Int, val xpToday: Int, val streak: Int)

private val ZeroInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

@Composable
fun HomeScreen(r: Repo, st: HomeState, refresh: Int, open: (Screen) -> Unit) {
    val stats by produceState<Stats?>(null, refresh) {
        value = withContext(Dispatchers.IO) {
            val today = today()
            Stats(r.user.learned(), r.user.dueCount(today), r.user.masteredCount(), r.user.xpOn(today),
                streak(r.user.xpDays(), today))
        }
    }
    val lessons by produceState<List<LessonInfo>?>(null) { value = withContext(Dispatchers.IO) { r.lessons } }

    Scaffold(
        containerColor = DuoWhite,
        contentWindowInsets = ZeroInsets,
        topBar = { stats?.let { Header(it) } },
        bottomBar = { DuoNav(st.tab) { st.tab = it } },
    ) { pad ->
        val s = stats
        when (st.tab) {
            0 -> {
                val list = lessons
                if (s == null || list == null) LoadingBox(pad)
                else LessonsTab(list, s.learned, st, pad) { open(Screen.Lesson(it)) }
            }
            1 -> if (s == null) LoadingBox(pad) else ReviewTab(s, pad) { open(Screen.Review) }
            else -> SearchTab(r.words, st, pad) { open(Screen.Detail(it)) }
        }
    }
}

@Composable
private fun LoadingBox(pad: PaddingValues) {
    Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DuoGreen, strokeWidth = 4.dp)
    }
}

@Composable
private fun Header(s: Stats) {
    val ratio = (s.xpToday.toFloat() / DAILY_GOAL).coerceAtMost(1f)
    val done = s.xpToday >= DAILY_GOAL
    Column(Modifier.fillMaxWidth().background(DuoWhite).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥", fontSize = 18.sp)
            Text(
                "${s.streak}",
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = DuoOrange,
            )
            Spacer(Modifier.weight(1f))
            Text("⚡", fontSize = 18.sp)
            Text(
                "${s.xpToday}",
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = DuoYellowDark,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoProgress(ratio, Modifier.weight(1f), color = if (done) DuoYellow else DuoGreen)
            Text(
                if (done) "达成" else "${s.xpToday}/$DAILY_GOAL",
                modifier = Modifier.padding(start = 10.dp),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = if (done) DuoYellowDark else DuoWolf,
            )
        }
        HorizontalDivider(thickness = 2.dp, color = DuoSwan)
    }
}

private data class TabSpec(val icon: String, val label: String, val color: androidx.compose.ui.graphics.Color)

@Composable
private fun DuoNav(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        TabSpec("📚", "学习", DuoGreen),
        TabSpec("🔁", "复习", DuoBlue),
        TabSpec("🔍", "查词", DuoPurple),
    )
    Column(Modifier.background(DuoWhite).navigationBarsPadding()) {
        HorizontalDivider(thickness = 2.dp, color = DuoSwan)
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            tabs.forEachIndexed { i, tab ->
                val on = selected == i
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(i) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(if (on) tab.color.copy(alpha = 0.16f) else DuoWhite, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(tab.icon, fontSize = 18.sp) }
                    Text(
                        tab.label,
                        color = if (on) tab.color else DuoHare,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// 关卡在竖线上左右摆，和多邻国的路径一样。相邻两行的连线在交界处拼上。
private val LaneStep = 44.dp
private val NodeSize = 72.dp
private val BubbleHeight = 34.dp
private val NormalRow = 108.dp
private val NormalCenter = 46.dp
private val CurrentRow = NormalRow + BubbleHeight
private val CurrentCenter = NormalCenter + BubbleHeight
private val Lanes = intArrayOf(0, 1, 2, 1, 0, -1, -2, -1)

private fun laneOf(number: Int) = Lanes[(number - 1) % Lanes.size]
private fun rowHeight(current: Boolean) = if (current) CurrentRow else NormalRow
private fun centerY(current: Boolean) = if (current) CurrentCenter else NormalCenter

private data class LessonRow(
    val info: LessonInfo,
    val number: Int,
    val done: Boolean,
    val unlocked: Boolean,
    val lane: Int,
    val prevLane: Int?,
    val nextLane: Int?,
    val prevDone: Boolean,
    val prevLesson: Int?,
    val nextLessonId: Int?,
)

@Composable
private fun LessonsTab(
    lessons: List<LessonInfo>, learned: Set<String>, st: HomeState, pad: PaddingValues,
    onOpen: (LessonInfo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 每个等级的第一关直接开放，之后要先学完上一关
    val rows = remember(lessons, learned) {
        var number = 0
        val built = ArrayList<LessonRow>(lessons.size)
        lessons.forEachIndexed { i, l ->
            val newLevel = i == 0 || lessons[i - 1].level != l.level
            number = if (newLevel) 1 else number + 1
            val prev = if (newLevel) null else built.last()
            built += LessonRow(
                info = l,
                number = number,
                done = l.words.all { it in learned },
                unlocked = newLevel || lessons[i - 1].words.all { it in learned },
                lane = laneOf(number),
                prevLane = prev?.lane,
                nextLane = null,
                prevDone = prev?.done == true,
                prevLesson = prev?.info?.lesson,
                nextLessonId = null,
            )
        }
        for (i in built.indices) {
            val next = built.getOrNull(i + 1)?.takeIf { it.number != 1 } ?: continue
            built[i] = built[i].copy(nextLane = next.lane, nextLessonId = next.info.lesson)
        }
        built
    }
    // 只显示当前选的难度；关卡编号和解锁本来就按难度各自计算
    val shown = rows.filter { it.info.level == st.level }
    val next = shown.firstOrNull { !it.done && it.unlocked }
    if (shown.isEmpty()) {
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("词库是空的", fontWeight = FontWeight.ExtraBold, color = DuoWolf)
        }
        return
    }
    LazyColumn(
        state = st.lessonList,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = pad.calculateTopPadding() + 12.dp,
            bottom = pad.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "levels") {
            LevelChips(st.level) {
                st.selectLevel(it)
                scope.launch { st.lessonList.scrollToItem(0) }
            }
        }
        item(key = "next") {
            if (next != null) ContinueCard(next) { onOpen(next.info) }
            else DoneCard()
        }
        shown.forEach { row ->
            if (row.number == 1) item(key = "level${row.info.level}") {
                LevelBanner(row.info.level, total = shown.size, done = shown.count { it.done })
            }
            item(key = row.info.lesson) {
                val currentId = next?.info?.lesson
                PathNode(
                    row = row,
                    current = row.info.lesson == currentId,
                    prevCurrent = row.prevLesson == currentId,
                    nextCurrent = row.nextLessonId == currentId,
                    onOpen = { onOpen(row.info) },
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(row: LessonRow, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DuoWhite)
            .border2()
            .padding(16.dp),
    ) {
        Text("继续学习", color = DuoGreenDark, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text(
            "${LEVEL_NAMES[row.info.level]} · 第 ${row.number} 关",
            modifier = Modifier.padding(top = 2.dp),
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = DuoEel,
        )
        Text(
            row.info.words.joinToString("  ·  "),
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = DuoWolf,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        DuoButton("开始", onOpen)
    }
}

@Composable
private fun DoneCard() {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).border2().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", fontSize = 36.sp)
        Text("这个难度全部学完了", fontWeight = FontWeight.Black, fontSize = 22.sp, color = DuoEel)
        Text("换个难度，或者去复习巩固一下", fontWeight = FontWeight.Bold, color = DuoWolf, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 学习页顶部的难度切换 */
@Composable
private fun LevelChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LEVEL_NAMES.forEach { (level, name) ->
            val on = level == selected
            val palette = levelPalette(level)
            Text(
                name,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) palette.face else DuoWhite)
                    .border(2.dp, if (on) palette.lip else DuoSwan, RoundedCornerShape(12.dp))
                    .clickable { onSelect(level) }
                    .padding(vertical = 8.dp),
                color = if (on) DuoWhite else DuoWolf,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 第一次打开时选从哪个难度开始，之后在学习页顶部随时能换 */
@Composable
fun LevelPicker(r: Repo, onPick: (Int) -> Unit) {
    val counts by produceState(emptyMap<Int, Int>()) {
        value = withContext(Dispatchers.IO) { r.lessons.groupBy { it.level }.mapValues { (_, l) -> l.sumOf { it.words.size } } }
    }
    Column(
        Modifier.fillMaxSize().background(DuoWhite).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🦉", fontSize = 56.sp, modifier = Modifier.padding(top = 24.dp))
        Text("从哪里开始？", fontWeight = FontWeight.Black, fontSize = 30.sp, color = DuoEel)
        Text("选一个适合你的难度，之后在学习页顶部随时可以切换。", fontWeight = FontWeight.Bold, color = DuoWolf,
            fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
        LEVEL_NAMES.forEach { (level, name) ->
            val palette = levelPalette(level)
            val count = counts[level]?.let { " · $it 词" } ?: ""
            DuoButton("$name　${LEVEL_HINTS[level]}$count", onClick = { onPick(level) }, face = palette.face, lip = palette.lip)
        }
    }
}

private fun Modifier.border2(): Modifier = this.border(
    2.dp, DuoSwan, RoundedCornerShape(16.dp),
)

@Composable
private fun LevelBanner(level: Int, total: Int, done: Int) {
    val palette = levelPalette(level)
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).clip(shape)) {
        Box(Modifier.matchParentSize().background(palette.lip))
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp).background(palette.face).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(LEVEL_NAMES[level] ?: "", color = DuoWhite, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "已完成 $done / $total 关",
                color = DuoWhite.copy(alpha = 0.92f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            DuoProgress(
                if (total == 0) 0f else done.toFloat() / total,
                modifier = Modifier.fillMaxWidth(),
                color = DuoWhite,
                track = DuoWhite.copy(alpha = 0.35f),
                barHeight = 10.dp,
            )
        }
    }
}

@Composable
private fun PathNode(
    row: LessonRow, current: Boolean, prevCurrent: Boolean, nextCurrent: Boolean, onOpen: () -> Unit,
) {
    val height = rowHeight(current)
    Box(Modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = LaneStep.toPx()
            fun x(lane: Int) = size.width / 2f + lane * step
            fun center(isCurrent: Boolean) = centerY(isCurrent).toPx()
            val y = center(current)
            row.prevLane?.let { prev ->
                val fromH = rowHeight(prevCurrent).toPx()
                val fromC = center(prevCurrent)
                val t = (fromH - fromC) / (fromH - fromC + y)
                val x1 = x(prev)
                val x2 = x(row.lane)
                drawLine(
                    color = if (row.prevDone) DuoYellow else DuoSwan,
                    start = Offset(x1 + (x2 - x1) * t, 0f),
                    end = Offset(x2, y),
                    strokeWidth = 14.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            row.nextLane?.let { next ->
                val fromH = size.height
                val toC = center(nextCurrent)
                val t = (fromH - y) / (fromH - y + toC)
                val x1 = x(row.lane)
                val x2 = x(next)
                drawLine(
                    color = if (row.done) DuoYellow else DuoSwan,
                    start = Offset(x1, y),
                    end = Offset(x1 + (x2 - x1) * t, size.height),
                    strokeWidth = 14.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        val top = centerY(current) - NodeSize / 2 - if (current) BubbleHeight else 0.dp
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(x = LaneStep * row.lane)
                .padding(top = top)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = row.unlocked,
                    onClick = onOpen,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (current) StartBubble()
            LessonNode(row, current)
        }
    }
}

@Composable
private fun StartBubble() {
    Box(Modifier.height(BubbleHeight), contentAlignment = Alignment.Center) {
        Text(
            "开始",
            modifier = Modifier
                .background(DuoWhite, RoundedCornerShape(12.dp))
                .border(2.dp, DuoSwan, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = DuoEel,
        )
    }
}

@Composable
private fun LessonNode(row: LessonRow, current: Boolean) {
    val face: androidx.compose.ui.graphics.Color
    val lip: androidx.compose.ui.graphics.Color
    val fg: androidx.compose.ui.graphics.Color
    when {
        row.done -> {
            face = DuoYellow; lip = DuoYellowLip; fg = DuoWhite
        }
        row.unlocked -> {
            face = DuoGreen; lip = DuoGreenDark; fg = DuoWhite
        }
        else -> {
            face = DuoLock; lip = DuoLockLip; fg = DuoHare
        }
    }
    val bob = if (current) rememberBob() else 0.dp
    Box(Modifier.size(NodeSize).clip(CircleShape)) {
        Box(Modifier.fillMaxSize().background(lip))
        Box(
            Modifier.fillMaxSize().padding(bottom = 6.dp).offset(y = bob).background(face, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${row.number}",
                color = fg,
                fontWeight = FontWeight.Black,
                fontSize = if (row.number >= 100) 16.sp else 22.sp,
            )
        }
    }
}

@Composable
private fun rememberBob(): Dp {
    val transition = rememberInfiniteTransition(label = "bob")
    val dy by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    return dy.dp
}

@Composable
private fun ReviewTab(s: Stats, pad: PaddingValues, onReview: () -> Unit) {
    BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(104.dp)) {
                Box(Modifier.fillMaxSize().background(DuoBlueDark, CircleShape))
                Box(
                    Modifier.fillMaxSize().padding(bottom = 6.dp).background(DuoBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("🔁", fontSize = 42.sp) }
            }
            Text("今天待复习", modifier = Modifier.padding(top = 18.dp), fontWeight = FontWeight.ExtraBold, color = DuoWolf, fontSize = 16.sp)
            Text("${s.due}", fontWeight = FontWeight.Black, fontSize = 64.sp, color = DuoEel, lineHeight = 68.sp)
            Text(
                if (s.due > 0) "个单词" else "今天可以休息一下",
                fontWeight = FontWeight.Bold,
                color = DuoWolf,
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("已学", s.learned.size, DuoBlue, Modifier.weight(1f))
                StatTile("已掌握", s.mastered, DuoYellowDark, Modifier.weight(1f))
            }
            Text(
                "答对升一格，间隔 1→2→4→7→15 天；再答对就算掌握。答错回到第一格。",
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = DuoWolf,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            DuoButton(
                if (s.due > 0) "开始复习" else "今天没有要复习的词",
                onClick = onReview,
                enabled = s.due > 0,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.border2().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", fontWeight = FontWeight.Black, fontSize = 26.sp, color = color)
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = DuoWolf)
    }
}

@Composable
private fun SearchTab(db: WordDb, st: HomeState, pad: PaddingValues, onOpen: (String) -> Unit) {
    val results by produceState(emptyList<Word>(), st.query) { value = withContext(Dispatchers.IO) { db.search(st.query) } }
    Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {
        SearchField(st.query) { st.query = it }
        if (st.query.isNotBlank() && results.isEmpty()) {
            Text(
                "词库里没有以「${st.query.trim()}」开头的词",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                color = DuoWolf,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.word }) { w ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(w.word) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.word, fontWeight = FontWeight.Black, fontSize = 18.sp, color = DuoEel)
                            if (!w.meaning.isNullOrBlank()) {
                                Text(
                                    w.meaning,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = DuoWolf,
                                )
                            }
                        }
                        w.phonetic?.let {
                            Text(
                                "/$it/",
                                modifier = Modifier.padding(start = 12.dp),
                                color = DuoBlue,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    HorizontalDivider(thickness = 2.dp, color = DuoSwan, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DuoEel),
        cursorBrush = SolidColor(DuoBlue),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(16.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(2.dp, if (focused) DuoBlue else DuoSwan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text("输入英文单词，比如 march", color = DuoHare, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                inner()
            }
        },
    )
}
