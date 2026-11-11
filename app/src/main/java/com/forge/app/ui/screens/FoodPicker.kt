package com.forge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forge.app.data.FoodPick
import com.forge.app.data.Fmt
import com.forge.app.data.chip
import com.forge.app.data.gramsLabel
import com.forge.app.data.per
import com.forge.app.domain.Macros
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.Stepper
import com.forge.app.ui.components.ToggleRow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlin.math.roundToInt

/** What the pick panel hands back: one line in the food log, and optionally a My foods entry. */
data class LogItem(
    val name: String,
    val perServing: Macros,
    val servings: Double,
    val source: String,
    /** Non-null: also save to My foods with this serving description. */
    val saveAs: Pair<String, String>? = null,
)

internal fun macroText(m: Macros) = "${Fmt.num(m.kcal)} kcal · ${Fmt.g(m.protein)} g protein"
internal fun fullMacro(m: Macros) = "${Fmt.num(m.kcal)} kcal · P ${Fmt.g(m.protein)} · F ${Fmt.g(m.fat)} · C ${Fmt.g(m.carbs)}"

private fun num(s: String) = s.replace(',', '.').trim().toDoubleOrNull()
private fun field(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

@Composable
fun PickPanel(pick: FoodPick, onCancel: () -> Unit, onAdd: (LogItem) -> Unit) {
    val gramBased = pick.per100 != null
    val original = pick.per100 ?: pick.base ?: Macros.ZERO
    var portion by remember(pick) { mutableIntStateOf(0) }
    var count by remember(pick) { mutableDoubleStateOf(pick.servings) }
    var gramsText by remember(pick) { mutableStateOf(field(pick.portions.firstOrNull()?.grams ?: 100.0)) }
    var editing by remember(pick) { mutableStateOf(false) }
    var kcal by remember(pick) { mutableStateOf(original.kcal.toString()) }
    var p by remember(pick) { mutableStateOf(field(original.protein)) }
    var f by remember(pick) { mutableStateOf(field(original.fat)) }
    var c by remember(pick) { mutableStateOf(field(original.carbs)) }
    var save by remember(pick) { mutableStateOf(false) }

    val edited: Macros? = if (!editing) original else {
        val k = num(kcal); val pp = num(p); val ff = num(f); val cc = num(c)
        if (k == null || pp == null || ff == null || cc == null) null else Macros(k.roundToInt(), pp, ff, cc)
    }
    val gramsMode = gramBased && portion >= pick.portions.size
    val grams = num(gramsText)?.takeIf { it > 0 && it < 5000 }

    // Name in the log, macros for one serving, and how many servings.
    val line: Triple<String, Macros, Double>? = when {
        edited == null -> null
        !gramBased -> Triple(pick.name, edited, count)
        gramsMode -> grams?.let { Triple("${pick.name} · ${gramsLabel(it)}", edited.per(it), 1.0) }
        else -> pick.portions[portion].let { pt -> Triple("${pick.name} · ${pt.label}", edited.per(pt.grams ?: 100.0), count) }
    }

    Column(
        Modifier.padding(horizontal = 20.dp).heightIn(max = 460.dp)
            .clip(RoundedCornerShape(18.dp)).background(C.S2)
            .border(1.dp, C.Volt.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pick.name, style = T.BodyS, color = C.Hi)
                if (pick.detail.isNotBlank()) Text(pick.detail, style = T.Small, color = C.Mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(Icons.Rounded.Close, "Cancel", onCancel, tint = C.Mid, size = 36.dp)
        }
        if (gramBased) {
            Gap(10.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pick.portions.forEachIndexed { i, pt -> Chip(pt.chip(), portion == i, { portion = i }) }
                Chip("Grams", gramsMode, { portion = pick.portions.size })
            }
        }
        Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gramsMode) {
                ForgeField(gramsText, { gramsText = it }, "Amount", Modifier.width(140.dp), number = true, suffix = "g")
            } else {
                Stepper(
                    "${Fmt.kg(count).removeSuffix(".0")}×",
                    onMinus = { count = (count - 0.5).coerceAtLeast(0.5) },
                    onPlus = { count += 0.5 },
                    modifier = Modifier.width(140.dp),
                )
            }
            HGap(12.dp)
            Text(line?.let { fullMacro(it.second * it.third) } ?: "Check the values", style = T.Small, color = if (line == null) C.Amber else C.Hi, modifier = Modifier.weight(1f))
        }
        if (pick.sourceNote.isNotBlank()) {
            Gap(10.dp)
            Text(pick.sourceNote, style = T.Tiny, color = C.Lo)
        }
        if (pick.fromOutside) {
            Gap(8.dp)
            Text(
                if (editing) "Values ${if (gramBased) "per 100 g" else "per serving"}" else "Edit values",
                style = T.Small, color = C.Volt,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editing = true }.padding(vertical = 6.dp),
            )
            if (editing) {
                Gap(4.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ForgeField(kcal, { kcal = it }, "Calories", Modifier.weight(1f), number = true, suffix = "kcal")
                    ForgeField(p, { p = it }, "Protein", Modifier.weight(1f), number = true, suffix = "g")
                }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ForgeField(f, { f = it }, "Fat", Modifier.weight(1f), number = true, suffix = "g")
                    ForgeField(c, { c = it }, "Carbs", Modifier.weight(1f), number = true, suffix = "g")
                }
            }
            ToggleRow("Save to My foods", null, save) { save = it }
        }
        Gap(8.dp)
        PrimaryButton("Add", {
            line?.let { (name, m, n) ->
                val serving = when {
                    !gramBased -> "1 serving"
                    gramsMode -> gramsLabel(grams ?: 100.0)
                    else -> pick.portions[portion].chip()
                }
                onAdd(LogItem(name, m, n, pick.source, if (save) pick.name to serving else null))
            }
        }, enabled = line != null, height = 46.dp, icon = Icons.Rounded.Check)
    }
}

@Composable
internal fun PickRow(title: String, sub: String, aside: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.BodyM, color = C.Hi, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = T.Small, color = C.Mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (aside != null) {
            HGap(10.dp)
            Text(aside, style = T.Small, color = C.Mid, maxLines = 1)
        }
        HGap(8.dp)
        Icon(Icons.Rounded.Add, null, tint = C.Volt, modifier = Modifier.size(20.dp))
    }
}
