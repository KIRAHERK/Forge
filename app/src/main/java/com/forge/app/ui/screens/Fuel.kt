package com.forge.app.ui.screens

import com.forge.app.i18n.t

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.Dishes
import com.forge.app.data.FoodEntity
import com.forge.app.data.FoodPick
import com.forge.app.data.OffProduct
import com.forge.app.data.OpenFoodFacts
import com.forge.app.data.TextMatch
import com.forge.app.data.per
import com.forge.app.data.Settings
import com.forge.app.data.Stats
import com.forge.app.domain.DayPlans
import com.forge.app.domain.Food
import com.forge.app.domain.FoodGroup
import com.forge.app.domain.Foods
import com.forge.app.domain.Macros
import com.forge.app.domain.MealType
import com.forge.app.domain.Plan
import com.forge.app.domain.Recipe
import com.forge.app.domain.Recipes
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.UiEvents
import com.forge.app.ui.components.Bar
import com.forge.app.ui.components.BarChart
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.MacroLine
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.ScreenHeader
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.SectionTitle
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.Stepper
import com.forge.app.ui.components.Tag
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.components.WeekBars
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt


@Composable
fun FuelScreen(nav: Nav) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val foods by remember(date) { repo.foodForDay(date) }.collectAsState(emptyList())
    val weekStart = s.weekStartOf(date)
    val weekFoods by remember(weekStart) { repo.foodBetween(weekStart, weekStart.plusDays(6)) }.collectAsState(emptyList())
    val m = Stats.sum(foods)
    var sheet by remember { mutableStateOf(false) }
    var sheetScan by remember { mutableStateOf(false) }
    var planOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(UiEvents.addFoodRequest.value) {
        if (UiEvents.addFoodRequest.value) { sheetScan = false; sheet = true; UiEvents.addFoodRequest.value = false }
    }
    LaunchedEffect(UiEvents.scanFoodRequest.value) {
        if (UiEvents.scanFoodRequest.value) { sheetScan = true; sheet = true; UiEvents.scanFoodRequest.value = false }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
        item { ScreenHeader(t("Plan · %1${'$'}s → %2${'$'}s kg", s.startWeight.roundToInt(), s.goalWeight.roundToInt()), t("Fuel")) }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, t("Previous day"), { date = date.minusDays(1) }, tint = C.Mid)
                Text(
                    if (date == today) t("TODAY") else if (date == today.minusDays(1)) t("YESTERDAY") else Fmt.day(date).uppercase(),
                    style = T.H3, color = C.Hi, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconBtn(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, t("Next day"),
                    { if (date.isBefore(today)) date = date.plusDays(1) },
                    tint = if (date.isBefore(today)) C.Mid else C.S3,
                )
            }
            Gap(10.dp)
        }

        item {
            Panel(accent = true, onShare = { sharing = true }) {
                Row(Modifier.padding(end = 40.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow(t("Calories"))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.num(m.kcal), style = T.Display, color = C.Hi)
                            Text(" / ${Fmt.num(s.kcal)}", style = T.H3, color = C.Mid, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (Stats.kcalOver(m.kcal, s.kcal)) {
                            Eyebrow(t("Over"), color = C.Amber)
                            Text("+" + Fmt.num(m.kcal - s.kcal), style = T.NumL, color = C.Amber)
                        } else {
                            Eyebrow(t("Left"))
                            Text(Fmt.num((s.kcal - m.kcal).coerceAtLeast(0)), style = T.NumL, color = if (Stats.kcalHit(m.kcal, s.kcal)) C.Volt else C.Hi)
                        }
                    }
                }
                Gap(10.dp)
                Bar(m.kcal.toFloat() / s.kcal, color = C.Hi, height = 8.dp)
                Gap(18.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow(t("Protein"))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.g(m.protein), style = T.Display, color = C.Volt)
                            Text(" / " + t("%s g", s.protein), style = T.H3, color = C.Mid, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Eyebrow(t("Left"))
                        Text(t("%s g", (s.protein - m.protein).roundToInt().coerceAtLeast(0)), style = T.NumL, color = C.Hi)
                    }
                }
                Gap(10.dp)
                Bar((m.protein / s.protein).toFloat(), height = 8.dp)
                Gap(18.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { MacroLine(t("Fat"), com.forge.app.i18n.t("%1\$s / %2\$s g", Fmt.g(m.fat), s.fat), (m.fat / s.fat).toFloat(), C.Mid) }
                    Box(Modifier.weight(1f)) { MacroLine(t("Carbs"), com.forge.app.i18n.t("%1\$s / %2\$s g", Fmt.g(m.carbs), s.carbs), (m.carbs / s.carbs).toFloat(), C.Mid) }
                }
                Gap(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(t("Add food"), { sheetScan = false; sheet = true }, Modifier.weight(1.3f), icon = Icons.Rounded.Add, height = 48.dp)
                    SecondaryButton(t("Recipes"), { nav.go("recipes") }, Modifier.weight(1f), height = 48.dp, hPad = 8.dp)
                    com.forge.app.ui.components.IconSquareButton(Icons.Rounded.DocumentScanner, t("Scan a barcode or label"), { sheetScan = true; sheet = true })
                }
            }
            Gap(12.dp)
        }

        item { SectionTitle(t("Logged · %s", foods.size)) }
        if (foods.isEmpty()) {
            item {
                Text(t("Nothing logged for this day yet."), style = T.Small, color = C.Lo)
                Gap(12.dp)
            }
        }
        items(foods, key = { it.id }) { f ->
            FoodRow(f) { scope.launch { repo.deleteFood(f.id) } }
            Gap(8.dp)
        }

        item {
            Gap(8.dp)
            WeekPanel(s, weekStart, weekFoods, today)
            Gap(12.dp)
        }

        item {
            PlanPanel(s, planOpen) { planOpen = !planOpen }
            BottomSpace()
        }
    }

    if (sheet) AddFoodSheet(date, startScan = sheetScan) { sheet = false; sheetScan = false }
    if (sharing) com.forge.app.ui.share.ShareFuelDialog(date) { sharing = false }
}

/** This week's calories or protein per day, with the daily average. */
@Composable
private fun WeekPanel(s: Settings, weekStart: LocalDate, weekFoods: List<FoodEntity>, today: LocalDate) {
    var showKcal by remember { mutableStateOf(false) }
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val values = days.map { d ->
        val list = weekFoods.filter { it.date == d.toEpochDay() }
        (if (showKcal) list.sumOf { it.kcal }.toDouble() else list.sumOf { it.protein }).toFloat()
    }
    val logged = days.indices.filter { values[it] > 0f && !days[it].isAfter(today) }
    val avg = if (logged.isEmpty()) null else logged.map { values[it] }.average()
    val target = if (showKcal) s.kcal.toFloat() else s.protein.toFloat()
    val hits = logged.count { values[it] >= target * 0.95f }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(t("This week"), modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(t("Protein"), !showKcal, { showKcal = false })
                Chip(t("Calories"), showKcal, { showKcal = true })
            }
        }
        Gap(14.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat(t("Daily avg"), avg?.let { Fmt.num(it.roundToInt()) } ?: "—", Modifier.weight(1f), unit = if (showKcal) t("kcal") else t("g"))
            Stat(t("Target hit"), "$hits/${logged.size}", Modifier.weight(1f), color = if (logged.isNotEmpty() && hits == logged.size) C.Volt else C.Hi)
        }
        Gap(16.dp)
        BarChart(
            values = values,
            labels = days.map { Fmt.dowLetter(it) },
            target = target,
            highlight = days.indexOf(today),
            valueText = { if (it <= 0f) "" else if (showKcal) Fmt.num(it.roundToInt()) else it.roundToInt().toString() },
        )
    }
}

@Composable
private fun FoodRow(f: FoodEntity, onDelete: () -> Unit) {
    val time = remember(f.at) { SimpleDateFormat("HH:mm", Locale.US).format(Date(f.at)) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.S1).border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name + if (f.servings != 1.0) " ×${Fmt.kg(f.servings).removeSuffix(".0")}" else "", style = T.BodyS, color = C.Hi)
            Text("$time · ${macroText(Macros(f.kcal, f.protein, f.fat, f.carbs))}", style = T.Small, color = C.Mid)
        }
        IconBtn(Icons.Rounded.Close, t("Remove"), onDelete, tint = C.Lo, size = 40.dp)
    }
}

@Composable
private fun PlanPanel(s: Settings, open: Boolean, onToggle: () -> Unit) {
    Panel(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(t("Your nutrition plan"))
                Gap(2.dp)
                Text(t("%1${'$'}s kcal · %2${'$'}s g P · %3${'$'}s g F · %4${'$'}s g C", Fmt.num(s.kcal), s.protein, s.fat, s.carbs), style = T.BodyS, color = C.Hi)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = C.Mid)
        }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Gap(14.dp)
                Text(Plan.GOAL_TEXT, style = T.Body, color = C.Hi)
                Gap(10.dp)
                Text(t("Expected rate: %1${'$'}s–%2${'$'}s kg per week. %3${'$'}s.", Plan.RATE_MIN, Plan.RATE_MAX, Plan.TIMELINE), style = T.Small, color = C.Mid)
                Text(Plan.RATE_NOTE, style = T.Small, color = C.Lo)
                Gap(14.dp)
                Eyebrow(t("2-week check-in"))
                Gap(6.dp)
                Plan.checkIn.forEachIndexed { i, l ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${i + 1}", style = T.Small, color = C.Volt, modifier = Modifier.width(18.dp))
                        Text(l, style = T.Small, color = C.Hi)
                    }
                }
                Gap(14.dp)
                Eyebrow(t("Low-cost food options"))
                Gap(6.dp)
                Plan.lowCost.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, style = T.Small, color = C.Mid, modifier = Modifier.width(110.dp))
                        Text(v, style = T.Small, color = C.Hi)
                    }
                }
                Gap(14.dp)
                if (s.planNote.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.AmberSoft).padding(12.dp)) {
                        Column {
                            Eyebrow(t("Your plan notes"), color = C.Amber)
                            Gap(4.dp)
                            Text(s.planNote, style = T.Small, color = C.Hi)
                        }
                    }
                    Gap(10.dp)
                }
                Text(Plan.MEDICAL_NOTE, style = T.Tiny, color = C.Lo)
            }
        }
    }
}

// ------------------------------------------------------------------ add food

private sealed interface Online {
    data object Idle : Online
    data object Loading : Online
    data object Offline : Online
    data class Done(val items: List<OffProduct>) : Online
}

private fun recentPick(e: FoodEntity) = FoodPick(
    name = e.name,
    base = Macros(e.kcal, e.protein, e.fat, e.carbs) * (1.0 / e.servings.coerceAtLeast(0.01)),
    source = e.source,
    servings = e.servings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(date: LocalDate, startScan: Boolean = false, onDismiss: () -> Unit) {
    val repo = Graph.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<FoodPick?>(null) }
    var scanning by remember { mutableStateOf(startScan) }
    val recent by repo.recentFoods.collectAsState(emptyList())
    val custom by repo.customFoods.collectAsState(emptyList())
    val book by Dishes.book.collectAsState()
    var added by remember { mutableIntStateOf(0) }
    var online by remember { mutableStateOf<Online>(Online.Idle) }
    val q = query.trim()

    LaunchedEffect(Unit) { Dishes.refresh(ctx) }
    LaunchedEffect(q) {
        if (q.length < 3) { online = Online.Idle; return@LaunchedEffect }
        delay(700)
        online = Online.Loading
        online = when (val r = OpenFoodFacts.search(q)) {
            is OpenFoodFacts.Search.Ok -> Online.Done(r.items)
            OpenFoodFacts.Search.Offline -> Online.Offline
        }
    }

    fun log(item: LogItem) {
        scope.launch {
            repo.addFood(date, item.name, item.perServing, item.servings, item.source)
            item.saveAs?.let { (name, serving) -> repo.addCustomFood(name, serving, item.perServing) }
        }
        added++
        pick = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = C.S1,
        modifier = Modifier.statusBarsPadding(),
        dragHandle = { BottomSheetDefaults.DragHandle(color = C.LineStrong) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("ADD FOOD"), style = T.H2, color = C.Hi, modifier = Modifier.weight(1f))
                if (added > 0) Tag("$added added", color = C.OnVolt, bg = C.Volt)
            }
            Gap(12.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t("Foods"), t("Recipes"), t("Quick add"), t("My foods")).forEachIndexed { i, l ->
                    Chip(l, tab == i, { tab = i; pick = null })
                }
            }
            Gap(12.dp)

            pick?.let { p ->
                PickPanel(p, onCancel = { pick = null }, onAdd = ::log)
                Gap(12.dp)
            }

            when (tab) {
                0 -> {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ForgeField(query, { query = it }, t("Search foods, dishes, products")) }
                        HGap(10.dp)
                        Box(
                            Modifier.padding(top = 6.dp).size(56.dp).clip(RoundedCornerShape(14.dp)).background(C.S2)
                                .border(1.dp, C.LineStrong, RoundedCornerShape(14.dp)).clickable { scanning = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.DocumentScanner, t("Scan a barcode or label"), tint = C.Volt, modifier = Modifier.size(24.dp)) }
                    }
                    Gap(8.dp)
                    val recents = recent.distinctBy { it.name }.take(6).filter { q.isEmpty() || TextMatch.matches(q, it.name) }
                    val dishes = book?.dishes.orEmpty().filter { it.matches(q) }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                        if (recents.isNotEmpty()) {
                            item { Eyebrow(t("Recent"), modifier = Modifier.padding(vertical = 8.dp)) }
                            items(recents, key = { "r" + it.id }) { e ->
                                PickRow(e.name, macroText(Macros(e.kcal, e.protein, e.fat, e.carbs))) { pick = recentPick(e) }
                            }
                        }
                        if (dishes.isNotEmpty()) {
                            item { Eyebrow(t("Syrian dishes"), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                            items(dishes, key = { "d" + it.id }) { d ->
                                val first = d.portions.firstOrNull()
                                val amount = first?.let { "${it.label} · " + t("%s g", Fmt.g(it.g)) } ?: com.forge.app.i18n.t("%s g", 100)
                                val m = d.per100.per(first?.g ?: 100.0)
                                PickRow(d.name, "$amount · ${macroText(m)}", aside = d.ar.ifBlank { null }) { pick = book!!.pick(d) }
                            }
                        }
                        FoodGroup.entries.forEach { g ->
                            val list = Foods.all.filter { it.group == g && TextMatch.matches(q, it.name) }
                            if (list.isNotEmpty()) {
                                item { Eyebrow(g.label, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                                items(list, key = { it.id }) { f ->
                                    PickRow(f.name, "${f.serving} · ${macroText(f.m)}") {
                                        pick = FoodPick(f.name, base = f.m, source = "food:${f.id}", sourceNote = t("%s · USDA FoodData Central", f.serving))
                                    }
                                }
                            }
                        }
                        if (q.isNotEmpty()) {
                            item {
                                Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Eyebrow(t("Online · Open Food Facts"), modifier = Modifier.weight(1f))
                                    if (online == Online.Loading) CircularProgressIndicator(color = C.Volt, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                }
                            }
                            when (val o = online) {
                                Online.Idle -> item { Text(t("Type at least 3 letters to search packaged foods online."), style = T.Small, color = C.Lo) }
                                Online.Loading -> item { Text(t("Searching…"), style = T.Small, color = C.Lo) }
                                Online.Offline -> item { Text(t("Couldn't reach Open Food Facts. Check your connection, or scan the label."), style = T.Small, color = C.Amber) }
                                is Online.Done -> if (o.items.isEmpty()) {
                                    item { Text(t("No products with full nutrition data for \"%s\". Try another word, or scan the label.", q), style = T.Small, color = C.Lo) }
                                } else {
                                    items(o.items, key = { "o" + it.code }) { p ->
                                        PickRow(p.name, listOf(p.brand, t("per 100 g"), macroText(p.per100)).filter { it.isNotBlank() }.joinToString(" · ")) { pick = p.pick() }
                                    }
                                }
                            }
                        }
                        item {
                            Gap(16.dp)
                            Details(t("Where the numbers come from")) {
                                Text(
                                    t("Basics: USDA FoodData Central. Syrian dishes: lab-measured or FAO values, tap one to see its source. ") +
                                        t("Online: Open Food Facts, from product labels. Brands and home recipes differ, so check when you can."),
                                    style = T.Tiny, color = C.Lo,
                                )
                            }
                            Gap(40.dp)
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    MealType.entries.forEach { t ->
                        val list = Recipes.all.filter { it.type == t }
                        item { Eyebrow(t.label, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                        items(list, key = { it.id }) { r ->
                            PickRow(r.name, macroText(r.perServing)) { pick = FoodPick(r.name, base = r.perServing, source = "recipe:${r.id}", sourceNote = t("1 serving")) }
                        }
                    }
                    item { Gap(40.dp) }
                }
                2 -> QuickAdd(onAdd = { name, m, save ->
                    log(LogItem(name, m, 1.0, "quick", if (save) name to t("1 serving") else null))
                })
                3 -> LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    if (custom.isEmpty()) item {
                        Text(t("Foods you save from Quick add, a search or a scan show up here."), style = T.Small, color = C.Lo, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    items(custom, key = { it.id }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                PickRow(c.name, "${c.serving} · ${macroText(Macros(c.kcal, c.protein, c.fat, c.carbs))}") {
                                    pick = FoodPick(c.name, base = Macros(c.kcal, c.protein, c.fat, c.carbs), source = "custom", sourceNote = c.serving)
                                }
                            }
                            IconBtn(Icons.Rounded.Close, t("Delete"), { scope.launch { repo.deleteCustomFood(c.id) } }, tint = C.Lo, size = 36.dp)
                        }
                    }
                    item { Gap(40.dp) }
                }
            }
        }
    }

    if (scanning) ScanDialog(onDismiss = { scanning = false }, onPick = { p -> pick = p; tab = 0; scanning = false })
}

@Composable
private fun QuickAdd(onAdd: (String, Macros, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ForgeField(name, { name = it }, t("Name"))
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ForgeField(kcal, { kcal = it }, t("Calories"), Modifier.weight(1f), number = true, suffix = t("kcal"))
            ForgeField(p, { p = it }, t("Protein"), Modifier.weight(1f), number = true, suffix = t("g"))
        }
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ForgeField(f, { f = it }, t("Fat"), Modifier.weight(1f), number = true, suffix = t("g"))
            ForgeField(c, { c = it }, t("Carbs"), Modifier.weight(1f), number = true, suffix = t("g"))
        }
        Gap(8.dp)
        com.forge.app.ui.components.ToggleRow(t("Save to My foods"), null, save) { save = it }
        Gap(8.dp)
        val k = kcal.replace(',', '.').toDoubleOrNull()
        PrimaryButton(t("Add"), {
            if (k != null) {
                onAdd(
                    name.ifBlank { t("Quick add") },
                    Macros(k.roundToInt(), p.replace(',', '.').toDoubleOrNull() ?: 0.0, f.replace(',', '.').toDoubleOrNull() ?: 0.0, c.replace(',', '.').toDoubleOrNull() ?: 0.0),
                    save,
                )
                name = ""; kcal = ""; p = ""; f = ""; c = ""
            }
        }, enabled = k != null, height = 50.dp)
        Gap(40.dp)
    }
}

// ------------------------------------------------------------------ recipes

@Composable
fun RecipesScreen(nav: Nav) {
    var filter by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Recipes"), t("Budget, high-protein, from your plan's food list"), onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            item {
                SectionTitle(t("Full-day plans"))
                Text(t("Each one lands close to %s kcal. Log meals as you eat them.", Plan.KCAL), style = T.Small, color = C.Mid)
                Gap(10.dp)
            }
            items(DayPlans.all, key = { it.id }) { p ->
                val t = p.total
                Panel(onClick = { nav.go("plan/${p.id}") }, padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name.uppercase(), style = T.H3, color = C.Hi)
                            Text(p.note, style = T.Small, color = C.Mid)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.num(t.kcal), style = T.Num, color = C.Hi)
                            Text("${Fmt.g(t.protein)} g P", style = T.Small, color = C.Volt)
                        }
                    }
                }
                Gap(10.dp)
            }
            item {
                SectionTitle(t("Meals"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("All"), filter == null, { filter = null })
                    MealType.entries.forEach { t -> Chip(t.label, filter == t.name, { filter = t.name }) }
                    Chip(t("Batch cook"), filter == "BATCH", { filter = "BATCH" })
                }
                Gap(12.dp)
            }
            val list = Recipes.all.filter {
                when (filter) {
                    null -> true
                    "BATCH" -> it.batch
                    else -> it.type.name == filter
                }
            }
            items(list, key = { it.id }) { r ->
                RecipeCard(r) { nav.recipe(r.id) }
                Gap(10.dp)
            }
            item { BottomSpace() }
        }
    }
}

@Composable
private fun RecipeCard(r: Recipe, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(t("%1${'$'}s · %2${'$'}s min", r.type.label, r.minutes) + if (r.servings > 1) t(" · serves %s", r.servings) else "")
                Gap(4.dp)
                Text(r.name.uppercase(), style = T.H3, color = C.Hi)
            }
            if (r.batch) Tag(t("Batch"))
        }
        Gap(10.dp)
        Row(Modifier.fillMaxWidth()) {
            MacroCell(t("kcal"), Fmt.num(r.perServing.kcal), C.Hi, Modifier.weight(1f))
            MacroCell("protein", t("%s g", Fmt.g(r.perServing.protein)), C.Volt, Modifier.weight(1f))
            MacroCell("fat", t("%s g", Fmt.g(r.perServing.fat)), C.Mid, Modifier.weight(1f))
            MacroCell("carbs", t("%s g", Fmt.g(r.perServing.carbs)), C.Mid, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = T.Num.copy(fontSize = T.Num.fontSize * 0.85f), color = color)
        Text(label, style = T.Tiny, color = C.Lo)
    }
}

@Composable
fun RecipeScreen(nav: Nav, id: String) {
    val r = Recipes.byId(id) ?: return
    val s = LocalSettings.current
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    val checked = remember { mutableStateListOf<Int>() }
    var logged by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar(r.name, t("%1${'$'}s · %2${'$'}s min", r.type.label, r.minutes), onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Gap(6.dp)
            Text(r.name.uppercase(), style = T.H1, color = C.Hi)
            Gap(6.dp)
            Text(r.why, style = T.Body, color = C.Mid)
            Gap(18.dp)
            Panel {
                Eyebrow(t("Per serving") + if (r.servings > 1) t(" · recipe makes %s", r.servings) else "")
                Gap(10.dp)
                Row(Modifier.fillMaxWidth()) {
                    Stat(t("kcal"), Fmt.num(r.perServing.kcal), Modifier.weight(1f))
                    Stat(t("Protein"), Fmt.g(r.perServing.protein), Modifier.weight(1f), unit = t("g"), color = C.Volt)
                    Stat(t("Fat"), Fmt.g(r.perServing.fat), Modifier.weight(0.8f), unit = t("g"))
                    Stat(t("Carbs"), Fmt.g(r.perServing.carbs), Modifier.weight(0.8f), unit = t("g"))
                }
                Gap(12.dp)
                Text(
                    t("%1${'$'}s%% of your calories and %2${'$'}s%% of your protein.", r.perServing.kcal * 100 / s.kcal, (r.perServing.protein * 100 / s.protein).roundToInt()),
                    style = T.Small, color = C.Mid,
                )
            }
            Gap(20.dp)
            Text(t("INGREDIENTS"), style = T.H3, color = C.Hi)
            Gap(8.dp)
            r.ingredients.forEachIndexed { i, ing ->
                val on = i in checked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (on) checked.remove(i) else checked.add(i) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (on) C.Volt else C.S2).border(1.dp, if (on) C.Volt else C.LineStrong, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Rounded.Check, null, tint = C.OnVolt, modifier = Modifier.size(14.dp)) }
                    HGap(12.dp)
                    Text(ing, style = T.Body, color = if (on) C.Lo else C.Hi)
                }
            }
            Gap(18.dp)
            Text(t("METHOD"), style = T.H3, color = C.Hi)
            Gap(8.dp)
            r.steps.forEachIndexed { i, st ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("${i + 1}", style = T.Num.copy(fontSize = T.Num.fontSize * 0.75f), color = C.Volt, modifier = Modifier.width(24.dp))
                    Text(st, style = T.Body, color = C.Hi)
                }
            }
            Gap(12.dp)
            Hairline()
            Gap(10.dp)
            Text(t("Macros calculated from USDA values for the amounts listed."), style = T.Tiny, color = C.Lo)
            Gap(24.dp)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            PrimaryButton(
                if (logged == 0) t("Log 1 serving") else t("Logged ×%s · add another", logged),
                { scope.launch { repo.addFood(LocalDate.now(), r.name, r.perServing, 1.0, "recipe:${r.id}") }; logged++ },
                icon = if (logged == 0) Icons.Rounded.Add else Icons.Rounded.Check,
            )
        }
    }
}

@Composable
fun PlanScreen(nav: Nav, id: String) {
    val p = DayPlans.all.firstOrNull { it.id == id } ?: return
    val s = LocalSettings.current
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    val loggedIds = remember { mutableStateListOf<String>() }
    val t = p.total
    Column(Modifier.fillMaxSize()) {
        TopBar(p.name, p.note, onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            item {
                Panel {
                    Eyebrow(t("Day total vs plan"))
                    Gap(12.dp)
                    MacroLine(t("Calories"), "${Fmt.num(t.kcal)} / ${Fmt.num(s.kcal)}", t.kcal.toFloat() / s.kcal, C.Hi)
                    Gap(10.dp)
                    MacroLine(t("Protein"), com.forge.app.i18n.t("%1\$s / %2\$s g", Fmt.g(t.protein), s.protein), (t.protein / s.protein).toFloat())
                    Gap(10.dp)
                    MacroLine(t("Fat"), com.forge.app.i18n.t("%1\$s / %2\$s g", Fmt.g(t.fat), s.fat), (t.fat / s.fat).toFloat(), C.Mid)
                    Gap(10.dp)
                    MacroLine(t("Carbs"), com.forge.app.i18n.t("%1\$s / %2\$s g", Fmt.g(t.carbs), s.carbs), (t.carbs / s.carbs).toFloat(), C.Mid)
                }
                Gap(14.dp)
            }
            items(p.recipes, key = { it.id }) { r ->
                Panel(onClick = { nav.recipe(r.id) }, padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(r.type.label)
                            Gap(2.dp)
                            Text(r.name, style = T.BodyS, color = C.Hi)
                            Text(macroText(r.perServing), style = T.Small, color = C.Mid)
                        }
                        val done = r.id in loggedIds
                        SecondaryButton(
                            if (done) t("Logged") else t("Log"),
                            { if (!done) { scope.launch { repo.addFood(LocalDate.now(), r.name, r.perServing, 1.0, "recipe:${r.id}") }; loggedIds.add(r.id) } },
                            height = 38.dp, color = if (done) C.Mid else C.Volt, border = if (done) C.Line else C.Volt.copy(alpha = 0.5f),
                        )
                    }
                }
                Gap(10.dp)
            }
            item {
                Text(t("Swap any meal for another recipe with similar protein. The total is what matters."), style = T.Small, color = C.Lo)
                BottomSpace()
            }
        }
    }
}
