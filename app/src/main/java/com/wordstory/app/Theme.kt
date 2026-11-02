package com.wordstory.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 多邻国的色板。Nunito 只有拉丁字母，中文缺字时回退系统字体（minSdk 26 支持）。
val DuoGreen = Color(0xFF58CC02)
val DuoGreenDark = Color(0xFF58A700)
val DuoBlue = Color(0xFF1CB0F6)
val DuoBlueDark = Color(0xFF1899D6)
val DuoRed = Color(0xFFFF4B4B)
val DuoRedDark = Color(0xFFEA2B2B)
val DuoYellow = Color(0xFFFFC800)
val DuoYellowDark = Color(0xFFD9A300)
val DuoYellowLip = Color(0xFFE6A800)
val DuoOrange = Color(0xFFFF9600)
val DuoOrangeDark = Color(0xFFE08600)
val DuoPurple = Color(0xFFCE82FF)
val DuoPurpleDark = Color(0xFFA560D6)
val DuoEel = Color(0xFF4B4B4B)
val DuoWolf = Color(0xFF777777)
val DuoHare = Color(0xFFAFAFAF)
val DuoSwan = Color(0xFFE5E5E5)
val DuoPolar = Color(0xFFF7F7F7)
val DuoWhite = Color(0xFFFFFFFF)
val DuoGreenSoft = Color(0xFFD7FFB8)
val DuoRedSoft = Color(0xFFFFDFE0)
val DuoLock = Color(0xFFE5E5E5)
val DuoLockLip = Color(0xFFCFCFCF)

data class LevelPalette(val face: Color, val lip: Color)

fun levelPalette(level: Int) = when (level) {
    1 -> LevelPalette(DuoGreen, DuoGreenDark)
    2 -> LevelPalette(DuoBlue, DuoBlueDark)
    3 -> LevelPalette(DuoPurple, DuoPurpleDark)
    4 -> LevelPalette(DuoRed, DuoRedDark)
    else -> LevelPalette(DuoOrange, DuoOrangeDark)
}

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Medium),
    Font(R.font.nunito_bold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private fun duo(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
)

val DuoTypography = Typography(
    displayLarge = duo(FontWeight.Black, 48, 52),
    displayMedium = duo(FontWeight.Black, 40, 44),
    displaySmall = duo(FontWeight.Black, 32, 36),
    headlineLarge = duo(FontWeight.Black, 32, 36),
    headlineMedium = duo(FontWeight.Black, 28, 32),
    headlineSmall = duo(FontWeight.ExtraBold, 24, 30),
    titleLarge = duo(FontWeight.ExtraBold, 22, 28),
    titleMedium = duo(FontWeight.ExtraBold, 18, 24),
    titleSmall = duo(FontWeight.ExtraBold, 16, 22),
    bodyLarge = duo(FontWeight.Bold, 16, 24),
    bodyMedium = duo(FontWeight.Bold, 14, 20),
    bodySmall = duo(FontWeight.Bold, 12, 16),
    labelLarge = duo(FontWeight.ExtraBold, 14, 18),
    labelMedium = duo(FontWeight.ExtraBold, 12, 16),
    labelSmall = duo(FontWeight.ExtraBold, 11, 14),
)

private val DuoScheme = lightColorScheme(
    primary = DuoGreen,
    onPrimary = Color.White,
    primaryContainer = DuoGreenSoft,
    onPrimaryContainer = DuoGreenDark,
    secondary = DuoBlue,
    onSecondary = Color.White,
    tertiary = DuoOrange,
    background = DuoWhite,
    onBackground = DuoEel,
    surface = DuoWhite,
    onSurface = DuoEel,
    surfaceVariant = DuoPolar,
    onSurfaceVariant = DuoWolf,
    error = DuoRed,
    onError = Color.White,
    errorContainer = DuoRedSoft,
    onErrorContainer = DuoRedDark,
    outline = DuoSwan,
)

@Composable
fun DuoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DuoScheme, typography = DuoTypography, content = content)
}

private val Chubby = RoundedCornerShape(16.dp)

/**
 * 多邻国那种厚底按钮：底下一截深色，按下时正面沉下去。
 * [outline] 给白底按钮加一圈边，否则和页面融在一起。
 */
@Composable
private fun LipBox(
    modifier: Modifier,
    face: Color,
    lip: Color,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    outline: Boolean,
    fixed: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val shift by animateDpAsState(
        if (pressed && enabled && onClick != null) depth else 0.dp, tween(70), label = "press",
    )
    val side = if (outline) 2.dp else 0.dp
    val top = if (outline) 2.dp else 0.dp
    val click = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick,
        )
    } else Modifier
    Box(modifier.clip(Chubby).then(click)) {
        Box(Modifier.matchParentSize().background(lip))
        val faceMod = if (fixed) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
        // 正面也要圆角，且比外层小一圈边框宽度：直角会盖住底层圆角，边框在四个角上就断了
        }.padding(start = side, end = side, top = top, bottom = depth).offset(y = shift)
            .background(face, RoundedCornerShape(16.dp - side))
        Box(faceMod, contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun DuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    face: Color = DuoGreen,
    lip: Color = DuoGreenDark,
    contentColor: Color = Color.White,
    outline: Boolean = false,
) {
    LipBox(
        modifier = modifier.fillMaxWidth().height(54.dp),
        face = if (enabled) face else DuoLock,
        lip = if (enabled) lip else DuoLockLip,
        enabled = enabled,
        onClick = onClick,
        outline = outline,
        fixed = true,
    ) {
        Text(
            text,
            color = if (enabled) contentColor else DuoHare,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class ChoiceTone { Idle, Correct, Wrong, Dim }

@Composable
fun DuoChoice(text: String, tone: ChoiceTone, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val face = when (tone) {
        ChoiceTone.Idle -> DuoWhite
        ChoiceTone.Correct -> DuoGreenSoft
        ChoiceTone.Wrong -> DuoRedSoft
        ChoiceTone.Dim -> DuoPolar
    }
    val lip = when (tone) {
        ChoiceTone.Correct -> DuoGreen
        ChoiceTone.Wrong -> DuoRed
        else -> DuoSwan
    }
    val fg = when (tone) {
        ChoiceTone.Idle -> DuoEel
        ChoiceTone.Correct -> DuoGreenDark
        ChoiceTone.Wrong -> DuoRedDark
        ChoiceTone.Dim -> DuoHare
    }
    LipBox(
        modifier = modifier.fillMaxWidth(),
        face = face,
        lip = lip,
        enabled = tone == ChoiceTone.Idle,
        onClick = onClick,
        outline = true,
        fixed = false,
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun DuoProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = DuoGreen,
    track: Color = DuoSwan,
    barHeight: Dp = 16.dp,
) {
    val shape = RoundedCornerShape(barHeight / 2)
    Box(modifier.height(barHeight).clip(shape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun DuoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Chubby)
            .background(DuoWhite)
            .border(2.dp, DuoSwan, Chubby)
            .padding(16.dp),
        content = content,
    )
}
