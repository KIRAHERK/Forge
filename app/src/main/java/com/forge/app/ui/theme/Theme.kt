package com.forge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.forge.app.R
import com.forge.app.domain.Accent

// ---------- Palette ----------
// One accent (volt) carries progress and primary actions. Red is reserved for
// misses, slips and penalties. Everything else is neutral.
object C {
    /** The chosen main color. Reading it from a composable recomposes when it changes. */
    var accent: Accent by mutableStateOf(Accent.GREEN)

    val Bg = Color(0xFF0B0B0D)
    /** Top of the background wash and the share cards: the background tinted with the accent. */
    val BgTop: Color get() = if (accent == Accent.GREEN) Color(0xFF15170E) else lerp(Bg, Volt, 0.055f)
    /** Top corner of accent panels. */
    val AccentTop: Color get() = if (accent == Accent.GREEN) Color(0xFF20251A) else lerp(S1, Volt, 0.09f)
    val S1 = Color(0xFF141417)
    val S2 = Color(0xFF1B1B1F)
    val S3 = Color(0xFF26262B)
    val Line = Color(0xFF232328)
    val LineStrong = Color(0xFF34343B)

    val Hi = Color(0xFFF2F2F3)
    val Mid = Color(0xFF9B9BA4)
    val Lo = Color(0xFF63636C)

    /** The accent. Named after the original lime ("volt"); follows the user's choice. */
    val Volt: Color get() = Color(accent.argb)
    val VoltSoft: Color get() = Volt.copy(alpha = 0.14f)
    val OnVolt = Color(0xFF0B0B0D)

    /** Misses, slips and penalties. Never the same as the accent. */
    val Red: Color get() = Color(accent.miss)
    val RedSoft: Color get() = Red.copy(alpha = 0.14f)
    val Amber = Color(0xFFFFB547)
    val AmberSoft = Color(0x24FFB547)
}

// ---------- Type ----------
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_bold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semi_bold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

object T {
    private const val TNUM = "tnum"

    val Hero = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 58.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM)
    val Display = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = 0.em, fontFeatureSettings = TNUM)
    val H1 = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 0.01.em)
    val H2 = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.02.em)
    val H3 = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.03.em)
    val Num = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM)
    val NumL = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 30.sp, fontFeatureSettings = TNUM)
    val Eyebrow = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em)
    val Body = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val BodyM = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp)
    val BodyS = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
    val Small = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Tiny = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = TNUM)
    val Button = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp, letterSpacing = 0.06.em)
}

private fun scheme() = darkColorScheme(
    primary = C.Volt,
    onPrimary = C.OnVolt,
    secondary = C.Hi,
    onSecondary = C.Bg,
    background = C.Bg,
    onBackground = C.Hi,
    surface = C.S1,
    onSurface = C.Hi,
    surfaceVariant = C.S2,
    onSurfaceVariant = C.Mid,
    surfaceContainer = C.S1,
    surfaceContainerHigh = C.S2,
    surfaceContainerHighest = C.S3,
    surfaceContainerLow = C.S1,
    surfaceContainerLowest = C.Bg,
    outline = C.LineStrong,
    outlineVariant = C.Line,
    error = C.Red,
    onError = C.Hi,
)

private val typography = Typography(
    bodyLarge = T.Body,
    bodyMedium = T.Body,
    bodySmall = T.Small,
    labelLarge = T.BodyS,
    labelMedium = T.Small,
    labelSmall = T.Tiny,
    titleLarge = T.H2,
    titleMedium = T.H3,
    titleSmall = T.BodyS,
    headlineLarge = T.H1,
    headlineMedium = T.H1,
    headlineSmall = T.H2,
    displayLarge = T.Hero,
    displayMedium = T.Display,
    displaySmall = T.H1,
)

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = typography, content = content)
}
