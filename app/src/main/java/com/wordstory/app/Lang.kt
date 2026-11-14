package com.wordstory.app

import android.content.SharedPreferences
import android.icu.text.Transliterator
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 简体 / 繁體。代码和词库里都是简体，显示时再转成繁体。
 * shortcut: 用系统 ICU 逐字转换，一简对多繁的字会错（头发→頭發、皇后→皇後），也不换台湾用词；
 * 要求更高时改成构建词库时用 OpenCC 预先转好。
 */
object Lang {
    var hant by mutableStateOf(false); private set

    // Transliterator 从 Android 10 才有，更老的系统只显示简体
    private val toHant by lazy {
        if (Build.VERSION.SDK_INT >= 29) runCatching { Transliterator.getInstance("Hans-Hant") }.getOrNull() else null
    }
    val supported get() = toHant != null
    private val cache by lazy { LruCache<String, String>(500) }   // lazy：单元测试里没有 Android 的类

    fun init(prefs: SharedPreferences) {
        val saved = prefs.getString("lang", null)
        val locale = Locale.getDefault()
        hant = supported && (saved?.equals("hant") ?: (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")))
    }

    fun toggle(prefs: SharedPreferences) {
        hant = !hant
        prefs.edit().putString("lang", if (hant) "hant" else "hans").apply()
    }

    fun zh(s: String): String {
        val t = toHant
        if (!hant || t == null || s.none { it in '一'..'鿿' }) return s
        return cache.get(s) ?: t.transliterate(s).also { cache.put(s, it) }
    }
}

/** 点一下在简体和繁體之间切换 */
@Composable
fun LangChip(prefs: SharedPreferences, modifier: Modifier = Modifier) {
    if (!Lang.supported) return
    val shape = RoundedCornerShape(12.dp)
    androidx.compose.material3.Text(
        if (Lang.hant) "🌐 繁體" else "🌐 简体",
        modifier = modifier.clip(shape).border(2.dp, DuoSwan, shape).clickable { Lang.toggle(prefs) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = DuoWolf, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
    )
}

// 包里所有的 Text 都走这两个函数（各文件不再 import material3 的 Text），所以界面文字和词库内容一起转
@Composable
fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null, textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = remember(text, Lang.hant) { Lang.zh(text) }, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
    lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    onTextLayout = onTextLayout, style = style,
)

@Composable
fun Text(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null, textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {}, style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = remember(text, Lang.hant) {
        val out = Lang.zh(text.text)
        // 样式和点词的位置按下标记，长度变了就不转
        if (out.length == text.length) AnnotatedString(out, text.spanStyles, text.paragraphStyles) else text
    },
    modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
    lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
)
