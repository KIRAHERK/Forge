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

val Changa = FontFamily(
    Font(R.font.changa_medium, FontWeight.Medium),
    Font(R.font.changa_semi_bold, FontWeight.SemiBold),
    Font(R.font.changa_bold, FontWeight.Bold),
)

val Cairo = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semi_bold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

/**
 * Type scale. English: Barlow Condensed titles, Barlow body. Arabic: Changa titles, Cairo body,
 * no letter spacing (it breaks joined letters) and taller lines (Arabic needs the room).
 */
object T {
    private const val TNUM = "tnum"
    private val ar get() = com.forge.app.i18n.Lang.arabic

    private fun title(weight: FontWeight, size: Float, line: Float, spacing: Float = 0f, tnum: Boolean = false) =
        if (ar) TextStyle(
            fontFamily = Changa, fontWeight = weight, fontSize = (size * 0.86f).sp, lineHeight = (size * 1.18f).sp, fontFeatureSettings = if (tnum) TNUM else null,
            // Changa sits low in a tall line box; trimming keeps titles close to the text above them.
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
        )
        else TextStyle(fontFamily = BarlowCondensed, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em, fontFeatureSettings = if (tnum) TNUM else null)

    private fun body(weight: FontWeight, size: Float, line: Float, spacing: Float = 0f, tnum: Boolean = false) =
        if (ar) TextStyle(
            fontFamily = Cairo, fontWeight = weight, fontSize = size.sp, lineHeight = (line * 1.12f).sp, fontFeatureSettings = if (tnum) TNUM else null,
            // Cairo's line box is tall; trimming the first and last line keeps labels from looking padded.
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
        )
        else TextStyle(fontFamily = Barlow, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em, fontFeatureSettings = if (tnum) TNUM else null)

    val Hero get() = title(FontWeight.Bold, 64f, 58f, -0.01f, tnum = true)
    val Display get() = title(FontWeight.Bold, 40f, 40f, 0f, tnum = true)
    val H1 get() = title(FontWeight.Bold, 30f, 32f, 0.01f)
    val H2 get() = title(FontWeight.SemiBold, 22f, 26f, 0.02f)
    val H3 get() = title(FontWeight.SemiBold, 18f, 22f, 0.03f)
    // Numbers keep Barlow Condensed in both languages: digits are Western either way.
    val Num get() = TextStyle(fontFamily = if (ar) Changa else BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = if (ar) 28.sp else 24.sp, fontFeatureSettings = TNUM)
    val NumL get() = TextStyle(fontFamily = if (ar) Changa else BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = if (ar) 36.sp else 30.sp, fontFeatureSettings = TNUM)
    val Eyebrow get() = body(FontWeight.SemiBold, 11f, 14f, 0.14f)
    val Body get() = body(FontWeight.Normal, 15f, 21f)
    val BodyM get() = body(FontWeight.Medium, 15f, 21f)
    val BodyS get() = body(FontWeight.SemiBold, 15f, 21f)
    val Small get() = body(FontWeight.Medium, 13f, 18f)
    val Tiny get() = body(FontWeight.Medium, 11f, 14f, tnum = true)
    val Button get() = title(FontWeight.Bold, 18f, 20f, 0.06f)

    /** A big title at [size] sp (e.g. onboarding). Keeps Arabic line height sane. */
    fun hero(size: Int, line: Int = size - 2): TextStyle =
        if (ar) title(FontWeight.Bold, size.toFloat(), line.toFloat()) else Hero.copy(fontSize = size.sp, lineHeight = line.sp)
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

private fun typography() = Typography(
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
    MaterialTheme(colorScheme = scheme(), typography = typography(), content = content)
}
