package com.airmousepro.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController
import com.airmousepro.ui.Haptics
import com.airmousepro.ui.HoldButton
import com.airmousepro.ui.TouchSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Bubble
// ---------------------------------------------------------------------------------------------

@Composable
fun BubbleUi(sizeDp: Int, opacity: Float, onTap: () -> Unit, onDrag: (Float, Float) -> Unit, onRelease: () -> Unit) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    // Fades while untouched so it stays out of the way, and wakes on touch.
    var touches by remember { mutableIntStateOf(0) }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(touches) {
        idle = false
        delay(2500)
        idle = true
    }
    val alpha by animateFloatAsState(if (idle) opacity * 0.5f else opacity, label = "bubbleAlpha")

    Box(
        Modifier
            .size(sizeDp.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    touches++
                    Haptics.tap(view, settings)
                    onTap()
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touches++ },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                ) { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Mouse, contentDescription = "Quick menu", tint = Color.White, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Rotary dial
// ---------------------------------------------------------------------------------------------

private const val STEP = 34f          // degrees between neighbouring items
private const val VOLUME_STEP = 14f   // degrees of knob turn per volume key press

/**
 * A radio-style dial on the screen edge. Drag around the ring to turn it; each detent ticks, the item under the
 * pointer is enlarged, and the centre knob (or a second tap on the item) runs it. "Volume" turns the whole dial
 * into a volume knob.
 */
@Composable
fun DialUi(
    items: List<DialItem>,
    side: Int,
    cyFraction: Float,
    screenW: Int,
    screenH: Int,
    onExecute: (DialItem) -> Unit,
    onVolume: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val panel = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current.density

    val n = items.size.coerceAtLeast(1)
    val minRot = -(n - 1) * STEP
    var rot by remember { mutableFloatStateOf(0f) }
    var volumeMode by remember { mutableStateOf(false) }
    val idx = if (volumeMode) 0 else (-rot / STEP).roundToInt().coerceIn(0, n - 1)
    val selected = items.getOrNull(idx)

    val radius = min(172f * density, screenH / 2f - 12f * density).coerceAtLeast(96f * density)
    val itemRadius = radius * 0.72f
    val knobR = radius * 0.2f
    val cx = if (side == 0) 0f else screenW.toFloat()
    val cy = (cyFraction * screenH).coerceIn(radius + 8f * density, (screenH - radius - 8f * density).coerceAtLeast(radius + 8f * density))
    val pointer = if (side == 0) 0f else 180f
    val inward = if (side == 0) 1f else -1f
    val iconBox = 46f * density

    // A tick for every detent crossed, whether dragged or animated.
    var lastIdx by remember { mutableIntStateOf(idx) }
    LaunchedEffect(idx) {
        if (idx != lastIdx) {
            lastIdx = idx
            Haptics.tick(view, settings)
        }
    }

    // Holder rather than a plain var, so the running animation survives recomposition.
    val settle = remember { arrayOfNulls<Job>(1) }
    fun settleTo(target: Float) {
        settle[0]?.cancel()
        settle[0] = scope.launch {
            animate(rot, target, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> rot = v }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Keyed on the item ids, not the list object: a new list on every redraw would restart the gesture mid-drag.
            .pointerInput(items.map { it.id }, side, cx, cy, radius, volumeMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    settle[0]?.cancel()
                    val c = Offset(cx, cy)
                    val start = down.position - c
                    val startDist = hypot(start.x, start.y)
                    val onRing = startDist > knobR && startDist <= radius + 24f * density
                    var prev = Math.toDegrees(atan2(start.y, start.x).toDouble()).toFloat()
                    var travelled = 0f
                    var volAcc = 0f

                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.first()
                        if (ch.pressed && onRing) {
                            val p = ch.position - c
                            val now = Math.toDegrees(atan2(p.y, p.x).toDouble()).toFloat()
                            var delta = now - prev
                            while (delta > 180f) delta -= 360f
                            while (delta < -180f) delta += 360f
                            prev = now
                            travelled += abs(delta) * hypot(p.x, p.y) * (PI.toFloat() / 180f)
                            if (volumeMode) {
                                rot += delta
                                volAcc += delta
                                while (volAcc >= VOLUME_STEP) { volAcc -= VOLUME_STEP; onVolume(true); Haptics.tick(view, settings) }
                                while (volAcc <= -VOLUME_STEP) { volAcc += VOLUME_STEP; onVolume(false); Haptics.tick(view, settings) }
                            } else {
                                rot = (rot + delta).coerceIn(minRot, 0f)
                            }
                        }
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })

                    val tapped = travelled < 10f * density
                    if (!tapped) {
                        if (!volumeMode) settleTo(-(((-rot) / STEP).roundToInt().coerceIn(0, n - 1)) * STEP)
                        return@awaitEachGesture
                    }
                    // A tap: knob runs, an item selects (or runs if already selected), outside dismisses.
                    // The current selection is read now, because values captured at composition would be stale.
                    val curIdx = (-rot / STEP).roundToInt().coerceIn(0, n - 1)
                    val curSel = items.getOrNull(curIdx)
                    val up = down.position
                    val d = up - c
                    val dist = hypot(d.x, d.y)
                    when {
                        dist <= knobR * 1.15f -> {
                            if (volumeMode) {
                                volumeMode = false
                            } else if (curSel != null) {
                                Haptics.tap(view, settings)
                                if (curSel.id == "b:volume") volumeMode = true else onExecute(curSel)
                            }
                        }
                        dist > radius + 10f * density -> onDismiss()
                        !volumeMode -> {
                            val hit = items.indices.minByOrNull { i ->
                                val a = Math.toRadians((pointer + i * STEP + rot).toDouble())
                                hypot(up.x - (cx + itemRadius * cos(a)).toFloat(), up.y - (cy + itemRadius * sin(a)).toFloat())
                            }
                            if (hit != null) {
                                val a = Math.toRadians((pointer + hit * STEP + rot).toDouble())
                                val near = hypot(up.x - (cx + itemRadius * cos(a)).toFloat(), up.y - (cy + itemRadius * sin(a)).toFloat()) < iconBox
                                if (near) {
                                    if (hit == curIdx) {
                                        Haptics.tap(view, settings)
                                        val chosen = items[hit]
                                        if (chosen.id == "b:volume") volumeMode = true else onExecute(chosen)
                                    } else {
                                        settleTo(-hit * STEP)
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.55f))
            val center = Offset(cx, cy)
            drawCircle(panel.copy(alpha = 0.96f), radius = radius, center = center)
            drawCircle(accent.copy(alpha = 0.45f), radius = radius, center = center, style = Stroke(2.dp.toPx()))
            // Tuning marks that turn with the dial.
            for (k in 0 until 120) {
                val a = Math.toRadians((k * 3f + rot).toDouble())
                val major = k % 5 == 0
                val outer = radius - 5.dp.toPx()
                val inner = outer - (if (major) 13 else 7).dp.toPx()
                drawLine(
                    Color.White.copy(alpha = if (major) 0.5f else 0.22f),
                    Offset(cx + (outer * cos(a)).toFloat(), cy + (outer * sin(a)).toFloat()),
                    Offset(cx + (inner * cos(a)).toFloat(), cy + (inner * sin(a)).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            // Pointer notch.
            val pa = Math.toRadians(pointer.toDouble())
            val tip = radius - 22.dp.toPx()
            val base = radius + 4.dp.toPx()
            val w = 7.dp.toPx()
            val nx = -sin(pa).toFloat()
            val ny = cos(pa).toFloat()
            val path = Path().apply {
                moveTo(cx + (tip * cos(pa)).toFloat(), cy + (tip * sin(pa)).toFloat())
                lineTo(cx + (base * cos(pa)).toFloat() + nx * w, cy + (base * sin(pa)).toFloat() + ny * w)
                lineTo(cx + (base * cos(pa)).toFloat() - nx * w, cy + (base * sin(pa)).toFloat() - ny * w)
                close()
            }
            drawPath(path, accent)
        }

        if (!volumeMode) {
            items.forEachIndexed { i, item ->
                Box(
                    Modifier
                        .offset {
                            val a = Math.toRadians((pointer + i * STEP + rot).toDouble())
                            IntOffset(
                                (cx + itemRadius * cos(a) - iconBox / 2f).roundToInt(),
                                (cy + itemRadius * sin(a) - iconBox / 2f).roundToInt(),
                            )
                        }
                        .size((iconBox / density).dp)
                        .graphicsLayer {
                            val off = abs(i * STEP + rot)
                            val closeness = (1f - off / 100f).coerceIn(0f, 1f)
                            alpha = 0.25f + 0.75f * closeness
                            val s = 0.82f + 0.43f * closeness
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(if (i == idx) accent else Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(item.icon, contentDescription = item.label, tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }

        // Label for the item under the pointer, on the side facing the screen.
        Box(
            Modifier
                .align(if (side == 0) Alignment.CenterStart else Alignment.CenterEnd)
                .offset { IntOffset(0, (cy - screenH / 2f).roundToInt()) }
                .padding(start = (radius / density + 16f).dp.takeIf { side == 0 } ?: 0.dp, end = (radius / density + 16f).dp.takeIf { side != 0 } ?: 0.dp),
        ) {
            Text(
                if (volumeMode) "Volume" else selected?.label.orEmpty(),
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        // Centre knob, half hidden by the screen edge; its icon sits on the visible half.
        Box(
            Modifier
                .offset { IntOffset((cx - knobR).roundToInt(), (cy - knobR).roundToInt()) }
                .size((knobR * 2 / density).dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = if (side == 0) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Icon(
                if (volumeMode) Icons.Rounded.VolumeUp else (selected?.icon ?: Icons.Rounded.Mouse),
                contentDescription = null, tint = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp).size(26.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Mini touchpad
// ---------------------------------------------------------------------------------------------

@Composable
fun PadUi(hid: HidController, onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to move", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Box(Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp)) }
        }
        TouchSurface(hid, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp))
        Row(Modifier.fillMaxWidth().height(52.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("Left", Modifier.weight(1f)) { hid.setButton(HidController.BUTTON_LEFT, it) }
            HoldButton("Right", Modifier.weight(1f)) { hid.setButton(HidController.BUTTON_RIGHT, it) }
        }
    }
}
