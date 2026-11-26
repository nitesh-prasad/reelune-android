package io.github.niteshprasad.reelune.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.niteshprasad.reelune.R

// Moonlit projection room: the film's own backdrop glows through frosted glass panels.
object Reel {
    val Night = Color(0xFF0D0B1E)
    val Twilight = Color(0xFF251B47)
    val Lunar = Color(0xFFDCE3F2)
    val Moonglow = Color(0xFF8FE3FF)
    val Halo = Color(0xFFB79CFF)

    val Text = Color(0xFFE8ECF7)
    val TextSoft = Color(0xB8DCE3F2)
    val TextFaint = Color(0x80DCE3F2)
    val GlassLine = Color(0x21DCE3F2)
    val GlassFill = Color(0x61251B47)
    val GlassStrong = Color(0xD9181234)

    val Imdb = Color(0xFFF5C518)
    val Tomato = Color(0xFFFA320A)

    val ToneAll = Color(0xFF7BD88F)
    val ToneGuided = Color(0xFF8FE3FF)
    val ToneTeen = Color(0xFFFFC46B)
    val ToneAdult = Color(0xFFFF7A8A)
    val ToneUnrated = Color(0xFFAAB3CC)

    val PrimaryButton = Brush.linearGradient(listOf(Color(0xFFF3F6FF), Color(0xFFC9D4F3)))
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Display = FontFamily(
    variable(R.font.unbounded, 400),
    variable(R.font.unbounded, 500),
    variable(R.font.unbounded, 600),
    variable(R.font.unbounded, 700),
)

val Body = FontFamily(
    variable(R.font.figtree, 400),
    variable(R.font.figtree, 500),
    variable(R.font.figtree, 600),
    variable(R.font.figtree, 700),
)

private val ReelTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 0.2.sp),
)

private val ReelColors = darkColorScheme(
    primary = Reel.Lunar,
    onPrimary = Color(0xFF15112E),
    secondary = Reel.Moonglow,
    tertiary = Reel.Halo,
    background = Reel.Night,
    onBackground = Reel.Text,
    surface = Reel.Night,
    onSurface = Reel.Text,
    surfaceVariant = Reel.Twilight,
    onSurfaceVariant = Reel.TextSoft,
    surfaceContainer = Color(0xFF17122F),
    surfaceContainerHigh = Color(0xFF1E1840),
    outline = Reel.GlassLine,
    error = Reel.ToneAdult,
)

/** The haze source (backdrop) that glass panels blur. Null where no backdrop exists. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun ReeluneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReelColors, typography = ReelTypography) {
        CompositionLocalProvider(content = content)
    }
}

/**
 * Frosted glass: blurs whatever is behind it (on Android 12+), tinted with twilight, with a fine
 * light edge. Falls back to a solid translucent fill on older phones.
 */
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier = composed {
    val haze = LocalHaze.current
    val style = remember(strong) {
        HazeBlurStyle {
            blurRadius(if (strong) 28.dp else 22.dp)
            noiseFactor(0.06f)
            colorEffects(listOf(HazeColorEffect.tint(if (strong) Reel.GlassStrong else Reel.GlassFill)))
            fallbackColorEffect(HazeColorEffect.tint(Reel.GlassStrong))
        }
    }
    val base = this.clip(shape)
    val blurred = if (haze != null) base.hazeBlur(HazeInput.Sources(haze), style) else base.background(Reel.GlassStrong)
    blurred.border(1.dp, Reel.GlassLine, shape)
}

/** Fades this element to transparent towards the bottom, so images melt into the sky behind them. */
fun Modifier.fadeToTransparent(start: Float = 0.5f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Black, start to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
