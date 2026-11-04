package com.wordstory.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 点到句子里的某个词时调用；App 顶层提供，弹出 WordSheet */
val LocalWordLookup = staticCompositionLocalOf<(String) -> Unit> { {} }

// 和 tools/build_db.py 的 TOKEN 一致，查询表就是按它切出来的词建的
private val WORD = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

private class Underline(val x1: Float, val x2: Float, val y: Float)

/** 英文单词底下画虚线，点一下查词。也处理 **加粗**。中文不受影响。 */
@Composable
fun TappableText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DuoEel,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 26.sp,
    fontWeight: FontWeight? = null,
) {
    val onWord = LocalWordLookup.current
    val annotated = remember(text, onWord) {
        val base = boldMarkdown(text)
        val pressed = TextLinkStyles(pressedStyle = SpanStyle(background = DuoBlue.copy(alpha = 0.2f)))
        buildAnnotatedString {
            append(base)
            WORD.findAll(base.text).forEach { m ->
                addLink(LinkAnnotation.Clickable(m.value, pressed) { onWord(m.value) }, m.range.first, m.range.last + 1)
            }
        }
    }
    val gap = with(LocalDensity.current) { 3.dp.toPx() }
    var lines by remember(annotated) { mutableStateOf(emptyList<Underline>()) }
    Text(
        annotated,
        modifier.drawBehind {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            lines.forEach { drawLine(DuoHare, Offset(it.x1, it.y), Offset(it.x2, it.y), 1.5.dp.toPx(), pathEffect = dash) }
        },
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        onTextLayout = { lines = underlines(it, annotated, gap) },
    )
}

/** 每个词在每一行上的虚线位置；排版时算一次，绘制时直接用 */
private fun underlines(layout: TextLayoutResult, text: AnnotatedString, gap: Float): List<Underline> =
    text.getLinkAnnotations(0, text.length).flatMap { r ->
        (layout.getLineForOffset(r.start)..layout.getLineForOffset(r.end - 1)).map { line ->
            val s = maxOf(r.start, layout.getLineStart(line))
            val e = minOf(r.end, layout.getLineEnd(line))
            Underline(layout.getBoundingBox(s).left, layout.getBoundingBox(e - 1).right, layout.getLineBaseline(line) + gap)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(db: WordDb, token: String, onDismiss: () -> Unit) {
    val data by produceState<Pair<Lookup?, Word?>?>(null, token) {
        value = withContext(Dispatchers.IO) { db.lookup(token).let { it to it?.story?.let(db::word) } }
    }
    var showStory by remember(token) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DuoWhite) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            val (hit, word) = data ?: return@Column
            if (hit == null) {
                PronounceableWord(token, fontSize = 32.sp, lineHeight = 36.sp)
                Text("词库里没有收录这个词", color = DuoWolf, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                return@Column
            }
            PronounceableWord(hit.head, fontSize = 32.sp, lineHeight = 36.sp)
            if (!hit.head.equals(token, ignoreCase = true)) {
                Text("$token → ${hit.head}", color = DuoWolf, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            hit.phonetic?.let {
                Text("/$it/", color = DuoBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
            }
            hit.meaning?.let {
                Text(it, color = DuoEel, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 8.dp))
            }
            word?.let { w ->
                DuoButton(
                    if (showStory) "收起故事" else "看看这个词背后的故事",
                    onClick = { showStory = !showStory },
                    modifier = Modifier.padding(top = 16.dp),
                    face = DuoWhite, lip = DuoSwan, contentColor = DuoBlue, outline = true,
                )
                if (showStory) {
                    Spacer(Modifier.height(12.dp))
                    WordSections(w)   // 故事里的词也能接着点，弹窗换成新词
                }
            }
        }
    }
}
