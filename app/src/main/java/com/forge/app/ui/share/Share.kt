package com.forge.app.ui.share

import com.forge.app.i18n.t

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.forge.app.Credits
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.Stats
import com.forge.app.domain.Lines
import com.forge.app.domain.Program
import com.forge.app.domain.Progression
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.components.BarChart
import com.forge.app.ui.components.Bar
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.LineChart
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.Ring
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.Tag
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// The shared image is always 1440 × 1800 (4:5), whatever the phone: the largest size
// Instagram keeps, so it stays sharp after apps recompress it.
private const val PX_W = 1440
private const val PX_H = 1800
const val CARD_DENSITY = 4f // the card is laid out at 360 × 450 dp

/** Small share icon for the corner of a panel. */
@Composable
fun ShareIcon(onClick: () -> Unit) {
    // Accent-tinted and outlined so it reads as a button, not decoration.
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(C.VoltSoft).border(1.dp, C.Volt.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Share, t("Share"), tint = C.Volt, modifier = Modifier.size(18.dp))
    }
}

/** A number and a caption centred inside a ring; the number shrinks rather than spill over the ring. */
@Composable
private fun RingLabel(value: String, caption: String, color: Color, ring: androidx.compose.ui.unit.Dp = 80.dp) {
    val ar = com.forge.app.i18n.Lang.arabic
    // Fits inside the ring's hole: the ring's width less both strokes and a little air.
    Column(Modifier.width(ring * 0.72f), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.text.BasicText(
            value, maxLines = 1,
            style = T.Num.copy(color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = if (ar) 20.sp else 22.sp),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = if (ar) 18.sp else 22.sp, stepSize = 1.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.text.BasicText(
            caption, maxLines = 1,
            style = T.Tiny.copy(color = C.Mid, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = if (ar) 12.sp else 14.sp),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = if (ar) 9.sp else 11.sp, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------------------ dialog

@Composable
internal fun ShareDialog(name: String, onDismiss: () -> Unit, card: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.96f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.clip(RoundedCornerShape(18.dp)).clickable(remember { MutableInteractionSource() }, null) {}) { CardCanvas(layer, card) }
            Gap(18.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(t("Cancel"), onDismiss, Modifier.weight(1f), height = 50.dp)
                PrimaryButton(if (busy) t("Preparing") else t("Share"), {
                    if (busy) return@PrimaryButton
                    busy = true
                    scope.launch {
                        runCatching { shareBitmap(ctx, layer.toImageBitmap().asAndroidBitmap(), name) }.onFailure { android.util.Log.e("Forge", "Share failed", it) }
                        busy = false
                        onDismiss()
                    }
                }, Modifier.weight(1f), height = 50.dp, enabled = !busy)
            }
        }
    }
}

/** Lays the card out at a fixed 1080 × 1350 px, records it, and shows it scaled to fit. */
@Composable
private fun CardCanvas(layer: GraphicsLayer, card: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = constraints.maxWidth.toFloat() / PX_W
        val d = LocalDensity.current
        Box(Modifier.width(with(d) { (PX_W * scale).toDp() }).height(with(d) { (PX_H * scale).toDp() })) {
            Box(
                Modifier
                    .layout { m, _ ->
                        val p = m.measure(Constraints.fixed(PX_W, PX_H))
                        layout((PX_W * scale).roundToInt(), (PX_H * scale).roundToInt()) {
                            p.placeWithLayer(0, 0) {
                                scaleX = scale; scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    }
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                CompositionLocalProvider(LocalDensity provides Density(CARD_DENSITY, 1f)) { card() }
            }
        }
    }
}

private suspend fun shareBitmap(ctx: Context, image: Bitmap, name: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val bmp = if (image.config == Bitmap.Config.HARDWARE) image.copy(Bitmap.Config.ARGB_8888, false) else image
        File(dir, "$name.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, t("Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ------------------------------------------------------------------ frame

private val cardDate get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", com.forge.app.i18n.Lang.locale)

/** Shared layout of every card: brand, title, optional badge, content, a line, the credit. */
@Composable
internal fun CardFrame(
    date: LocalDate,
    eyebrow: String,
    title: String,
    badge: String?,
    line: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.size(360.dp, 450.dp).background(C.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(C.BgTop, C.Bg), endY = size.height * 0.6f))
            drawCircle(
                Brush.radialGradient(
                    listOf(C.Volt.copy(alpha = if (badge != null) 0.22f else 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.02f), radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f, center = Offset(size.width * 0.95f, size.height * 0.02f),
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("FORGE"), style = T.H3.copy(letterSpacing = 3.sp), color = C.Volt, modifier = Modifier.weight(1f))
                Text(date.format(cardDate), style = T.Tiny, color = C.Mid)
            }
            Gap(12.dp)
            Eyebrow(eyebrow)
            Gap(2.dp)
            Text(title.uppercase(), style = if (com.forge.app.i18n.Lang.arabic) T.hero(28, 30) else T.hero(34, 36), color = C.Hi, maxLines = 2)
            if (badge != null) {
                Gap(8.dp)
                Tag(badge, color = C.OnVolt, bg = C.Volt)
            }
            Gap(14.dp)
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp)), content = content)
            if (line != null) {
                Text(line, style = T.H3, color = C.Hi, maxLines = 2)
                Gap(12.dp)
            }
            Hairline()
            Gap(8.dp)
            Row {
                Text(t("Tracked with Forge"), style = T.Tiny, color = C.Mid, modifier = Modifier.weight(1f))
                Text("by ${Credits.AUTHOR}", style = T.Tiny, color = C.Lo)
            }
        }
    }
}

// ------------------------------------------------------------------ workout

@Composable
fun ShareWorkoutDialog(sessionId: Long, onDismiss: () -> Unit) {
    ShareDialog("forge-workout", onDismiss) {
        WorkoutCard(sessionId)
    }
}

/** The workout card itself, laid out at 360 × 450 dp. */
@Composable
fun WorkoutCard(sessionId: Long) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val ses by repo.session(sessionId).collectAsState(null)
    val sets by repo.setsFor(sessionId).collectAsState(emptyList())
    val sessions by repo.sessions.collectAsState(emptyList())
    val session = ses ?: return
    val day = Program.day(session.dayId)
    val prs = session.prNames.split("|").filter { it.isNotBlank() }
    val streak = Stats.workoutStreak(s, sessions, java.time.LocalDateTime.now())
    val minutes = ((session.finishedAt ?: session.startedAt) - session.startedAt) / 60000
    val done = sets.filter { it.done }
    // Best set per exercise, in program order.
    val tops = done.groupBy { it.exerciseIndex }.toSortedMap().values.map { list ->
        list.maxBy { Progression.e1rm(it.reps, it.weightKg) }
    }
    CardFrame(
        date = LocalDate.ofEpochDay(session.date),
        eyebrow = day?.let { t("Day %s · workout", it.number) } ?: t("Workout"),
        title = day?.name ?: t("Session"),
        badge = when {
            prs.isNotEmpty() -> if (prs.size == 1) t("New personal record") else t("%s new personal records", prs.size)
            session.complete -> t("Session complete")
            else -> null
        },
        line = when {
            prs.isNotEmpty() -> t("Stronger than last time.")
            streak >= 3 -> t("%s sessions in a row.", streak)
            session.complete -> Lines.done(session.id)
            else -> t("Logged. The next one counts too.")
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Stat(t("Time"), "$minutes", Modifier.weight(1f), unit = t("min"))
            Stat(t("Volume"), Fmt.num(session.volumeKg.roundToInt()), Modifier.weight(1.2f), unit = t("kg"))
            Stat(t("Sets"), "${done.size}", Modifier.weight(0.8f))
            Stat(t("Streak"), "$streak", Modifier.weight(0.9f), color = if (streak > 0) C.Volt else C.Hi)
        }
        Gap(12.dp)
        Eyebrow(t("Top sets"))
        Gap(6.dp)
        tops.take(5).forEach { st ->
            val ex = runCatching { com.forge.app.domain.Exercises.byId(st.exerciseId) }.getOrNull()
            val isPr = ex != null && prs.any { it.startsWith(ex.name) || it.startsWith(ex.en) }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ex?.name ?: st.exerciseId, style = T.Small, color = C.Hi, modifier = Modifier.weight(1f), maxLines = 1)
                if (isPr) { Tag(t("PR"), color = C.OnVolt, bg = C.Volt); HGap(6.dp) }
                Text(
                    if (st.weightKg > 0) t("%1${'$'}s × %2${'$'}s kg", st.reps, Progression.fmtKg(st.weightKg)) else t("%s reps", st.reps),
                    style = T.Small, color = if (isPr) C.Volt else C.Mid,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ fuel

@Composable
fun ShareFuelDialog(date: LocalDate, onDismiss: () -> Unit) {
    ShareDialog("forge-fuel", onDismiss) {
        FuelCard(date)
    }
}

/** The fuel card itself, laid out at 360 × 450 dp. */
@Composable
fun FuelCard(date: LocalDate) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val foods by remember(date) { repo.foodForDay(date) }.collectAsState(emptyList())
    val weekStart = s.weekStartOf(date)
    val week by remember(weekStart) { repo.foodBetween(weekStart, weekStart.plusDays(6)) }.collectAsState(emptyList())
    val m = Stats.sum(foods)
    // Same rules as the Habits grid and XP, so the card never says "hit" for a day the grid marks missed.
    val proteinHit = Stats.proteinHit(m.protein, s.protein)
    val kcalHit = Stats.kcalHit(m.kcal, s.kcal)
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekProtein = days.map { d -> week.filter { it.date == d.toEpochDay() }.sumOf { it.protein }.toFloat() }
    val weekHits = weekProtein.count { it >= s.protein * 0.95f }

    CardFrame(
        date = date,
        eyebrow = t("Nutrition"),
        title = if (date == LocalDate.now()) t("Today's fuel") else t("Fuel"),
        badge = when {
            proteinHit && kcalHit -> t("Targets hit")
            proteinHit -> t("Protein target hit")
            kcalHit -> t("Calorie target hit")
            else -> null
        },
        line = when {
            proteinHit && kcalHit -> t("Every gram accounted for.")
            weekHits >= 4 -> t("Protein target hit %s days this week.", weekHits)
            proteinHit -> t("Protein done. Muscle gets built on days like this.")
            else -> t("%s g of protein to go today.", (s.protein - m.protein).roundToInt().coerceAtLeast(0))
        },
    ) {
        // Arabic runs taller, so the rings shrink a little to leave the weekly chart its room.
        val ar = com.forge.app.i18n.Lang.arabic
        val ringSize = if (ar) 64.dp else 80.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(m.kcal.toFloat() / s.kcal, size = ringSize, stroke = 7.dp, color = C.Hi) {
                    RingLabel(Fmt.num(m.kcal), t("of %s", Fmt.num(s.kcal)), C.Hi, ringSize)
                }
                Gap(if (ar) 2.dp else 4.dp)
                Eyebrow(t("Calories"))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ring((m.protein / s.protein).toFloat(), size = ringSize, stroke = 7.dp, color = C.Volt) {
                    RingLabel(com.forge.app.i18n.t("%s g", Fmt.g(m.protein)), t("of %s g", s.protein), C.Volt, ringSize)
                }
                Gap(if (ar) 2.dp else 4.dp)
                Eyebrow(t("Protein"))
            }
        }
        Gap(if (ar) 4.dp else 8.dp)
        Text(
            t("Fat %1${'$'}s / %2${'$'}s g  ·  Carbs %3${'$'}s / %4${'$'}s g", Fmt.g(m.fat), s.fat, Fmt.g(m.carbs), s.carbs),
            style = if (ar) T.Tiny else T.Small, color = C.Mid, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Gap(if (ar) 4.dp else 8.dp)
        Eyebrow(t("Protein this week"))
        Gap(if (ar) 0.dp else 4.dp)
        // The chart takes the rest of the card, so there's never a blank band above the credit.
        BarChart(
            weekProtein, days.map { Fmt.dowLetter(it) }, Modifier.weight(1f).heightIn(min = 44.dp),
            target = s.protein.toFloat(), highlight = days.indexOf(date), height = Dp.Unspecified,
        )
    }
}

// ------------------------------------------------------------------ progress

@Composable
fun ShareProgressDialog(onDismiss: () -> Unit) {
    ShareDialog("forge-progress", onDismiss) {
        ProgressCard()
    }
}

/** The progress card itself, laid out at 360 × 450 dp. */
@Composable
fun ProgressCard() {
    val repo = Graph.repo
    val s = LocalSettings.current
    val weights by repo.weights.collectAsState(emptyList())
    val today = LocalDate.now()
    val avg = Stats.movingAverage(weights)
    val cur = avg.lastOrNull()?.second ?: weights.lastOrNull()?.kg
    val rate = Stats.weeklyRate(weights, today)
    val gaining = s.goalWeight >= s.startWeight
    val sinceStart = cur?.let { it - s.startWeight }
    val monthAgo = today.minusDays(30).toEpochDay()
    val base = weights.filter { it.date <= monthAgo }.maxByOrNull { it.date } ?: weights.firstOrNull()
    val month = if (cur != null && base != null) cur - base.kg else null
    // "Toward the goal" means the right sign for this goal, gaining or losing.
    fun toward(d: Double?) = d != null && abs(d) >= 0.05 && (d > 0) == gaining
    val goalReached = cur != null && if (gaining) cur >= s.goalWeight else cur <= s.goalWeight
    val progress = if (cur == null || s.goalWeight == s.startWeight) 0f else ((cur - s.startWeight) / (s.goalWeight - s.startWeight)).toFloat()
    val from = today.minusDays(90).toEpochDay()

    CardFrame(
        date = today,
        eyebrow = t("Body weight · 7-day average"),
        title = cur?.let { t("%s kg", Fmt.kg(it)) } ?: t("Progress"),
        badge = when {
            goalReached -> t("Goal reached")
            toward(rate) -> t("On track")
            else -> null
        },
        line = when {
            goalReached -> t("%s kg. Done. Next target.", Fmt.kg(s.goalWeight))
            toward(month) -> t("%s kg in 30 days. Moving the right way.", signed(month!!))
            toward(sinceStart) -> t("%s kg since day one.", signed(sinceStart!!))
            else -> t("Weighed every morning. The trend is what counts.")
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Stat(t("Since start"), sinceStart?.let { signed(it) } ?: "—", Modifier.weight(1f), unit = t("kg"), color = if (toward(sinceStart)) C.Volt else C.Hi)
            Stat(t("30 days"), month?.let { signed(it) } ?: "—", Modifier.weight(1f), unit = t("kg"), color = if (toward(month)) C.Volt else C.Hi)
            Stat(t("Per week"), rate?.let { String.format(Locale.US, "%+.2f", it) } ?: "—", Modifier.weight(1f), unit = t("kg"))
        }
        Gap(14.dp)
        LineChart(
            weights.filter { it.date >= from }.map { it.date to it.kg },
            avg.filter { it.first >= from },
            goal = s.goalWeight, height = 84.dp,
            empty = t("Log a few mornings to see the trend."),
        )
        Gap(10.dp)
        Row {
            Text(t("Start %s kg", Fmt.kg(s.startWeight)), style = T.Tiny, color = C.Mid, modifier = Modifier.weight(1f))
            Text(t("Goal %s kg", Fmt.kg(s.goalWeight)), style = T.Tiny, color = C.Volt)
        }
        Gap(5.dp)
        Bar(progress, height = 7.dp)
    }
}

private fun signed(v: Double) = String.format(Locale.US, "%+.1f", v)
