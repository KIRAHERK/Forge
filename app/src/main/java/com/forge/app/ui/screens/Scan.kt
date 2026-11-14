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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.CircularProgressIndicator
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
import com.forge.app.data.OpenFoodFacts
import com.forge.app.data.Portion
import com.forge.app.domain.Basis
import com.forge.app.domain.LabelColumn
import com.forge.app.domain.LabelParser
import com.forge.app.domain.LabelResult
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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
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
    var mode by remember { mutableStateOf(ScanMode.BARCODE) }
    var stage by remember { mutableStateOf<Stage>(Stage.Camera) }
    var session by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var typeCode by remember { mutableStateOf(false) }
    var nameHint by remember { mutableStateOf("") }
    val capture = remember { mutableStateOf<ImageCapture?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val barcodes = remember { BarcodeScanning.getClient(barcodeOptions) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { barcodes.close(); recognizer.close() } }

    fun retry() { stage = Stage.Camera; session++ }

    fun lookup(code: String) {
        stage = Stage.Busy(t("Looking up %s", code))
        scope.launch {
            stage = when (val r = OpenFoodFacts.lookup(code)) {
                is OpenFoodFacts.Lookup.Found -> { onPick(r.product.pick()); return@launch }
                is OpenFoodFacts.Lookup.NoNutrition -> {
                    nameHint = r.name
                    Stage.Message(t("No nutrition data"), t("%s is in Open Food Facts, but without its nutrition table. Scan the label instead.", r.name.ifBlank { t("This product") }), toLabel = true)
                }
                OpenFoodFacts.Lookup.NotFound -> Stage.Message(t("Not in Open Food Facts yet"), t("Barcode %s isn't in the database. Scan the nutrition label and name the food yourself.", code), toLabel = true)
                OpenFoodFacts.Lookup.Offline -> Stage.Message(t("No connection"), t("Couldn't reach Open Food Facts. Check your connection, or scan the label instead: that works offline."), toLabel = true)
            }
        }
    }

    fun read(image: InputImage) {
        stage = Stage.Busy(t("Reading the label"))
        scope.launch {
            val text = runCatching { recognizer.process(image).await() }.getOrNull()
            val lines = text?.toLines()
            if (com.forge.app.BuildConfig.DEBUG) lines?.forEach { android.util.Log.d("ForgeScan", "${it.left},${it.top},${it.right},${it.bottom} | ${it.text}") }
            val result = lines?.let { LabelParser.parse(it) }
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
        LabelReview(review.result, nameHint, onBack = { retry() }, onUse = onPick)
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
                    listOf(t("Barcode"), t("Label")), if (mode == ScanMode.BARCODE) 0 else 1,
                    { mode = if (it == 0) ScanMode.BARCODE else ScanMode.LABEL; retry() },
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
                        if (mode == ScanMode.BARCODE) t("Point at the barcode. It reads by itself.") else t("Fit the nutrition table inside the frame, then take the photo."),
                        style = T.BodyS, color = C.Hi, textAlign = TextAlign.Center,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Gap(16.dp)
                    if (mode == ScanMode.LABEL && granted) {
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).border(3.dp, C.Hi, CircleShape).padding(6.dp).clip(CircleShape).background(C.Volt)
                                .clickable {
                                    val cap = capture.value ?: return@clickable
                                    stage = Stage.Busy(t("Reading the label"))
                                    takePhoto(ctx, cap) { bmp, rotation ->
                                        if (bmp == null) stage = Stage.Message(t("Photo failed"), t("The camera didn't return a photo. Try again."))
                                        else read(InputImage.fromBitmap(bmp, rotation))
                                    }
                                },
                        )
                        Gap(16.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun CameraView(
    mode: ScanMode,
    session: Int,
    torch: Boolean,
    paused: Boolean,
    capture: androidx.compose.runtime.MutableState<ImageCapture?>,
    onFlash: (Boolean) -> Unit,
    onBarcode: (String) -> Unit,
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
            }
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.toTypedArray())
                onFlash(camera?.cameraInfo?.hasFlashUnit() == true)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            camera = null
            capture.value = null
        }
    }

    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    LaunchedEffect(analyzer, paused) { analyzer?.paused = paused }

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

private fun LabelColumn.title(i: Int, servingG: Double?) = when (basis) {
    Basis.PER_100 -> t("Per 100 g")
    Basis.SERVING -> t("Per serving") + (servingG?.let { " · ${show(it)} g" } ?: "")
    Basis.UNKNOWN -> t("Column %s", i + 1)
}

@Composable
private fun LabelReview(result: LabelResult, nameHint: String, onBack: () -> Unit, onUse: (FoodPick) -> Unit) {
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

    val missing = listOfNotNull(
        "calories".takeIf { col.kcal == null }, "protein".takeIf { col.protein == null },
        "fat".takeIf { col.fat == null }, "carbs".takeIf { col.carbs == null },
    )
    val values = listOf(num(kcal), num(p), num(f), num(c))
    val servingG = num(serving)?.takeIf { it > 0 && it < 2000 }
    val ready = name.isNotBlank() && values.all { it != null && it >= 0 }

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
                    result.columns.forEachIndexed { i, cl -> Chip(cl.title(i, result.servingG), column == i, { column = i }) }
                }
                Gap(16.dp)
            }
            Eyebrow(t("These values are"))
            Gap(8.dp)
            Segmented(listOf(t("Per 100 g"), t("Per serving")), if (per100) 0 else 1, { per100 = it == 0 }, colors = listOf(C.Volt, C.Volt))
            if (!per100) {
                Gap(10.dp)
                ForgeField(serving, { serving = it }, t("Serving size (optional)"), number = true, suffix = t("g"))
            }
            Gap(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ForgeField(kcal, { kcal = it; calculated = false }, t("Calories"), Modifier.weight(1f), number = true, suffix = t("kcal"))
                ForgeField(p, { p = it }, t("Protein"), Modifier.weight(1f), number = true, suffix = t("g"))
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ForgeField(f, { f = it }, t("Fat"), Modifier.weight(1f), number = true, suffix = t("g"))
                ForgeField(c, { c = it }, t("Carbs"), Modifier.weight(1f), number = true, suffix = t("g"))
            }
            if (missing.isNotEmpty()) {
                Gap(10.dp)
                Text(if (missing.size == 1) t("Not found on the label: %s. Type it in.", missing.joinToString(", ")) else t("Not found on the label: %s. Type them in.", missing.joinToString(", ")), style = T.Small, color = C.Amber)
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
                        name = name.trim(), per100 = m,
                        portions = listOfNotNull(servingG?.let { Portion(t("1 serving"), it) }, Portion("100 g", 100.0)),
                        source = "label", sourceNote = note, fromOutside = true,
                    )
                    servingG != null -> FoodPick(
                        name = name.trim(), per100 = m * (100.0 / servingG),
                        portions = listOf(Portion(t("1 serving"), servingG), Portion("100 g", 100.0)),
                        source = "label", sourceNote = note, fromOutside = true,
                    )
                    else -> FoodPick(name = name.trim(), base = m, source = "label", sourceNote = note, fromOutside = true)
                }
                onUse(pick)
            }, enabled = ready)
        }
    }
}
