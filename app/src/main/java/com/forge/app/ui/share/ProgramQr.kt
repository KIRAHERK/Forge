package com.forge.app.ui.share

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forge.app.data.ProgramCode
import com.forge.app.data.UserProgram
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.LocalDate
import java.util.concurrent.Executors

/** Black-on-white QR for [text], one pixel per module, scaled up without smoothing when drawn. */
fun qrBitmap(text: String): ImageBitmap {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
    )
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        bmp.setPixel(x, y, if (m[x, y]) 0xFF0B0B0D.toInt() else 0xFFFFFFFF.toInt())
    }
    return bmp.asImageBitmap()
}

// ------------------------------------------------------------------ share

/** Shows the program as a QR card; Share sends it as an image (good for WhatsApp groups). */
@Composable
fun ShareProgramDialog(program: UserProgram, onDismiss: () -> Unit) {
    val code = remember(program) { ProgramCode.encode(program) }
    val qr = remember(code) { qrBitmap(code) }
    ShareDialog("forge-program", onDismiss) { ProgramQrCard(program, qr) }
}

@Composable
private fun ProgramQrCard(program: UserProgram, qr: ImageBitmap) {
    CardFrame(
        date = LocalDate.now(),
        eyebrow = "Program · ${program.daysPerWeek} days a week",
        title = program.name,
        badge = null,
        line = null,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                program.days.take(6).forEach { d ->
                    Text(d.name, style = T.Small, color = C.Hi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${d.exercises.size} exercises · ${d.totalSets} sets", style = T.Tiny, color = C.Mid)
                    Gap(6.dp)
                }
            }
            Box(Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(C.Hi).padding(6.dp)) {
                Image(qr, "QR code of the program", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
        }
        Gap(10.dp)
        Text("Scan in Forge: Train · Programs · Scan a program", style = T.Small, color = C.Volt, maxLines = 2)
    }
}

// ------------------------------------------------------------------ scan

private val qrOptions = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()

/**
 * Camera scanner for a program QR, with a fallback to pick a photo of one (a screenshot
 * or an image sent on WhatsApp). Calls [onCode] with the decoded program.
 */
@Composable
fun ScanProgramDialog(onDismiss: () -> Unit, onCode: (ProgramCode.Shared) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var message by remember { mutableStateOf<String?>(null) }
    val latest by rememberUpdatedState(onCode)

    fun handle(raw: String?): Boolean {
        val shared = raw?.let { ProgramCode.decode(it) }
        if (shared != null) { latest(shared); return true }
        message = if (raw == null) "No QR code found in that picture." else "That QR code isn't a Forge program."
        return false
    }

    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val image = runCatching { InputImage.fromFilePath(ctx, uri) }.getOrNull() ?: run { message = "Couldn't open that picture."; return@rememberLauncherForActivityResult }
        val scanner = BarcodeScanning.getClient(qrOptions)
        scanner.process(image)
            .addOnSuccessListener { codes -> handle(codes.firstNotNullOfOrNull { it.rawValue }) }
            .addOnFailureListener { message = "Couldn't read that picture." }
            .addOnCompleteListener { scanner.close() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            if (granted) QrCamera { raw -> handle(raw) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, "Close", onDismiss, bg = C.S1)
                    Text("SCAN A PROGRAM", style = T.H3, color = C.Hi, modifier = Modifier.weight(1f).padding(start = 12.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(260.dp).border(3.dp, C.Volt, RoundedCornerShape(24.dp)))
                    if (!granted) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("CAMERA IS OFF", style = T.H2, color = C.Hi)
                            Gap(6.dp)
                            Text("Allow the camera to scan, or pick a photo of the code.", style = T.Body, color = C.Mid, textAlign = TextAlign.Center)
                            Gap(12.dp)
                            TextAction("Allow camera", { permission.launch(Manifest.permission.CAMERA) })
                        }
                    }
                }
                message?.let {
                    Text(it, style = T.BodyS, color = C.Amber, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Gap(10.dp)
                }
                Text("Point at the QR code a coach or friend shared, or pick a screenshot of it.", style = T.Small, color = C.Mid, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Gap(12.dp)
                SecondaryButton("From a photo", {
                    message = null
                    photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PhotoLibrary, height = 50.dp)
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class QrAnalyzer(private val onRaw: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(qrOptions)
    @Volatile private var done = false
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done) { proxy.close(); return }
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                val raw = codes.firstNotNullOfOrNull { it.rawValue }
                // Only stop on a Forge program, so a random QR in view doesn't end the scan.
                if (raw != null && !done && raw.startsWith(ProgramCode.PREFIX)) { done = true; onRaw(raw) }
            }
            .addOnCompleteListener { proxy.close() }
    }
    fun close() = scanner.close()
}

@Composable
private fun QrCamera(onRaw: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val latest by rememberUpdatedState(onRaw)
    DisposableEffect(owner) {
        val analyzer = QrAnalyzer { raw -> view.post { latest(raw) } }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { it.setAnalyzer(executor, analyzer) }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}

// ------------------------------------------------------------------ import preview

@Composable
fun ImportProgramDialog(shared: ProgramCode.Shared, onDismiss: () -> Unit, onAdd: (use: Boolean) -> Unit) {
    ForgeDialog(
        title = shared.n,
        onDismiss = onDismiss,
        confirm = "Add and use",
        onConfirm = { onAdd(true) },
    ) {
        Eyebrow("${ProgramCode.daysPerWeek(shared)} days a week · ${shared.d.size} workouts")
        Gap(10.dp)
        shared.d.forEach { d ->
            Text(d.n, style = T.BodyS, color = C.Hi)
            Text(ProgramCode.names(shared, d).joinToString(", "), style = T.Small, color = C.Mid)
            Gap(6.dp)
        }
        if (shared.c.isNotEmpty()) {
            Text("Adds ${shared.c.size} exercise${if (shared.c.size == 1) "" else "s"} to your own: ${shared.c.joinToString(", ") { it.n }}.", style = T.Small, color = C.Lo)
            Gap(6.dp)
        }
        TextAction("Add without using it", { onAdd(false) }, color = C.Mid, style = T.Small)
    }
}

