package com.forge.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forge.app.Graph
import com.forge.app.data.Repo
import com.forge.app.data.UserProgram
import com.forge.app.domain.Equip
import com.forge.app.domain.Exercise
import com.forge.app.domain.Exercises
import com.forge.app.domain.MuscleGroup
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.ProgramTemplate
import com.forge.app.domain.RepUnit
import com.forge.app.domain.Templates
import com.forge.app.ui.LocalPrograms
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.SectionTitle
import com.forge.app.ui.components.Segmented
import com.forge.app.ui.components.Stepper
import com.forge.app.ui.components.Tag
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

// ------------------------------------------------------------------ programs list

@Composable
fun ProgramsScreen(nav: Nav) {
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    val snap = LocalPrograms.current
    val programs = snap?.programs.orEmpty()
    val activeId = snap?.active?.id
    var addTemplate by remember { mutableStateOf<ProgramTemplate?>(null) }
    var deleting by remember { mutableStateOf<UserProgram?>(null) }
    var sharing by remember { mutableStateOf<UserProgram?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var received by remember { mutableStateOf<com.forge.app.data.ProgramCode.Shared?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Programs", "Pick one, edit it, or build your own", onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            item {
                Gap(4.dp)
                Panel(padding = PaddingValues(16.dp)) {
                    Text("GOT A PROGRAM FROM SOMEONE?", style = T.H3, color = C.Hi)
                    Text("Scan the QR code a coach or friend shared, or pick a screenshot of it.", style = T.Small, color = C.Mid)
                    Gap(12.dp)
                    PrimaryButton("Scan a program", { scanning = true }, height = 46.dp, icon = Icons.Rounded.QrCodeScanner)
                }
                Gap(6.dp)
                SectionTitle("Your programs")
            }
            items(programs, key = { it.id }) { p ->
                val inUse = p.id == activeId
                Panel(accent = inUse, border = if (inUse) C.Volt.copy(alpha = 0.45f) else C.Line, onClick = { nav.go("program/${p.id}") }, padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (inUse) { Tag("In use", color = C.OnVolt, bg = C.Volt); Gap(6.dp) }
                            Text(p.name.uppercase(), style = T.H3, color = C.Hi, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${p.daysPerWeek} days a week · ${p.days.sumOf { it.exercises.size }} exercises",
                                style = T.Small, color = C.Mid,
                            )
                        }
                    }
                    Gap(12.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!inUse) SecondaryButton("Use", { scope.launch { repo.activateProgram(p.id) } }, Modifier.weight(1f), height = 42.dp, color = C.Volt, border = C.Volt.copy(alpha = 0.5f), hPad = 6.dp)
                        SecondaryButton("Edit", { nav.go("program/${p.id}") }, Modifier.weight(1f), height = 42.dp, hPad = 6.dp)
                        SecondaryButton("Share", { sharing = p }, Modifier.weight(1f), height = 42.dp, hPad = 6.dp, icon = Icons.Rounded.QrCode2)
                    }
                    Gap(4.dp)
                    Row {
                        TextAction("Make a copy", { scope.launch { repo.duplicateProgram(p.id) } }, color = C.Mid, style = T.Small)
                        if (!inUse) TextAction("Delete", { deleting = p }, color = C.Red, style = T.Small)
                    }
                }
                Gap(10.dp)
            }

            item {
                Gap(4.dp)
                SectionTitle("Start a new one")
                Panel(onClick = {
                    scope.launch { val id = repo.createFromTemplate(null, "My program"); nav.go("program/$id") }
                }, padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = C.Volt)
                        HGap(10.dp)
                        Column {
                            Text("BUILD FROM SCRATCH", style = T.H3, color = C.Hi)
                            Text("Your own days and exercises", style = T.Small, color = C.Mid)
                        }
                    }
                }
                Gap(10.dp)
            }
            items(Templates.all, key = { "t" + it.id }) { t ->
                Panel(onClick = { addTemplate = t }, padding = PaddingValues(16.dp)) {
                    Text(t.name.uppercase(), style = T.H3, color = C.Hi)
                    Text("${t.daysPerWeek} days a week · ${t.equipment}" + (t.credit?.let { " · $it" } ?: ""), style = T.Small, color = C.Volt)
                    Gap(4.dp)
                    Text(t.note, style = T.Small, color = C.Mid)
                }
                Gap(10.dp)
            }
            item { BottomSpace() }
        }
    }

    addTemplate?.let { t ->
        ForgeDialog(
            title = t.name,
            onDismiss = { addTemplate = null },
            confirm = "Add and use",
            onConfirm = {
                addTemplate = null
                scope.launch { repo.activateProgram(repo.createFromTemplate(t.id)) }
            },
        ) {
            Text(t.note, style = T.Body, color = C.Mid)
            Gap(10.dp)
            t.days.forEach { d ->
                Text(d.name, style = T.BodyS, color = C.Hi)
                Text(d.exercises.joinToString(", ") { it.exercise.name }, style = T.Small, color = C.Mid)
                Gap(6.dp)
            }
            Gap(4.dp)
            TextAction("Add without using it", {
                addTemplate = null
                scope.launch { repo.createFromTemplate(t.id) }
            }, color = C.Mid, style = T.Small)
        }
    }
    sharing?.let { p -> com.forge.app.ui.share.ShareProgramDialog(p) { sharing = null } }
    if (scanning) {
        com.forge.app.ui.share.ScanProgramDialog(onDismiss = { scanning = false }) { shared -> scanning = false; received = shared }
    }
    received?.let { shared ->
        com.forge.app.ui.share.ImportProgramDialog(shared, onDismiss = { received = null }) { use ->
            received = null
            scope.launch {
                val id = repo.importProgram(shared)
                if (use) repo.activateProgram(id)
            }
        }
    }
    deleting?.let { p ->
        ForgeDialog(
            title = "Delete ${p.name}?",
            onDismiss = { deleting = null },
            confirm = "Delete",
            destructive = true,
            onConfirm = { deleting = null; scope.launch { repo.deleteProgram(p.id) } },
        ) {
            Text("Workouts you already logged with it stay in your history.", style = T.Body, color = C.Mid)
        }
    }
}

// ------------------------------------------------------------------ program editor

private class DayEdit(val id: String?, name: String, exercises: List<ProgramExercise>) {
    var name by mutableStateOf(name)
    val exercises = exercises.toMutableStateList()
}

private val weekdays = DayOfWeek.entries

@Composable
fun ProgramEditorScreen(nav: Nav, programId: String) {
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    val s = LocalSettings.current
    val program = remember(programId) { com.forge.app.data.ProgramStore.program(programId) }
    if (program == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Program", null, onBack = { nav.back() })
            Text("This program no longer exists.", style = T.Body, color = C.Mid, modifier = Modifier.padding(20.dp))
        }
        return
    }

    var name by remember { mutableStateOf(program.name) }
    val days = remember { program.days.map { DayEdit(it.id, it.name, it.exercises) }.toMutableStateList() }
    // Monday..Sunday -> index into days, or null for rest.
    val schedule = remember {
        program.schedule.map { slot -> program.days.indexOfFirst { it.id == slot }.takeIf { it >= 0 } }.toMutableStateList()
    }
    var dirty by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<DayEdit?>(null) }
    var editing by remember { mutableStateOf<Pair<DayEdit, Int>?>(null) }
    var renaming by remember { mutableStateOf<DayEdit?>(null) }
    var removingDay by remember { mutableStateOf<Int?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val isActive = s.activeProgramId == program.id

    fun touch() { dirty = true }
    fun leave() { if (dirty) confirmLeave = true else nav.back() }
    fun save() {
        scope.launch {
            repo.saveProgram(
                program.id, name, program.note,
                days.map { Repo.DayDraft(it.id, it.name, it.exercises.toList()) },
                schedule.toList(),
            )
            dirty = false
            nav.back()
        }
    }

    BackHandler { leave() }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (isActive) "Edit program · in use" else "Edit program", null, onBack = { leave() }) {
            TextAction("Save", { save() }, color = if (dirty) C.Volt else C.Mid)
        }
        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            item {
                ForgeField(name, { name = it.take(40); touch() }, "Program name")
                Gap(16.dp)
                SectionTitle("Your week")
                Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    weekdays.forEachIndexed { i, d ->
                        if (i > 0) Hairline()
                        var open by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = T.BodyM, color = C.Mid, modifier = Modifier.width(52.dp))
                                val idx = schedule[i]
                                Text(
                                    idx?.let { days.getOrNull(it)?.name } ?: "Rest",
                                    style = T.BodyM, color = if (idx == null) C.Lo else C.Hi,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Icon(Icons.Rounded.ExpandMore, null, tint = C.Mid)
                            }
                            DropdownMenu(open, { open = false }, containerColor = C.S2) {
                                days.forEachIndexed { k, day ->
                                    DropdownMenuItem(
                                        text = { Text(day.name, style = T.BodyM, color = if (schedule[i] == k) C.Volt else C.Hi) },
                                        onClick = { open = false; schedule[i] = k; touch() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Rest", style = T.BodyM, color = if (schedule[i] == null) C.Volt else C.Hi) },
                                    onClick = { open = false; schedule[i] = null; touch() },
                                )
                            }
                        }
                    }
                }
                Gap(6.dp)
                Text("${schedule.count { it != null }} training days a week. A day can repeat in the week.", style = T.Small, color = C.Lo)
                Gap(16.dp)
            }

            days.forEachIndexed { di, day ->
                item(key = "day$di-${day.id}") {
                    Panel(padding = PaddingValues(0.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { renaming = day }) {
                                Eyebrow("Day ${di + 1} · ${day.exercises.sumOf { it.sets }} sets")
                                Text(day.name.uppercase(), style = T.H3, color = C.Hi, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextAction("Rename", { renaming = day }, color = C.Mid, style = T.Small)
                            IconBtn(Icons.Rounded.Close, "Remove day", { removingDay = di }, tint = C.Lo, size = 36.dp)
                        }
                        day.exercises.forEachIndexed { ei, pe ->
                            Hairline(Modifier.padding(horizontal = 16.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = day to ei }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(pe.displayName, style = T.BodyM, color = C.Hi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pe.prescription, style = T.Small, color = C.Mid)
                                }
                                IconBtn(Icons.Rounded.KeyboardArrowUp, "Move up", {
                                    if (ei > 0) { day.exercises.add(ei - 1, day.exercises.removeAt(ei)); touch() }
                                }, tint = if (ei > 0) C.Mid else C.S3, size = 34.dp)
                                IconBtn(Icons.Rounded.KeyboardArrowDown, "Move down", {
                                    if (ei < day.exercises.lastIndex) { day.exercises.add(ei + 1, day.exercises.removeAt(ei)); touch() }
                                }, tint = if (ei < day.exercises.lastIndex) C.Mid else C.S3, size = 34.dp)
                                IconBtn(Icons.Rounded.Close, "Remove", { day.exercises.removeAt(ei); touch() }, tint = C.Lo, size = 34.dp)
                            }
                        }
                        Hairline(Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { picking = day }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = C.Volt, modifier = Modifier.size(20.dp))
                            HGap(8.dp)
                            Text("Add exercise", style = T.BodyS, color = C.Volt)
                        }
                    }
                    Gap(12.dp)
                }
            }

            item {
                SecondaryButton("Add a day", {
                    days.add(DayEdit(null, "Day ${days.size + 1}", emptyList())); touch()
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, height = 48.dp)
                Gap(16.dp)
                PrimaryButton("Save program", { save() }, enabled = dirty || program.days.isEmpty())
                BottomSpace()
            }
        }
    }

    picking?.let { day ->
        ExercisePickerSheet(
            onDismiss = { picking = null },
            onPick = { ex ->
                day.exercises.add(defaultPrescription(ex))
                touch()
                picking = null
            },
        )
    }
    editing?.let { (day, ei) ->
        val pe = day.exercises.getOrNull(ei)
        if (pe == null) { editing = null } else {
            PrescriptionDialog(pe, onDismiss = { editing = null }) { updated ->
                day.exercises[ei] = updated; touch(); editing = null
            }
        }
    }
    renaming?.let { day ->
        var text by remember(day) { mutableStateOf(day.name) }
        ForgeDialog("Day name", { renaming = null }, "Save", {
            day.name = text.trim().ifBlank { day.name }; touch(); renaming = null
        }) {
            ForgeField(text, { text = it.take(40) }, "Name, e.g. Push or Legs")
        }
    }
    removingDay?.let { di ->
        ForgeDialog("Remove ${days.getOrNull(di)?.name ?: "day"}?", { removingDay = null }, "Remove", {
            days.removeAt(di)
            for (i in schedule.indices) {
                val v = schedule[i]
                schedule[i] = when {
                    v == null -> null
                    v == di -> null
                    v > di -> v - 1
                    else -> v
                }
            }
            touch(); removingDay = null
        }, destructive = true) {
            Text("Its exercises are removed from the program. Workouts you already logged stay in your history.", style = T.Body, color = C.Mid)
        }
    }
    if (confirmLeave) {
        ForgeDialog("Discard changes?", { confirmLeave = false }, "Discard", { confirmLeave = false; nav.back() }, dismiss = "Keep editing", destructive = true) {
            Text("Your edits to this program haven't been saved.", style = T.Body, color = C.Mid)
        }
    }
}

/** Sensible starting sets and reps for an exercise just added to a day. */
private fun defaultPrescription(ex: Exercise): ProgramExercise {
    val timed = setOf("plank", "hollow_hold", "wall_sit", "farmers_carry")
    return when {
        ex.id == "side_plank" -> ProgramExercise(ex.id, 3, 20, 40, RepUnit.SEC_SIDE)
        ex.id in timed -> ProgramExercise(ex.id, 3, 30, 45, RepUnit.SEC)
        ex.compound -> ProgramExercise(ex.id, 3, 6, 10)
        else -> ProgramExercise(ex.id, 3, 10, 15)
    }
}

@Composable
private fun PrescriptionDialog(pe: ProgramExercise, onDismiss: () -> Unit, onSave: (ProgramExercise) -> Unit) {
    var sets by remember { mutableIntStateOf(pe.sets) }
    var min by remember { mutableIntStateOf(pe.repMin) }
    var max by remember { mutableIntStateOf(pe.repMax) }
    var unit by remember { mutableStateOf(pe.unit) }
    val timed = unit.timed
    ForgeDialog(pe.exercise.name, onDismiss, "Save", {
        onSave(pe.copy(sets = sets, repMin = minOf(min, max), repMax = maxOf(min, max), unit = unit))
    }) {
        Eyebrow("Counted in")
        Gap(6.dp)
        Segmented(listOf("Reps", "Seconds"), if (timed) 1 else 0, { i ->
            val toTimed = i == 1
            if (toTimed != timed) {
                unit = when {
                    toTimed && unit.perSide -> RepUnit.SEC_SIDE
                    toTimed -> RepUnit.SEC
                    unit == RepUnit.SEC_SIDE -> RepUnit.SIDE
                    else -> RepUnit.NONE
                }
                if (toTimed) { min = 30; max = 45 } else { min = 8; max = 12 }
            }
        }, colors = listOf(C.Volt, C.Volt))
        Gap(10.dp)
        Eyebrow("Each side separately")
        Gap(6.dp)
        Segmented(listOf("No", "Per side", "Per leg"), when (unit) { RepUnit.SIDE, RepUnit.SEC_SIDE -> 1; RepUnit.LEG -> 2; else -> 0 }, { i ->
            unit = when {
                timed -> if (i == 0) RepUnit.SEC else RepUnit.SEC_SIDE
                i == 1 -> RepUnit.SIDE
                i == 2 -> RepUnit.LEG
                else -> RepUnit.NONE
            }
        }, colors = listOf(C.Volt, C.Volt, C.Volt))
        Gap(14.dp)
        val step = if (timed) 5 else 1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sets", style = T.BodyM, color = C.Hi, modifier = Modifier.width(90.dp))
            Stepper("$sets", { sets = (sets - 1).coerceAtLeast(1) }, { sets = (sets + 1).coerceAtMost(10) }, Modifier.weight(1f))
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (timed) "Min sec" else "Min reps", style = T.BodyM, color = C.Hi, modifier = Modifier.width(90.dp))
            Stepper("$min", { min = (min - step).coerceAtLeast(1) }, { min = (min + step).coerceAtMost(600) }, Modifier.weight(1f))
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (timed) "Max sec" else "Max reps", style = T.BodyM, color = C.Hi, modifier = Modifier.width(90.dp))
            Stepper("$max", { max = (max - step).coerceAtLeast(1) }, { max = (max + step).coerceAtMost(600) }, Modifier.weight(1f))
        }
        Gap(8.dp)
        Text(
            "Work in the range. When every set hits the top, the app tells you to add weight.",
            style = T.Small, color = C.Lo,
        )
    }
}

// ------------------------------------------------------------------ exercise picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snap = LocalPrograms.current
    var query by remember { mutableStateOf("") }
    var equip by remember { mutableStateOf<Equip?>(null) }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    val all = remember(snap) { Exercises.everything() }
    val q = query.trim().lowercase()
    val list = all.filter { e ->
        (equip == null || e.equip == equip) && (group == null || e.group == group) &&
            (q.isEmpty() || e.name.lowercase().contains(q) || e.muscles.lowercase().contains(q))
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
                Text("ADD EXERCISE", style = T.H2, color = C.Hi, modifier = Modifier.weight(1f))
                TextAction("Create your own", { creating = true })
            }
            Gap(10.dp)
            Box(Modifier.padding(horizontal = 20.dp)) { ForgeField(query, { query = it }, "Search ${all.size} exercises") }
            Gap(10.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("All muscles", group == null, { group = null })
                MuscleGroup.entries.forEach { g -> Chip(g.label, group == g, { group = if (group == g) null else g }) }
            }
            Gap(6.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Any equipment", equip == null, { equip = null })
                Equip.entries.forEach { e -> Chip(e.label, equip == e, { equip = if (equip == e) null else e }) }
            }
            Gap(8.dp)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                if (list.isEmpty()) {
                    item {
                        Text("Nothing matches. Clear a filter, or create it as your own exercise.", style = T.Small, color = C.Lo, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                items(list, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(e) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = T.BodyS, color = C.Hi)
                            Text("${e.group.label} · ${e.equip.label}" + if (e.muscles.isNotBlank() && !e.custom) " · ${e.muscles}" else "", style = T.Small, color = C.Mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (e.custom) {
                            Tag("Yours")
                            IconBtn(Icons.Rounded.Close, "Delete ${e.name}", { deleting = e }, tint = C.Lo, size = 36.dp)
                        }
                    }
                    Hairline()
                }
                item { Gap(40.dp) }
            }
        }
    }

    if (creating) {
        CustomExerciseDialog(onDismiss = { creating = false }) { name, eq, g, rest ->
            creating = false
            scope.launch {
                val id = repo.addCustomExercise(name, eq, g, rest, "")
                onPick(Exercises.byId(id))
            }
        }
    }
    deleting?.let { e ->
        ForgeDialog("Delete ${e.name}?", { deleting = null }, "Delete", {
            deleting = null; scope.launch { repo.deleteCustomExercise(e.id) }
        }, destructive = true) {
            Text("It disappears from this list. Programs and workouts that already use it keep it.", style = T.Body, color = C.Mid)
        }
    }
}

@Composable
private fun CustomExerciseDialog(onDismiss: () -> Unit, onCreate: (String, Equip, MuscleGroup, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var equip by remember { mutableStateOf(Equip.DUMBBELL) }
    var group by remember { mutableStateOf(MuscleGroup.CHEST) }
    var rest by remember { mutableIntStateOf(90) }
    ForgeDialog("Your own exercise", onDismiss, "Create", { if (name.isNotBlank()) onCreate(name, equip, group, rest) }) {
        ForgeField(name, { name = it.take(40) }, "Name")
        Gap(12.dp)
        Eyebrow("Equipment")
        Gap(6.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Equip.entries.forEach { e -> Chip(e.label, equip == e, { equip = e }) }
        }
        Gap(12.dp)
        Eyebrow("Muscle group")
        Gap(6.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MuscleGroup.entries.forEach { g -> Chip(g.label, group == g, { group = g }) }
        }
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rest", style = T.BodyM, color = C.Hi, modifier = Modifier.width(70.dp))
            Stepper("${rest}s", { rest = (rest - 15).coerceAtLeast(15) }, { rest = (rest + 15).coerceAtMost(300) }, Modifier.weight(1f))
        }
    }
}
