package com.forge.app.ui.screens

import com.forge.app.i18n.t

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forge.app.data.FoodPick
import com.forge.app.data.BarcodeLookup
import com.forge.app.data.BarcodeMemory
import com.forge.app.data.Measure
import com.forge.app.data.OpenFoodFacts
import com.forge.app.data.SavedBarcode
import com.forge.app.data.Portion
import com.forge.app.domain.Basis
import com.forge.app.domain.LabelColumn
import com.forge.app.domain.LabelParser
import com.forge.app.domain.LabelResult
import com.forge.app.domain.LabelVotes
import com.forge.app.domain.Macros
import com.forge.app.domain.OcrLine
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.Segmented
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private enum class ScanMode { BARCODE, LABEL }

private sealed interface Stage {
    data object Camera : Stage
    data class Busy(val text: String) : Stage
    data class Message(val title: String, val body: String, val toLabel: Boolean = false) : Stage
    data class Review(val result: LabelResult) : Stage
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

private fun MlText.toLines(): List<OcrLine> = textBlocks.flatMap { b ->
    b.lines.mapNotNull { l -> l.boundingBox?.let { r -> OcrLine(l.text, r.left, r.top, r.right, r.bottom) } }
}

private val barcodeOptions = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
    .build()

/** Full-screen camera for a product barcode or a nutrition table. Hands back a food to log. */
@Composable
fun ScanDialog(onDismiss: () -> Unit, onPick: (FoodPick) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ScanScreen(onDismiss, onPick)
    }
}

@Composable
private fun ScanScreen(onDismiss: () -> Unit, onPick: (FoodPick) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Opens on the nutrition label: most Syrian products aren't in the barcode database yet.
    var mode by remember { mutableStateOf(ScanMode.LABEL) }
    var stage by remember { mutableStateOf<Stage>(Stage.Camera) }
    var session by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var typeCode by remember { mutableStateOf(false) }
    var nameHint by remember { mutableStateOf("") }
    // A barcode no database knew: the label scanned next is remembered under it.
    var pendingCode by remember { mutableStateOf<String?>(null) }
    val capture = remember { mutableStateOf<ImageCapture?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val barcodes = remember { BarcodeScanning.getClient(barcodeOptions) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { barcodes.close(); recognizer.close() } }

    // Live reading of the label: every camera frame votes, values lock once read consistently.
    var votes by remember { mutableStateOf(LabelVotes()) }
    var tick by remember { mutableIntStateOf(0) }

    fun retry() { stage = Stage.Camera; session++; votes = LabelVotes(); tick++ }

    fun onFrame(lines: List<OcrLine>) {
        if (stage != Stage.Camera || mode != ScanMode.LABEL) return
        val current = votes
        scope.launch {
            val r = withContext(Dispatchers.Default) { LabelParser.parse(lines) }
            if (current !== votes || stage != Stage.Camera) return@launch
            current.add(r)
            tick++
            if (current.complete) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                stage = Stage.Review(current.result())
            }
        }
    }

    fun lookup(code: String) {
        stage = Stage.Busy(t("Looking up %s", code))
        pendingCode = null
        scope.launch {
            stage = when (val r = BarcodeLookup.find(ctx, code)) {
                is BarcodeLookup.Outcome.Found -> { onPick(r.pick); return@launch }
                is BarcodeLookup.Outcome.NoNutrition -> {
                    nameHint = r.name
                    pendingCode = code
                    Stage.Message(t("No nutrition data"), t("%s is in Open Food Facts, but without its nutrition table. Scan the label once and Forge remembers it.", r.name.ifBlank { t("This product") }), toLabel = true)
                }
                BarcodeLookup.Outcome.NotFound -> {
                    pendingCode = code
                    Stage.Message(t("Product not found"), t("Barcode %s isn't in Open Food Facts, USDA or the Forge product list. Scan its nutrition label once and Forge remembers it next time.", code), toLabel = true)
                }
                BarcodeLookup.Outcome.Offline -> Stage.Message(t("No connection"), t("Couldn't reach Open Food Facts. Check your connection, or scan the label instead: that works offline."), toLabel = true)
            }
        }
    }

    /** A still photo: read once. From the camera, it's added to what the live reading has seen. */
    fun read(image: InputImage, live: LabelVotes? = null) {
        stage = Stage.Busy(t("Reading the label"))
        scope.launch {
            val text = runCatching { recognizer.process(image).await() }.getOrNull()
            val lines = text?.toLines()
            if (com.forge.app.BuildConfig.DEBUG) lines?.forEach { android.util.Log.d("ForgeScan", "${it.left},${it.top},${it.right},${it.bottom} | ${it.text}") }
            val parsed = lines?.let { withContext(Dispatchers.Default) { LabelParser.parse(it) } }
            val result = if (live != null) { parsed?.let { live.add(it, weight = 2) }; live.result().takeIf { !it.empty } ?: parsed } else parsed
            stage = if (result == null || result.empty) {
                Stage.Message(
                    t("Couldn't read a nutrition table"),
                    t("Fill the frame with the table, keep the phone steady and make sure it's well lit. Arabic-only labels can't be read yet; use the English side if there is one."),
                )
            } else Stage.Review(result)
        }
    }

    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val image = runCatching { InputImage.fromFilePath(ctx, uri) }.getOrNull() ?: return@rememberLauncherForActivityResult
        if (mode == ScanMode.LABEL) read(image) else {
            stage = Stage.Busy(t("Looking for a barcode"))
            scope.launch {
                val code = runCatching { barcodes.process(image).await() }.getOrNull()
                    ?.firstNotNullOfOrNull { b -> b.rawValue?.takeIf { OpenFoodFacts.validBarcode(it) } }
                if (code != null) lookup(code) else stage = Stage.Message(t("No barcode found"), t("Try a sharper photo, or scan the nutrition label instead."), toLabel = true)
            }
        }
    }

    val review = stage as? Stage.Review
    if (review != null) {
        LabelReview(review.result, nameHint, remembered = pendingCode != null, onBack = { retry() }, onUse = { pick ->
            val code = pendingCode
            val m = pick.per100 ?: pick.base
            if (code != null && m != null) BarcodeMemory.put(ctx, code, SavedBarcode.of(pick, m))
            onPick(if (code != null) pick.copy(barcode = code, source = "barcode:$code") else pick)
        })
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraView(
                mode = mode,
                session = session,
                torch = torch,
                paused = stage != Stage.Camera,
                capture = capture,
                onFlash = { hasFlash = it },
                onBarcode = { code ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lookup(code)
                },
                recognizer = recognizer,
                onText = ::onFrame,
            )
            ScanFrame(mode)
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t("CAMERA IS OFF"), style = T.H2, color = C.Hi)
                Gap(8.dp)
                Text(
                    t("Forge needs the camera to read barcodes and labels. Photos stay on your phone. You can also pick a photo you already took."),
                    style = T.Body, color = C.Mid, textAlign = TextAlign.Center,
                )
                Gap(16.dp)
                PrimaryButton(if (asked) t("Open settings") else t("Allow camera"), {
                    if (asked) ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    else permission.launch(Manifest.permission.CAMERA)
                }, height = 50.dp)
            }
        }

        // Top bar
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.Close, t("Close"), onDismiss, tint = C.Hi, bg = Color.Black.copy(alpha = 0.45f))
                Spacer(Modifier.weight(1f))
                Segmented(
                    listOf(t("Label"), t("Barcode")), if (mode == ScanMode.LABEL) 0 else 1,
                    { mode = if (it == 0) ScanMode.LABEL else ScanMode.BARCODE; retry() },
                    colors = listOf(C.Volt, C.Volt),
                )
                Spacer(Modifier.weight(1f))
                if (granted && hasFlash) {
                    IconBtn(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, t("Torch"), { torch = !torch }, tint = if (torch) C.Volt else C.Hi, bg = Color.Black.copy(alpha = 0.45f))
                } else Spacer(Modifier.size(44.dp))
            }
        }

        // Bottom controls
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = stage) {
                is Stage.Message -> MessageCard(s, onRetry = { retry() }, onLabel = { mode = ScanMode.LABEL; retry() })
                is Stage.Busy -> Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(C.S1).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = C.Volt, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    HGap(12.dp)
                    Text(s.text, style = T.BodyS, color = C.Hi)
                }
                else -> {
                    Text(
                        if (mode == ScanMode.BARCODE) t("Point at the barcode. It reads by itself. Syrian products aren't supported yet: scan the nutrition table instead.") else t("Hold the nutrition table inside the frame. Each value locks in once it's read clearly; move closer or tilt if one won't."),
                        style = T.BodyS, color = C.Hi, textAlign = TextAlign.Center,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Gap(12.dp)
                    if (mode == ScanMode.LABEL && granted) {
                        tick.let { LiveValues(votes) }
                        Gap(14.dp)
                    }
                    if (mode == ScanMode.LABEL && granted) {
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).border(3.dp, C.Hi, CircleShape).padding(6.dp).clip(CircleShape).background(C.Volt)
                                .clickable {
                                    val cap = capture.value ?: return@clickable
                                    stage = Stage.Busy(t("Reading the label"))
                                    takePhoto(ctx, cap) { bmp, rotation ->
                                        if (bmp == null) stage = Stage.Message(t("Photo failed"), t("The camera didn't return a photo. Try again."))
                                        else read(InputImage.fromBitmap(bmp, rotation), votes)
                                    }
                                },
                        )
                        Gap(16.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (mode == ScanMode.LABEL && votes.readable > 0) {
                            SecondaryButton(t("Use what's read"), { stage = Stage.Review(votes.result()) }, height = 44.dp, border = C.Volt.copy(alpha = 0.6f), color = C.Volt)
                        }
                        SecondaryButton(t("From photo"), { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, height = 44.dp, border = C.Hi.copy(alpha = 0.4f))
                        if (mode == ScanMode.BARCODE) SecondaryButton(t("Type code"), { typeCode = true }, height = 44.dp, border = C.Hi.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    if (typeCode) {
        var code by remember { mutableStateOf("") }
        ForgeDialog(
            title = t("Barcode"),
            onDismiss = { typeCode = false },
            confirm = t("Look up"),
            onConfirm = {
                val c = code.filter { it.isDigit() }
                if (OpenFoodFacts.validBarcode(c)) { typeCode = false; lookup(c) }
            },
        ) {
            Text(t("The digits under the bars, 8 to 13 of them."), style = T.Small, color = C.Mid)
            Gap(12.dp)
            ForgeField(code, { code = it }, t("Barcode"), number = true)
        }
    }
}

private fun fieldName(f: LabelVotes.Field) = when (f) {
    LabelVotes.Field.KCAL -> t("Calories")
    LabelVotes.Field.PROTEIN -> t("Protein")
    LabelVotes.Field.FAT -> t("Fat")
    LabelVotes.Field.CARBS -> t("Carbs")
}

/** The four values while the camera reads: a value locks, with a tick, once it's been read the same way a few times. */
@Composable
private fun LiveValues(votes: LabelVotes) {
    val basis = votes.primary
    val liquid = votes.readable > 0 && votes.result().liquid
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(10.dp)) {
        Text(
            when {
                votes.readable == 0 -> t("Looking for a nutrition table…")
                basis == Basis.PER_100 -> if (liquid) t("Per 100 ml") else t("Per 100 g")
                basis == Basis.SERVING -> t("Per serving")
                else -> t("Reading…")
            },
            style = T.Tiny, color = C.Mid, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelVotes.Field.entries.forEach { f ->
                val locked = votes.locked(basis, f)
                val seen = votes.seen(basis, f)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (locked != null) C.Volt.copy(alpha = 0.16f) else C.S1)
                        .border(1.dp, if (locked != null) C.Volt else C.Line, RoundedCornerShape(10.dp))
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(fieldName(f), style = T.Tiny, color = C.Mid, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (locked != null) {
                            Icon(Icons.Rounded.Check, null, tint = C.Volt, modifier = Modifier.size(13.dp))
                            HGap(2.dp)
                        }
                        Text(show(locked ?: seen).ifEmpty { "—" }, style = T.BodyS, color = if (locked != null) C.Hi else C.Lo, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Reads one value again: the camera looks for that row ("Protein  5.6 g") until the same number
 * has been read a few times. When the row has more than one column, [pick] chooses.
 */
@Composable
private fun RescanCamera(field: LabelVotes.Field, pick: (List<Double>) -> Double, onValue: (Double) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }
    val votes = remember { LabelVotes(needed = 3) }
    var tick by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    val capture = remember { mutableStateOf<ImageCapture?>(null) }
    androidx.activity.compose.BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            mode = ScanMode.LABEL, session = 0, torch = torch, paused = done, capture = capture,
            onFlash = { hasFlash = it }, onBarcode = {}, recognizer = recognizer,
            onText = { lines ->
                scope.launch {
                    val r = withContext(Dispatchers.Default) { LabelParser.parse(lines) }
                    if (done) return@launch
                    votes.add(r)
                    tick++
                    val found = votes.candidates(field)
                    if (found.isNotEmpty()) {
                        done = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onValue(pick(found))
                    }
                }
            },
        )
        ScanFrame(ScanMode.BARCODE)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, t("Cancel"), onCancel, tint = C.Hi, bg = Color.Black.copy(alpha = 0.45f))
            Spacer(Modifier.weight(1f))
            if (hasFlash) IconBtn(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, t("Torch"), { torch = !torch }, tint = if (torch) C.Volt else C.Hi, bg = Color.Black.copy(alpha = 0.45f))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                t("Point at the %s row, with its name and number inside the frame.", fieldName(field).lowercase()),
                style = T.BodyS, color = C.Hi, textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Gap(10.dp)
            tick.let {
                val seen = Basis.entries.firstNotNullOfOrNull { b -> votes.seen(b, field) }
                Text(seen?.let { t("Reading: %s", show(it)) } ?: t("Looking…"), style = T.Small, color = C.Mid)
            }
        }
    }
}

@Composable
private fun MessageCard(s: Stage.Message, onRetry: () -> Unit, onLabel: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.S1).border(1.dp, C.Line, RoundedCornerShape(20.dp)).padding(18.dp)) {
        Text(s.title.uppercase(), style = T.H3, color = C.Hi)
        Gap(6.dp)
        Text(s.body, style = T.Small, color = C.Mid)
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.toLabel) PrimaryButton(t("Scan the label"), onLabel, Modifier.weight(1f), height = 46.dp)
            SecondaryButton(t("Try again"), onRetry, if (s.toLabel) Modifier else Modifier.weight(1f), height = 46.dp)
        }
    }
}

/** Dims everything outside the scan window and marks its corners. */
@Composable
private fun ScanFrame(mode: ScanMode) {
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.82f
        val h = if (mode == ScanMode.BARCODE) w * 0.55f else (w * 1.35f).coerceAtMost(size.height * 0.56f)
        val left = (size.width - w) / 2
        val top = size.height * 0.44f - h / 2
        val r = 24.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.5f))
        drawRoundRect(Color.Transparent, Offset(left, top), GSize(w, h), CornerRadius(r), blendMode = BlendMode.Clear)
        val len = 34.dp.toPx()
        val path = Path()
        // One bracket per corner: down the side, round the corner, along the edge.
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            path.moveTo(x, y + sy * len)
            path.lineTo(x, y + sy * r)
            path.quadraticTo(x, y, x + sx * r, y)
            path.lineTo(x + sx * len, y)
        }
        corner(left, top, 1f, 1f)
        corner(left + w, top, -1f, 1f)
        corner(left + w, top + h, -1f, -1f)
        corner(left, top + h, 1f, -1f)
        drawPath(path, C.Volt, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(private val scanner: BarcodeScanner, private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    @Volatile var paused = false
    @Volatile private var done = false

    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done || paused) { proxy.close(); return }
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                val code = codes.firstNotNullOfOrNull { b -> b.rawValue?.takeIf { OpenFoodFacts.validBarcode(it) } }
                if (code != null && !done && !paused) { done = true; onCode(code) }
            }
            .addOnCompleteListener { proxy.close() }
    }
}

/** Reads the text in camera frames, a few times a second, for the live label reading. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class TextAnalyzer(private val recognizer: TextRecognizer, private val onLines: (List<OcrLine>) -> Unit) : ImageAnalysis.Analyzer {
    @Volatile var paused = false
    private var last = 0L

    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        val now = android.os.SystemClock.elapsedRealtime()
        if (media == null || paused || now - last < 250) { proxy.close(); return }
        last = now
        recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { if (!paused) onLines(it.toLines()) }
            .addOnCompleteListener { proxy.close() }
    }
}

@Composable
private fun CameraView(
    mode: ScanMode,
    session: Int,
    torch: Boolean,
    paused: Boolean,
    capture: androidx.compose.runtime.MutableState<ImageCapture?>,
    onFlash: (Boolean) -> Unit,
    onBarcode: (String) -> Unit,
    recognizer: TextRecognizer? = null,
    onText: ((List<OcrLine>) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(barcodeOptions) }
    val latest by rememberUpdatedState(onBarcode)
    val latestText by rememberUpdatedState(onText)
    var textAnalyzer by remember { mutableStateOf<TextAnalyzer?>(null) }
    val wantTorch by rememberUpdatedState(torch)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var analyzer by remember { mutableStateOf<BarcodeAnalyzer?>(null) }

    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

    DisposableEffect(mode, session, owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val cases = mutableListOf<UseCase>(preview)
            if (mode == ScanMode.BARCODE) {
                val a = BarcodeAnalyzer(scanner) { code -> view.post { latest(code) } }
                analyzer = a
                cases += ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(executor, a) }
                capture.value = null
            } else {
                val c = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                capture.value = c
                cases += c
                if (recognizer != null) {
                    // Small print needs a sharp frame: full HD where the camera can.
                    val a = TextAnalyzer(recognizer) { lines -> view.post { latestText?.invoke(lines) } }
                    textAnalyzer = a
                    cases += ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                                .build(),
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also { it.setAnalyzer(executor, a) }
                }
            }
            runCatching {
                p.unbindAll()
                // A camera that can't stream frames and take photos at once still gets the photo button.
                val cam = runCatching { p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.toTypedArray()) }
                    .getOrElse {
                        if (mode != ScanMode.LABEL || cases.size < 3) throw it
                        textAnalyzer = null
                        p.unbindAll()
                        p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.filter { c -> c !is ImageAnalysis }.toTypedArray())
                    }
                camera = cam
                onFlash(cam.cameraInfo.hasFlashUnit())
                // Rebinding (switching Label/Barcode) turns the light off; a torch request made before the
                // camera is open is dropped. Re-apply whenever the camera reaches OPEN.
                cam.cameraInfo.cameraState.removeObservers(owner)
                cam.cameraInfo.cameraState.observe(owner) { st ->
                    if (st.type == androidx.camera.core.CameraState.Type.OPEN && cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(wantTorch)
                    }
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            camera?.cameraInfo?.cameraState?.removeObservers(owner)
            provider?.unbindAll()
            camera = null
            capture.value = null
        }
    }

    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    LaunchedEffect(analyzer, paused) { analyzer?.paused = paused }
    LaunchedEffect(textAnalyzer, paused) { textAnalyzer?.paused = paused }

    AndroidView({ view }, Modifier.fillMaxSize())
}

private fun takePhoto(ctx: Context, capture: ImageCapture, done: (Bitmap?, Int) -> Unit) {
    capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val rotation = image.imageInfo.rotationDegrees
            val bmp = runCatching { image.toBitmap() }.getOrNull()
            image.close()
            done(bmp, rotation)
        }

        override fun onError(exception: ImageCaptureException) = done(null, 0)
    })
}

// ------------------------------------------------------------------ review

private fun num(s: String) = s.replace(',', '.').trim().toDoubleOrNull()
private fun show(v: Double?) = when {
    v == null -> ""
    v % 1.0 == 0.0 -> v.toInt().toString()
    else -> String.format(java.util.Locale.US, "%.1f", v)
}

private fun LabelColumn.title(i: Int, servingG: Double?, unit: Measure) = when (basis) {
    Basis.PER_100 -> if (unit == Measure.ML) t("Per 100 ml") else t("Per 100 g")
    Basis.SERVING -> t("Per serving") + (servingG?.let { " · ${show(it)} ${t(unit.sym)}" } ?: "")
    Basis.UNKNOWN -> t("Column %s", i + 1)
}

@Composable
private fun LabelReview(result: LabelResult, nameHint: String, remembered: Boolean, onBack: () -> Unit, onUse: (FoodPick) -> Unit) {
    var column by remember { mutableIntStateOf(0) }
    val col = result.columns.getOrElse(column) { result.columns.first() }
    var name by remember { mutableStateOf(nameHint) }
    var per100 by remember(column) { mutableStateOf(col.basis != Basis.SERVING) }
    var serving by remember { mutableStateOf(show(result.servingG)) }
    var kcal by remember(column) { mutableStateOf(show(col.kcal?.let { Math.round(it).toDouble() })) }
    var p by remember(column) { mutableStateOf(show(col.protein)) }
    var f by remember(column) { mutableStateOf(show(col.fat)) }
    var c by remember(column) { mutableStateOf(show(col.carbs)) }
    var calculated by remember(column) { mutableStateOf(false) }
    var unit by remember { mutableStateOf(if (result.liquid) Measure.ML else Measure.G) }

    val missing = listOfNotNull(
        "calories".takeIf { col.kcal == null }, "protein".takeIf { col.protein == null },
        "fat".takeIf { col.fat == null }, "carbs".takeIf { col.carbs == null },
    )
    var rescan by remember { mutableStateOf<LabelVotes.Field?>(null) }
    val values = listOf(num(kcal), num(p), num(f), num(c))
    val servingG = num(serving)?.takeIf { it > 0 && it < 2000 }
    val ready = name.isNotBlank() && values.all { it != null && it >= 0 }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, t("Retake"), onBack)
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text(t("CHECK THE LABEL"), style = T.H3, color = C.Hi)
                Text(t("Compare each number with the package"), style = T.Small, color = C.Mid)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Gap(8.dp)
            ForgeField(name, { name = it }, t("What is this food?"))
            Gap(16.dp)
            if (result.columns.size > 1) {
                Eyebrow(t("The label has %s columns", result.columns.size))
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.columns.forEachIndexed { i, cl -> Chip(cl.title(i, result.servingG, unit), column == i, { column = i }) }
                }
                Gap(16.dp)
            }
            Eyebrow(t("Measured in"))
            Gap(8.dp)
            Segmented(listOf(t("Grams · food"), t("ml · drink")), if (unit == Measure.ML) 1 else 0, { unit = if (it == 1) Measure.ML else Measure.G }, colors = listOf(C.Volt, C.Volt))
            Gap(16.dp)
            Eyebrow(t("These values are"))
            Gap(8.dp)
            Segmented(listOf(if (unit == Measure.ML) t("Per 100 ml") else t("Per 100 g"), t("Per serving")), if (per100) 0 else 1, { per100 = it == 0 }, colors = listOf(C.Volt, C.Volt))
            if (!per100) {
                Gap(10.dp)
                ForgeField(serving, { serving = it }, t("Serving size (optional)"), number = true, suffix = t(unit.sym))
            }
            Gap(16.dp)
            @Composable
            fun RescanButton(field: LabelVotes.Field) =
                IconBtn(Icons.Rounded.PhotoCamera, t("Scan %s again", fieldName(field).lowercase()), { rescan = field }, tint = C.Volt, size = 36.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ForgeField(kcal, { kcal = it; calculated = false }, t("Calories"), Modifier.weight(1f), number = true, suffix = t("kcal"))
                RescanButton(LabelVotes.Field.KCAL)
                ForgeField(p, { p = it }, t("Protein"), Modifier.weight(1f), number = true, suffix = t("g"))
                RescanButton(LabelVotes.Field.PROTEIN)
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ForgeField(f, { f = it }, t("Fat"), Modifier.weight(1f), number = true, suffix = t("g"))
                RescanButton(LabelVotes.Field.FAT)
                ForgeField(c, { c = it }, t("Carbs"), Modifier.weight(1f), number = true, suffix = t("g"))
                RescanButton(LabelVotes.Field.CARBS)
            }
            if (missing.isNotEmpty()) {
                Gap(10.dp)
                Text(if (missing.size == 1) t("Not found on the label: %s. Type it in, or tap the camera next to it.", missing.joinToString(", ")) else t("Not found on the label: %s. Type them in, or tap the camera next to each.", missing.joinToString(", ")), style = T.Small, color = C.Amber)
            }
            // The cross-check: about 4 kcal per gram of protein and carbs, 9 per gram of fat.
            val (vk, vp, vf, vc) = values
            if (vk != null && vp != null && vf != null && vc != null && vk > 0) {
                Gap(10.dp)
                val ok = kotlin.math.abs(4 * vp + 4 * vc + 9 * vf - vk) / maxOf(vk, 40.0) < 0.2
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ok) { Icon(Icons.Rounded.Check, null, tint = C.Volt, modifier = Modifier.size(16.dp)); HGap(6.dp) }
                    Text(
                        if (ok) t("Calories match protein, fat and carbs.")
                        else t("These don't add up: protein and carbs give about 4 kcal per gram, fat 9. Check each number against the pack."),
                        style = T.Small, color = if (ok) C.Volt else C.Amber,
                    )
                }
            }
            if (col.fixes.isNotEmpty()) {
                Gap(6.dp)
                Text(t("Corrected a likely misread: %s. Check it against the pack.", col.fixes.joinToString(", ") { fix ->
                    fix.replace("protein", fieldName(LabelVotes.Field.PROTEIN)).replace("fat", fieldName(LabelVotes.Field.FAT)).replace("carbs", fieldName(LabelVotes.Field.CARBS))
                }), style = T.Small, color = C.Amber)
            }
            if (col.kcalFromKj && col.kcal != null) {
                Gap(6.dp)
                Text(t("The label gave energy in kJ only; converted to kcal."), style = T.Small, color = C.Mid)
            }
            if (num(kcal) == null && num(p) != null && num(f) != null && num(c) != null) {
                Gap(10.dp)
                SecondaryButton(t("Work out calories from protein, fat, carbs"), {
                    kcal = (4 * num(p)!! + 9 * num(f)!! + 4 * num(c)!!).roundToInt().toString()
                    calculated = true
                }, height = 44.dp, color = C.Volt, border = C.Volt.copy(alpha = 0.5f))
            }
            Gap(16.dp)
            Text(t("Read on your phone; nothing is uploaded. OCR can misread a digit, so check before adding."), style = T.Tiny, color = C.Lo)
            if (remembered) {
                Gap(6.dp)
                Text(t("Forge will remember these values for this barcode, so the next scan finds it."), style = T.Tiny, color = C.Volt)
            }
            Gap(24.dp)
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PrimaryButton(t("Use these values"), {
                val (k, pp, ff, cc) = values.map { it!! }
                val m = Macros(k.roundToInt(), pp, ff, cc)
                val note = buildList {
                    add(t("Scanned from the label"))
                    if (col.kcalFromKj && !calculated) add(t("calories converted from kJ"))
                    if (calculated) add(t("calories worked out from macros"))
                }.joinToString(" · ")
                val pick = when {
                    per100 -> FoodPick(
                        name = name.trim(), per100 = m, unit = unit,
                        portions = listOfNotNull(servingG?.let { Portion(t("1 serving"), it) }, Portion("100 ${unit.sym}", 100.0)),
                        source = "label", sourceNote = note, fromOutside = true,
                    )
                    servingG != null -> FoodPick(
                        name = name.trim(), per100 = m * (100.0 / servingG), unit = unit,
                        portions = listOf(Portion(t("1 serving"), servingG), Portion("100 ${unit.sym}", 100.0)),
                        source = "label", sourceNote = note, fromOutside = true,
                    )
                    else -> FoodPick(name = name.trim(), base = m, unit = unit, source = "label", sourceNote = note, fromOutside = true)
                }
                onUse(pick)
            }, enabled = ready)
        }
    }
    rescan?.let { field ->
        RescanCamera(
            field,
            pick = { found ->
                // Several columns on that row: take the number that makes this column add up.
                if (found.size == 1) found.first() else found.minByOrNull { v ->
                    val k = if (field == LabelVotes.Field.KCAL) v else num(kcal)
                    val pp = if (field == LabelVotes.Field.PROTEIN) v else num(p)
                    val ff = if (field == LabelVotes.Field.FAT) v else num(f)
                    val cc = if (field == LabelVotes.Field.CARBS) v else num(c)
                    if (k == null || pp == null || ff == null || cc == null || k <= 0) 0.0 else kotlin.math.abs(4 * pp + 4 * cc + 9 * ff - k) / k
                }!!
            },
            onValue = { v ->
                when (field) {
                    LabelVotes.Field.KCAL -> { kcal = show(Math.round(v).toDouble()); calculated = false }
                    LabelVotes.Field.PROTEIN -> p = show(v)
                    LabelVotes.Field.FAT -> f = show(v)
                    LabelVotes.Field.CARBS -> c = show(v)
                }
                rescan = null
            },
            onCancel = { rescan = null },
        )
    }
    }
}
