package com.forge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T

/** One stop on the tour: the tab it opens, what's there, and the one thing people tend to miss. */
data class GuideStep(val tab: String, val title: String, val points: List<String>, val tip: String)

object Guide {
    val steps = listOf(
        GuideStep(
            "today", "Your day on one screen.",
            listOf(
                "Today's workout, with a countdown to your deadline.",
                "Calories and protein left for today.",
                "Your morning weigh-in and habit check-in.",
            ),
            "Can't train? Tap \"I'm sick today\" under Start workout. No penalty, and your streak waits for you.",
        ),
        GuideStep(
            "train", "Your program.",
            listOf(
                "This week's training days and every past session.",
                "Each exercise has a how-to: setup, cues and common mistakes.",
                "Programs: pick one of 6 ready ones, edit it, or build your own.",
            ),
            "Got a program from a coach or a friend? Programs → Scan a program, and point the camera at their QR code.",
        ),
        GuideStep(
            "fuel", "Everything you eat.",
            listOf(
                "Add food from a basic list, 35 Syrian dishes, or online search.",
                "Recipes and full-day meal plans that hit your protein.",
                "A weekly chart of protein or calories.",
            ),
            "The scan button next to Recipes reads barcodes and nutrition labels, so you don't type anything.",
        ),
        GuideStep(
            "habits", "Build it. Or quit it.",
            listOf(
                "Add any habit you want to build, or one you want to quit.",
                "Tick it off each day; the grid shows your whole week.",
                "Streaks, best runs and a week score.",
            ),
            "Fighting an urge? Quit habits come with an urge timer to help you ride it out.",
        ),
        GuideStep(
            "progress", "Proof it's working.",
            listOf(
                "Your 7-day average weight, so daily swings don't fool you.",
                "Kilos per week against your target, and when you'll get there.",
                "Workouts, training volume, lifts and your level.",
            ),
            "Tap the share icon on a chart to turn it into a clean picture for Instagram or WhatsApp.",
        ),
        GuideStep(
            "today", "You're all set.",
            listOf(
                "Settings is the gear at the top of Today: your color, reminder times, targets and backups.",
                "Reminders do the pushing, so keep notifications on.",
            ),
            "You can replay this guide any time from Settings.",
        ),
    )
}

/**
 * A walk through every tab. It switches to each tab for real so people see their own screen,
 * dims it, rings the tab in the bottom bar and explains it in a card above.
 */
@Composable
fun GuideOverlay(nav: Nav, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val steps = Guide.steps
    val cur = steps[step]
    val last = step == steps.lastIndex
    LaunchedEffect(step) { nav.tab(cur.tab) }
    BackHandler { if (step > 0) step-- else onDone() }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pulse by rememberInfiniteTransition(label = "guide").animateFloat(
        1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )

    BoxWithConstraints(
        Modifier.fillMaxSize()
            // Swallow taps so the screen underneath can't be used mid-tour.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val tabIndex = TAB_ORDER.indexOf(cur.tab).takeIf { !last }
        // Dim the screen; on tab stops leave the bottom bar clear so the selected tab stands out.
        Box(
            Modifier.fillMaxSize()
                .padding(bottom = if (tabIndex != null) navBottom + 67.dp else 0.dp)
                .background(C.Bg.copy(alpha = 0.72f)),
        )
        // Ring around the tab being explained (bottom bar: 6 dp side padding, 5 equal slots, 66 dp tall).
        if (tabIndex != null) {
            val slot = (maxWidth - 12.dp) / TAB_ORDER.size
            val ring = 58.dp
            Box(
                Modifier
                    .offset(x = 6.dp + slot * tabIndex + (slot - ring) / 2, y = maxHeight - navBottom - 33.dp - ring / 2 + 1.dp)
                    .size(ring)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 1.6f - pulse }
                    .border(2.dp, C.Volt, CircleShape),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = navBottom + if (last) 24.dp else 84.dp),
        ) {
            Panel(accent = true, border = C.Volt.copy(alpha = 0.45f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith fadeOut(tween(140))
                    },
                    label = "guideStep",
                ) { i ->
                    val g = steps[i]
                    Column {
                        Eyebrow(
                            if (i == steps.lastIndex) "Guide" else "${i + 1} of ${steps.size - 1} · ${tabLabel(g.tab)}",
                            color = C.Volt,
                        )
                        Gap(6.dp)
                        Text(g.title.uppercase(), style = T.H1, color = C.Hi)
                        Gap(10.dp)
                        g.points.forEach { p ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(RoundedCornerShape(1.dp)).background(C.Volt))
                                HGap(10.dp)
                                Text(p, style = T.Body, color = C.Hi)
                            }
                        }
                        Gap(12.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.VoltSoft).padding(12.dp),
                        ) {
                            Icon(Icons.Rounded.Lightbulb, null, tint = C.Volt, modifier = Modifier.size(18.dp))
                            HGap(10.dp)
                            Text(g.tip, style = T.Small, color = C.Hi)
                        }
                    }
                }
                Gap(16.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!last) TextAction("Skip", onDone, color = C.Mid, style = T.Button)
                    else if (step > 0) TextAction("Back", { step-- }, color = C.Mid, style = T.Button)
                    HGap(12.dp)
                    Dots(step, steps.size, Modifier.weight(1f))
                    PrimaryButton(
                        if (last) "Let's go" else "Next",
                        { if (last) onDone() else step++ },
                        Modifier.width(132.dp),
                        height = 48.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            Box(
                Modifier.height(5.dp).width(if (i == current) 16.dp else 5.dp).clip(CircleShape)
                    .background(if (i == current) C.Volt else C.S3),
            )
        }
    }
}

private val TAB_ORDER = listOf("today", "train", "fuel", "habits", "progress")

private fun tabLabel(route: String) = route.replaceFirstChar { it.uppercase() }
