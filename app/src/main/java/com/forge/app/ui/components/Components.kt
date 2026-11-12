package com.forge.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.delay
import java.time.LocalDateTime

// ------------------------------------------------------------------ basics

@Composable
fun rememberNow(periodMs: Long = 30_000): State<LocalDateTime> =
    produceState(LocalDateTime.now(), periodMs) {
        while (true) {
            value = LocalDateTime.now()
            delay(periodMs)
        }
    }

@Composable
fun rememberHaptic(): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { strong: Boolean ->
            val c = when {
                strong && Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
                strong -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(c)
        }
    }
}

fun Modifier.pressScale(onClick: () -> Unit, enabled: Boolean = true, scaleTo: Float = 0.97f): Modifier = this.then(
    Modifier.composedPress(onClick, enabled, scaleTo)
)

@Composable
private fun Modifier.composedPressImpl(onClick: () -> Unit, enabled: Boolean, scaleTo: Float): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

private fun Modifier.composedPress(onClick: () -> Unit, enabled: Boolean, scaleTo: Float): Modifier =
    this.composed { composedPressImpl(onClick, enabled, scaleTo) }

/** Blank frame shown for the few milliseconds before a screen's data arrives, so nothing half-loaded flashes. */
@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().fillMaxHeight().background(C.Bg))
}

@Composable
fun Eyebrow(text: String, color: Color = C.Mid, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.Eyebrow, color = color, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(C.Line))
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

// ------------------------------------------------------------------ surfaces

/**
 * The app's background light: a dark olive wash at the top and a soft volt glow in
 * the top-right corner, the same look as the share cards.
 */
@Composable
fun Glow(modifier: Modifier = Modifier, strength: Float = 1f) {
    Canvas(modifier) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.BgTop, C.Bg), endY = size.height * 0.45f))
        val r = size.width * 1.0f
        val c = Offset(size.width * 0.98f, -size.width * 0.05f)
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(C.Volt.copy(alpha = 0.13f * strength), Color.Transparent), center = c, radius = r),
            radius = r, center = c,
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = C.S1,
    border: Color = C.Line,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    /** Hero panels get a faint volt-tinted gradient, like the share cards. */
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (accent) Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.AccentTop, color), start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY)))
            else Modifier.background(color),
        )
        .border(1.dp, if (accent && border == C.Line) C.Volt.copy(alpha = 0.22f) else border, shape)
    Column((if (onClick != null) base.pressScale(onClick, scaleTo = 0.985f) else base).padding(padding), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = T.H3, color = C.Hi, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = T.Small, color = C.Volt, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

@Composable
fun ScreenHeader(eyebrow: String?, title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Gap(4.dp)
            }
            Text(title.uppercase(), style = T.H1, color = C.Hi)
        }
        trailing()
    }
}

@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(title.uppercase(), style = T.H3, color = C.Hi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = T.Small, color = C.Mid, maxLines = 1)
        }
        trailing()
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = C.Hi, bg: Color = Color.Transparent, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------------ buttons

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = C.Volt,
    textColor: Color = C.OnVolt,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
) {
    val bg by animateColorAsState(if (enabled) color else C.S3, label = "btn")
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .pressScale(onClick, enabled),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) textColor else C.Lo, modifier = Modifier.size(20.dp))
            HGap(8.dp)
        }
        Text(text.uppercase(), style = T.Button, color = if (enabled) textColor else C.Lo)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = C.Hi,
    border: Color = C.LineStrong,
    height: Dp = 52.dp,
    hPad: Dp = 16.dp,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .pressScale(onClick)
            .padding(horizontal = hPad),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            HGap(8.dp)
        }
        Text(text.uppercase(), style = T.Button.copy(fontSize = T.Button.fontSize * 0.9f), color = color, maxLines = 1)
    }
}

/** Square icon button that sits in a row of 48dp buttons, e.g. the scanner next to Add food. */
@Composable
fun IconSquareButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(16.dp))
            .border(1.dp, C.Volt.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .pressScale(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = C.Volt, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, selectedColor: Color = C.Volt) {
    val bg by animateColorAsState(if (selected) selectedColor else C.S2, label = "chip")
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (selected) selectedColor else C.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.Small, color = if (selected) C.OnVolt else C.Hi, maxLines = 1)
    }
}

@Composable
fun Tag(text: String, color: Color = C.Mid, bg: Color = C.S2, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text.uppercase(), style = T.Eyebrow.copy(letterSpacing = T.Eyebrow.letterSpacing * 0.6f), color = color, maxLines = 1)
    }
}

/** Two-option toggle, e.g. "No / Yes". Null = nothing selected yet. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(C.Volt, C.Red),
) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(C.S2).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = selected == i
            val bg by animateColorAsState(if (sel) colors.getOrElse(i) { C.Volt } else Color.Transparent, label = "seg")
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(bg).clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.BodyS, color = if (sel) (if (colors.getOrElse(i) { C.Volt } == C.Red) C.Hi else C.OnVolt) else C.Mid)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.BodyM, color = C.Hi)
            if (subtitle != null) Text(subtitle, style = T.Small, color = C.Mid)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.OnVolt, checkedTrackColor = C.Volt,
                uncheckedThumbColor = C.Mid, uncheckedTrackColor = C.S3, uncheckedBorderColor = C.LineStrong,
            ),
        )
    }
}

@Composable
fun ValueRow(title: String, value: String, subtitle: String? = null, valueColor: Color = C.Volt, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.BodyM, color = C.Hi)
            if (subtitle != null) Text(subtitle, style = T.Small, color = C.Mid)
        }
        Text(value, style = T.Num.copy(fontSize = T.Num.fontSize * 0.9f), color = valueColor)
    }
}

// ------------------------------------------------------------------ progress

@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 10.dp,
    color: Color = C.Volt,
    track: Color = C.S3,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val over = progress > 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (over) {
                drawArc(color.copy(alpha = 0.35f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw * 0.35f))
            }
        }
        content()
    }
}

@Composable
fun Bar(progress: Float, modifier: Modifier = Modifier, color: Color = C.Volt, track: Color = C.S3, height: Dp = 6.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(p).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun MacroLine(label: String, value: String, progress: Float, color: Color = C.Volt) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
            Text(value, style = T.Small, color = C.Hi)
        }
        Gap(6.dp)
        Bar(progress, color = color, height = 5.dp)
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = C.Hi, caption: String? = null) {
    Column(modifier) {
        Eyebrow(label)
        Gap(6.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = T.NumL, color = color)
            if (unit != null) {
                HGap(3.dp)
                Text(unit, style = T.Small, color = C.Mid, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (caption != null) Text(caption, style = T.Tiny, color = C.Lo)
    }
}

// ------------------------------------------------------------------ inputs

@Composable
fun CheckCircle(checked: Boolean, onClick: () -> Unit, size: Dp = 40.dp, color: Color = C.Volt) {
    val haptic = rememberHaptic()
    val bg by animateColorAsState(if (checked) color else Color.Transparent, tween(180), label = "chk")
    val scale by animateFloatAsState(if (checked) 1f else 0.92f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "chkScale")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, if (checked) color else C.LineStrong, RoundedCornerShape(12.dp))
            .clickable { haptic(!checked); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(checked, enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.Check, null, tint = C.OnVolt, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
    dim: Boolean = false,
    btn: Dp = 34.dp,
) {
    val haptic = rememberHaptic()
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(btn, 40.dp).clickable { haptic(false); onMinus() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, "Less", tint = C.Mid, modifier = Modifier.size(16.dp))
        }
        Box(
            Modifier.weight(1f).heightIn(min = 40.dp).let { if (onValueClick != null) it.clickable(onClick = onValueClick) else it },
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = T.Num.copy(fontSize = T.Num.fontSize * 0.95f), color = if (dim) C.Mid else C.Hi, textAlign = TextAlign.Center, maxLines = 1)
        }
        Box(Modifier.size(btn, 40.dp).clickable { haptic(false); onPlus() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, "More", tint = C.Mid, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ForgeField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    suffix: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = T.Small) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = T.BodyM.copy(color = C.Hi),
        suffix = suffix?.let { { Text(it, style = T.Small, color = C.Mid) } },
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Volt, unfocusedBorderColor = C.LineStrong,
            focusedLabelColor = C.Volt, unfocusedLabelColor = C.Mid,
            cursorColor = C.Volt, focusedContainerColor = C.S2, unfocusedContainerColor = C.S2,
        ),
    )
}

/** Simple number entry dialog. */
@Composable
fun NumberDialog(
    title: String,
    initial: String,
    suffix: String?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    message: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    ForgeDialog(
        title = title,
        onDismiss = onDismiss,
        confirm = "Save",
        onConfirm = { text.replace(',', '.').toDoubleOrNull()?.let(onConfirm) },
    ) {
        if (message != null) {
            Text(message, style = T.Body, color = C.Mid)
            Gap(12.dp)
        }
        ForgeField(text, { text = it }, label = title, number = true, suffix = suffix)
    }
}

@Composable
fun ForgeDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String = "Cancel",
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.S1,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title.uppercase(), style = T.H2, color = C.Hi) },
        text = { Column(content = content) },
        confirmButton = {
            Text(
                confirm.uppercase(), style = T.Button, color = if (destructive) C.Red else C.Volt,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onConfirm() }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
        dismissButton = if (dismiss.isBlank()) null else {
            {
                Text(
                    dismiss.uppercase(), style = T.Button, color = C.Mid,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        },
    )
}

// ------------------------------------------------------------------ details

/**
 * Extra explanation that most people only need once. Closed by default, so the
 * screen shows numbers first and words only on request.
 */
@Composable
fun Details(
    label: String = "Details",
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "details")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
            Icon(
                Icons.Rounded.ExpandMore, if (open) "Hide $label" else "Show $label", tint = C.Mid,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot },
            )
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), content = content)
        }
    }
}

/** Small legend entry: a colour swatch and a label. */
@Composable
fun LegendItem(label: String, color: Color, line: Boolean = false, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
            if (line) {
                drawLine(
                    color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4f, cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null,
                )
            } else {
                drawCircle(color, size.height / 2.2f, Offset(size.width / 2, size.height / 2))
            }
        }
        HGap(5.dp)
        Text(label, style = T.Tiny, color = C.Mid)
    }
}

// ------------------------------------------------------------------ charts

private const val DAY_MS = 86_400_000L

/** Range picker shared by the time-series charts. */
enum class ChartRange(val label: String, val days: Long?) { M1("1M", 30), M3("3M", 90), Y1("1Y", 365), ALL("All", null) }

@Composable
fun RangePicker(selected: ChartRange, onSelect: (ChartRange) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(C.S2).padding(2.dp)) {
        ChartRange.entries.forEach { r ->
            val sel = r == selected
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) C.S3 else Color.Transparent)
                    .clickable { onSelect(r) }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text(r.label, style = T.Tiny, color = if (sel) C.Hi else C.Lo) }
        }
    }
}

private fun niceLabel(v: Double, decimals: Int): String =
    if (decimals == 0) Math.round(v).toString() else String.format(java.util.Locale.US, "%.${decimals}f", v)

private val shortDate = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

/**
 * Time-series line chart with labelled axes. [points] are drawn as faint dots
 * (single readings), [line] as the main line (usually an average). X values are
 * epoch days.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    line: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    height: Dp = 170.dp,
    decimals: Int = 1,
    lineColor: Color = C.Volt,
    empty: String = "Not enough data yet.",
) {
    val xs = (points.map { it.first } + line.map { it.first })
    if (xs.distinct().size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(empty, style = T.Small, color = C.Lo)
        }
        return
    }
    val minX = xs.min()
    val maxX = xs.max().coerceAtLeast(minX + 1)
    val ys = points.map { it.second } + line.map { it.second }
    // Show the goal only when it's close enough not to squash the data into a flat line.
    val span = (ys.max() - ys.min()).coerceAtLeast(1.0)
    val goalShown = goal?.takeIf { it in (ys.min() - span)..(ys.max() + span) }
    val all = ys + listOfNotNull(goalShown)
    val pad = ((all.max() - all.min()) * 0.12).coerceAtLeast(0.3)
    val minY = all.min() - pad
    val maxY = all.max() + pad
    val reveal by animateFloatAsState(1f, tween(900, easing = FastOutSlowInEasing), label = "line")

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.fillMaxHeight().padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maxY, (maxY + minY) / 2, minY).forEach { Text(niceLabel(it, decimals), style = T.Tiny, color = C.Lo) }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                fun x(v: Long) = (v - minX).toFloat() / (maxX - minX) * size.width
                fun y(v: Double) = (size.height - ((v - minY) / (maxY - minY)) * size.height).toFloat()
                for (i in 0..2) {
                    val gy = 7f + (size.height - 14f) * i / 2f
                    drawLine(C.Line, Offset(0f, gy), Offset(size.width, gy), 1f)
                }
                goalShown?.let { g ->
                    drawLine(C.Hi.copy(alpha = 0.4f), Offset(0f, y(g)), Offset(size.width, y(g)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                }
                points.forEach { (d, v) -> drawCircle(C.Mid.copy(alpha = 0.55f), 3.5f, Offset(x(d), y(v))) }
                if (line.size >= 2) {
                    val n = (line.size * reveal).toInt().coerceIn(2, line.size)
                    val path = Path()
                    line.take(n).forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
                    // Soft fill under the line makes the direction readable at a glance.
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(x(line[n - 1].first), size.height)
                        lineTo(x(line.first().first), size.height)
                        close()
                    }
                    drawPath(fill, lineColor.copy(alpha = 0.10f))
                    drawPath(path, lineColor, style = Stroke(4f, cap = StrokeCap.Round))
                    val last = line[n - 1]
                    drawCircle(lineColor, 7f, Offset(x(last.first), y(last.second)))
                    drawCircle(C.Bg, 3f, Offset(x(last.first), y(last.second)))
                } else if (line.size == 1) {
                    drawCircle(lineColor, 6f, Offset(x(line[0].first), y(line[0].second)))
                }
            }
        }
        Gap(4.dp)
        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            val mid = (minX + maxX) / 2
            listOf(minX, mid, maxX).forEachIndexed { i, d ->
                Text(
                    java.time.LocalDate.ofEpochDay(d).format(shortDate), style = T.Tiny, color = C.Lo,
                    textAlign = when (i) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Weight chart: daily readings as dots, 7-day average as a line, with a range picker. */
@Composable
fun WeightChart(points: List<Pair<Long, Double>>, avg: List<Pair<Long, Double>>, modifier: Modifier = Modifier, goal: Double? = null) {
    var range by rememberSaveable { mutableStateOf(ChartRange.M3) }
    val last = (points.map { it.first } + avg.map { it.first }).maxOrNull()
    val from = range.days?.let { d -> last?.minus(d) }
    fun cut(l: List<Pair<Long, Double>>) = if (from == null) l else l.filter { it.first >= from }
    Column(modifier.fillMaxWidth()) {
        RangePicker(range, { range = it }, Modifier.align(Alignment.End))
        Gap(12.dp)
        LineChart(cut(points), cut(avg), goal = goal, empty = "Log at least two mornings to see the trend.")
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem("Average", C.Volt, line = true)
            LegendItem("Daily", C.Mid)
            if (goal != null) LegendItem("Goal", C.Hi.copy(alpha = 0.5f), line = true, dashed = true)
        }
    }
}

/**
 * Bar chart with the value printed on each bar and an optional dashed target.
 * Bars at or above 95% of the target use the accent colour.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    target: Float? = null,
    highlight: Int = -1,
    height: Dp = 130.dp,
    valueText: (Float) -> String = { if (it <= 0f) "" else Math.round(it).toString() },
    color: Color = C.Volt,
) {
    val reveal by animateFloatAsState(1f, tween(800, easing = FastOutSlowInEasing), label = "bars")
    val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast((target ?: 0f) * 1.1f).coerceAtLeast(1f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    val frac = (v / maxV * reveal).coerceIn(0f, 1f)
                    val hit = target != null && v >= target * 0.95f
                    val col = when {
                        target == null -> if (i == highlight) color else color.copy(alpha = 0.55f)
                        hit -> color
                        i == highlight -> C.Hi.copy(alpha = 0.7f)
                        else -> C.LineStrong
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(valueText(v), style = T.Tiny, color = if (hit || i == highlight) C.Hi else C.Mid, maxLines = 1)
                        Gap(3.dp)
                        Box(
                            Modifier.fillMaxWidth(0.62f).fillMaxHeight(frac * 0.82f)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(col),
                        )
                    }
                }
            }
            if (target != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val ty = size.height - (target / maxV) * size.height * 0.82f
                    drawLine(C.Hi.copy(alpha = 0.4f), Offset(0f, ty), Offset(size.width, ty), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
            }
        }
        Hairline()
        Gap(6.dp)
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(l, style = T.Tiny, color = if (i == highlight) C.Hi else C.Lo, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

/** Seven bars against a target line. */
@Composable
fun WeekBars(values: List<Float>, target: Float, labels: List<String>, modifier: Modifier = Modifier, highlight: Int = -1) =
    BarChart(values, labels, modifier, target = target, highlight = highlight)

/**
 * A strip of small squares, one per day, oldest on the left. Good for showing
 * consistency over the last few weeks without any words.
 */
@Composable
fun DayDots(colors: List<Color>, modifier: Modifier = Modifier, cell: Dp = 10.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        colors.forEach { c ->
            Box(Modifier.weight(1f).height(cell).clip(RoundedCornerShape(3.dp)).background(c))
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = C.Volt) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(C.S3, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f)
            return@Canvas
        }
        val min = values.min()
        val max = values.max().let { if (it - min < 0.001) min + 1 else it }
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = size.width * i / (values.size - 1)
            val py = size.height - ((v - min) / (max - min)).toFloat() * size.height
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

@Composable
fun BottomSpace() = Spacer(Modifier.navigationBarsPadding().height(96.dp))

@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = C.Volt, style: TextStyle = T.BodyS) {
    Text(text, style = style, color = color, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp))
}
