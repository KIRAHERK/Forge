package com.forge.app.ui.screens

import com.forge.app.i18n.t

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.MeasurementEntity
import com.forge.app.data.Photos
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun PhotosPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val photos = remember(version) { Photos.list(ctx) }
    var viewing by remember { mutableStateOf<Photos.Photo?>(null) }
    var camUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { Photos.import(ctx, uri, LocalDate.now()); version++ }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch { Photos.import(ctx, u, LocalDate.now()); version++ }
    }

    Panel {
        Eyebrow(t("Progress photos"))
        Gap(4.dp)
        if (photos.isEmpty()) {
            Text(t("Every two weeks. Same spot, light and pose."), style = T.Small, color = C.Mid)
        } else {
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoTile(photos.first(), t("Start"), Modifier.weight(1f)) { viewing = photos.first() }
                if (photos.size > 1) PhotoTile(photos.last(), t("Latest"), Modifier.weight(1f)) { viewing = photos.last() }
                else Box(Modifier.weight(1f).aspectRatio(0.75f).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Line, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text(t("Next one in\n2 weeks"), style = T.Small, color = C.Lo)
                }
            }
            if (photos.size > 2) {
                Gap(10.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { p -> PhotoTile(p, null, Modifier.width(64.dp), small = true) { viewing = p } }
                }
            }
        }
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(t("Camera"), {
                val u = Photos.cameraUri(ctx)
                camUri = u
                runCatching { camera.launch(u) }
            }, Modifier.weight(1f), icon = Icons.Rounded.PhotoCamera, height = 46.dp)
            SecondaryButton(t("Gallery"), {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, Modifier.weight(1f), icon = Icons.Rounded.PhotoLibrary, height = 46.dp)
        }
    }

    viewing?.let { p ->
        PhotoViewer(p, onClose = { viewing = null }, onDelete = { Photos.delete(p); viewing = null; version++ })
    }
}

@Composable
private fun PhotoTile(p: Photos.Photo, label: String?, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p.file) { value = Photos.load(p.file, if (small) 200 else 700) }
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(if (small) 10.dp else 16.dp)).background(C.S2).clickable(onClick = onClick),
        ) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        if (label != null) {
            Gap(6.dp)
            Text("$label · ${Fmt.day(p.date)}", style = T.Tiny, color = C.Mid)
        }
    }
}

@Composable
private fun PhotoViewer(p: Photos.Photo, onClose: () -> Unit, onDelete: () -> Unit) {
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p.file) { value = Photos.load(p.file, 1600) }
    var confirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.Close, t("Close"), onClose, bg = C.S1)
                HGap(10.dp)
                Text(Fmt.day(p.date).uppercase(), style = T.H3, color = C.Hi, modifier = Modifier.weight(1f))
                IconBtn(Icons.Rounded.DeleteOutline, t("Delete"), { confirm = true }, tint = C.Red, bg = C.S1)
            }
        }
    }
    if (confirm) {
        ForgeDialog(t("Delete photo?"), { confirm = false }, t("Delete"), { confirm = false; onDelete() }, destructive = true) {
            Text(t("This photo is removed from the app for good."), style = T.Body, color = C.Mid)
        }
    }
}

@Composable
fun MeasurementsPanel(list: List<MeasurementEntity>) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val first = list.firstOrNull()
    val last = list.lastOrNull()

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(t("Measurements · cm"))
                if (last != null) Text(t("Last: %s", Fmt.day(LocalDate.ofEpochDay(last.date))), style = T.Tiny, color = C.Lo)
            }
        }
        Gap(12.dp)
        if (last == null) {
            Text(t("Every two weeks: chest, flexed arm, waist at the navel, mid-thigh."), style = T.Small, color = C.Mid)
        } else {
            Row(Modifier.fillMaxWidth()) {
                MeasureCell(t("Chest"), last.chest, first?.chest, Modifier.weight(1f))
                MeasureCell(t("Arm"), last.arm, first?.arm, Modifier.weight(1f))
                MeasureCell(t("Waist"), last.waist, first?.waist, Modifier.weight(1f))
                MeasureCell(t("Thigh"), last.thigh, first?.thigh, Modifier.weight(1f))
            }
        }
        Gap(14.dp)
        SecondaryButton(t("Log measurements"), { editing = true }, Modifier.fillMaxWidth(), height = 46.dp)
    }

    if (editing) {
        var chest by remember { mutableStateOf(last?.chest?.let { Fmt.kg(it) } ?: "") }
        var arm by remember { mutableStateOf(last?.arm?.let { Fmt.kg(it) } ?: "") }
        var waist by remember { mutableStateOf(last?.waist?.let { Fmt.kg(it) } ?: "") }
        var thigh by remember { mutableStateOf(last?.thigh?.let { Fmt.kg(it) } ?: "") }
        fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
        ForgeDialog(
            title = t("Measurements"),
            onDismiss = { editing = false },
            confirm = t("Save"),
            onConfirm = {
                editing = false
                scope.launch {
                    Graph.repo.logMeasurements(MeasurementEntity(LocalDate.now().toEpochDay(), num(chest), num(arm), num(waist), num(thigh)))
                }
            },
        ) {
            Text(t("Relaxed, same time of day. Arm flexed at its widest point."), style = T.Small, color = C.Mid)
            Gap(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ForgeField(chest, { chest = it }, t("Chest"), Modifier.weight(1f), number = true, suffix = t("cm"))
                ForgeField(arm, { arm = it }, t("Arm"), Modifier.weight(1f), number = true, suffix = t("cm"))
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ForgeField(waist, { waist = it }, t("Waist"), Modifier.weight(1f), number = true, suffix = t("cm"))
                ForgeField(thigh, { thigh = it }, t("Thigh"), Modifier.weight(1f), number = true, suffix = t("cm"))
            }
        }
    }
}

@Composable
private fun MeasureCell(label: String, value: Double?, first: Double?, modifier: Modifier) {
    Column(modifier) {
        Text(value?.let { Fmt.kg(it) } ?: "—", style = T.Num, color = C.Hi)
        Text(label, style = T.Tiny, color = C.Lo)
        if (value != null && first != null && value != first) {
            val d = value - first
            Text(String.format(Locale.US, "%+.1f", d), style = T.Tiny, color = if (label == t("Waist")) C.Amber else C.Volt)
        }
    }
}
