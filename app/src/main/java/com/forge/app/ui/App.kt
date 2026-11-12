package com.forge.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forge.app.Graph
import com.forge.app.data.Settings
import com.forge.app.data.Stats
import com.forge.app.domain.Xp
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.screens.ExerciseScreen
import com.forge.app.ui.screens.FuelScreen
import com.forge.app.ui.screens.HabitsScreen
import com.forge.app.ui.screens.HistoryScreen
import com.forge.app.ui.screens.OnboardingScreen
import com.forge.app.ui.screens.PlanScreen
import com.forge.app.ui.screens.ProgressScreen
import com.forge.app.ui.screens.RecipeScreen
import com.forge.app.ui.screens.RecipesScreen
import com.forge.app.ui.screens.SettingsScreen
import com.forge.app.ui.screens.SummaryScreen
import com.forge.app.ui.screens.TodayScreen
import com.forge.app.ui.screens.TrainScreen
import com.forge.app.ui.screens.UrgeScreen
import com.forge.app.ui.screens.WorkoutScreen
import com.forge.app.ui.screens.WeightScreen
import com.forge.app.ui.screens.UpdateScreen
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One-shot UI requests coming from notifications or other screens. */
object UiEvents {
    val weighRequest = mutableStateOf(false)
    val addFoodRequest = mutableStateOf(false)
    /** A full-screen moment is showing; hold other reveals until it's gone. */
    val overlayOpen = mutableStateOf(false)
}

val LocalSettings = compositionLocalOf { Settings() }

class Nav(val controller: NavHostController) {
    fun tab(route: String) {
        // Today is the root: just pop back to it. Navigating to it with restoreState would
        // bring back whatever screen was saved on top of it.
        if (route == "today") {
            if (!controller.popBackStack("today", inclusive = false)) controller.navigate("today")
            return
        }
        controller.navigate(route) {
            popUpTo("today") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun go(route: String) = controller.navigate(route) { launchSingleTop = true }
    fun back() { if (!controller.popBackStack()) tab("today") }

    fun workout(id: Long) = go("workout/$id")
    fun exercise(id: String) = go("exercise/$id")
    fun recipe(id: String) = go("recipe/$id")
    fun summary(id: Long) = controller.navigate("summary/$id") {
        popUpTo("today")
        launchSingleTop = true
    }
}

private val tabs = listOf(
    Triple("today", "Today", Icons.Rounded.WbSunny),
    Triple("train", "Train", Icons.Rounded.FitnessCenter),
    Triple("fuel", "Fuel", Icons.Rounded.Restaurant),
    Triple("habits", "Habits", Icons.Rounded.TaskAlt),
    Triple("progress", "Progress", Icons.Rounded.Insights),
)

@Composable
fun AppRoot(routeRequest: MutableState<String?>) {
    val repo = Graph.repo
    val settings by repo.settingsFlow.collectAsState(initial = null)
    val s = settings

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        com.forge.app.ui.components.Glow(Modifier.fillMaxSize())
        when {
            s == null -> {}
            !s.onboarded -> OnboardingScreen()
            else -> MainShell(s, routeRequest)
        }
    }
}

@Composable
private fun MainShell(s: Settings, routeRequest: MutableState<String?>) {
    val repo = Graph.repo
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val scope = rememberCoroutineScope()
    val entry by controller.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val xpN by repo.xpTotal.collectAsState(initial = null)
    val xp = xpN ?: 0
    val level = Xp.level(xp)

    // Quiet update check once per app start.
    LaunchedEffect(Unit) {
        if (s.autoUpdateCheck && com.forge.app.update.Updater.state.value == com.forge.app.update.Updater.State.Idle) {
            com.forge.app.update.Updater.check()
        }
    }

    // Requests from notifications and the widget.
    val req = routeRequest.value
    LaunchedEffect(req) {
        if (req == null) return@LaunchedEffect
        routeRequest.value = null
        when (req) {
            "start", "workout" -> {
                val active = repo.db.sessions().activeNow()
                if (active != null) nav.workout(active.id)
                else {
                    val today = LocalDate.now()
                    val day = s.dayFor(today)
                    val done = Stats.doneDates(repo.db.sessions().allNow()).contains(today.toEpochDay())
                    if (req == "start" && day != null && !done) nav.workout(repo.startSession(day.id, today, false))
                    else nav.tab("today")
                }
            }
            "weigh" -> { nav.tab("today"); nav.go("weight"); UiEvents.weighRequest.value = true }
            "update" -> { nav.tab("today"); nav.go("update") }
            "today", "train", "fuel", "habits", "progress" -> nav.tab(req)
            else -> nav.tab("today")
        }
    }

    CompositionLocalProvider(LocalSettings provides s) {
    Box(Modifier.fillMaxSize()) {
        NavHost(controller, startDestination = "today", modifier = Modifier.fillMaxSize()) {
            composable("today") { TodayScreen(nav) }
            composable("train") { TrainScreen(nav) }
            composable("fuel") { FuelScreen(nav) }
            composable("habits") { HabitsScreen(nav) }
            composable("progress") { ProgressScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("urge") { UrgeScreen(nav) }
            composable("recipes") { RecipesScreen(nav) }
            composable("history") { HistoryScreen(nav) }
            composable("weight") { WeightScreen(nav) }
            composable("update") { UpdateScreen(nav) }
            composable("plan/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                PlanScreen(nav, it.arguments?.getString("id") ?: "")
            }
            composable("recipe/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                RecipeScreen(nav, it.arguments?.getString("id") ?: "")
            }
            composable("exercise/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                ExerciseScreen(nav, it.arguments?.getString("id") ?: "")
            }
            composable("workout/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("summary/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                SummaryScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
        }

        // Solid strip behind the status bar so scrolled content never runs under the clock.
        // It repeats the top of the glow so the strip doesn't show as a band.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(C.Bg).clipToBounds()) {
            com.forge.app.ui.components.Glow(
                Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp),
            )
        }

        if (current in tabs.map { it.first }) {
            BottomBar(current ?: "today", Modifier.align(Alignment.BottomCenter)) { nav.tab(it) }
        }

        // Level-up moment
        val showLevel = xpN != null && level.level > s.lastSeenLevel && xp > 0
        UiEvents.overlayOpen.value = showLevel
        AnimatedVisibility(showLevel, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
            LevelUp(level.level, level.title) {
                scope.launch { Graph.settings.update { it.copy(lastSeenLevel = level.level) } }
            }
        }
        // If XP dropped below a level (penalties), quietly sync down so the next level-up still triggers.
        LaunchedEffect(level.level, xpN != null) {
            if (xpN != null && level.level < s.lastSeenLevel) Graph.settings.update { it.copy(lastSeenLevel = level.level) }
        }
    }
    }
}

@Composable
private fun BottomBar(current: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Column(modifier.fillMaxWidth().background(C.Bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (route, label, icon) ->
                TabItem(label, icon, route == current, Modifier.weight(1f)) { onSelect(route) }
            }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ind by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "tab")
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
        ).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(20.dp).height(3.dp).graphicsLayer { scaleX = ind; alpha = ind }
                .clip(RoundedCornerShape(2.dp)).background(C.Volt),
        )
        Gap(6.dp)
        Icon(icon, label, tint = if (selected) C.Hi else C.Lo, modifier = Modifier.size(22.dp))
        Gap(3.dp)
        Text(label, style = T.Tiny, color = if (selected) C.Hi else C.Lo)
    }
}

@Composable
private fun LevelUp(level: Int, title: String, onDismiss: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(if (started) 1f else 0.6f, spring(dampingRatio = 0.45f, stiffness = 220f), label = "lvl")
    Box(
        Modifier.fillMaxSize().background(C.Bg).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Eyebrow("Level up", color = C.Volt)
            Gap(8.dp)
            Text(
                "$level", style = T.Hero.copy(fontSize = 160.sp, lineHeight = 150.sp), color = C.Hi,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            )
            Text(title.uppercase(), style = T.H1, color = C.Volt)
            Gap(16.dp)
            Text(
                "Earned, not given. Next level needs ${Xp.stepFor(level)} XP.",
                style = T.Body, color = C.Mid, textAlign = TextAlign.Center,
            )
            Gap(32.dp)
            PrimaryButton("Keep going", onDismiss, Modifier.width(220.dp))
        }
    }
}
