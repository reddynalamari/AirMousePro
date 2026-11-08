package com.airmousepro.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.input.AirMouseEngine
import com.airmousepro.input.tvPad
import com.airmousepro.input.HardwareKeys
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

private const val LONG_PRESS_MS = 380L
private const val TAP_MS = 280L
private const val PX_PER_NOTCH = 30f

/**
 * Point with the phone, click with your thumb:
 *  - tap anywhere = click, two-finger tap = right click, three-finger tap = middle click
 *  - press and hold = drag (the pointer follows the phone while held)
 *  - swipe on the screen = scroll
 *  - volume up = click (hold to drag), volume down = right click
 * The pointer freezes while a finger is down, so tapping never moves the cursor.
 */
@Composable
fun AirMouseScreen(hid: HidController) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val view = LocalView.current
    val engine = remember { AirMouseEngine(ctx, hid, settings) }
    var active by remember { mutableStateOf(true) }
    var dragUi by remember { mutableStateOf(false) }

    DisposableEffect(engine) {
        engine.active = true
        engine.onToggleRequest = { active = !active }
        // On a TV the engine flicks arrow keys instead of steering a pointer.
        engine.tvMode = { hid.tvMode }
        engine.start()
        onDispose {
            engine.stop()
            engine.onToggleRequest = null
            engine.tvMode = null
        }
    }
    DisposableEffect(active) {
        engine.active = active
        onDispose { }
    }

    // Volume keys as mouse buttons, only while this screen is showing.
    val keysOn = settings.airVolumeKeys
    DisposableEffect(keysOn) {
        if (keysOn) {
            HardwareKeys.handler = { e ->
                if (e.keyCode != KeyEvent.KEYCODE_VOLUME_UP && e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
                    false
                } else if (hid.tvMode) {
                    // On a TV the volume keys are the two buttons you want under your thumb: OK and Back.
                    if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                        Haptics.tap(view, settings)
                        if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP) hid.keyCombo(0, Key.ENTER) else hid.consumer(Consumer.BACK)
                    }
                    true
                } else {
                    val button = if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP) HidController.BUTTON_LEFT else HidController.BUTTON_RIGHT
                    if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                        engine.freezeFor(320)
                        engine.beginClick()
                        hid.setButton(button, true)
                        Haptics.tap(view, settings)
                    } else if (e.action == KeyEvent.ACTION_UP) {
                        hid.setButton(button, false)
                        engine.freezeFor(160)
                    }
                    true
                }
            }
        }
        onDispose { HardwareKeys.handler = null }
    }

    val surface = @Composable { m: Modifier ->
        AirSurface(hid, engine, active, dragUi, onDrag = { dragUi = it }, modifier = m)
    }
    val controls = @Composable {
        AirControls(engine, active, onToggle = { active = !active })
    }

    if (isLandscape()) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            surface(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) { controls() }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            surface(Modifier.weight(1f).fillMaxWidth())
            controls()
        }
    }
}

@Composable
private fun AirControls(engine: AirMouseEngine, active: Boolean, onToggle: () -> Unit) {
    val settings = LocalContext.current.appSettings
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = if (active) "Pause pointer" else "Resume pointer", tint = androidx.compose.ui.graphics.Color.White) }
        SmallAction(Icons.Rounded.RestartAlt, "Zero drift") { engine.zeroDrift() }
        // Cycle which edge of the phone points at the screen.
        val edge = listOf("Top", "Right", "Left")[settings.airPointing.coerceIn(0, 2)]
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { settings.airPointing = (settings.airPointing + 1) % 3 }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) { Text("Point: $edge", fontSize = 13.sp) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Tune, contentDescription = "Sensitivity", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
        Slider(
            value = settings.airSensitivity, onValueChange = { settings.airSensitivity = it },
            valueRange = 0.3f..3f, modifier = Modifier.padding(start = 6.dp),
        )
    }
    if (settings.airVolumeKeys) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Icons.Rounded.VolumeUp, "click")
            Legend(Icons.Rounded.VolumeDown, "right-click")
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun Legend(icon: ImageVector, caption: String) {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
        Text(" $caption", color = c, fontSize = 12.sp)
    }
}

@Composable
private fun AirSurface(
    hid: HidController,
    engine: AirMouseEngine,
    active: Boolean,
    dragUi: Boolean,
    onDrag: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    val accent = MaterialTheme.colorScheme.primary
    val tv = hid.tvMode
    val bg = when {
        dragUi -> accent.copy(alpha = 0.35f)
        active -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            // On a TV the screen is a swipe-for-arrows pad (tap is OK); the pointer gestures below step aside.
            .then(if (tv) Modifier.tvPad(hid, settings, view) else Modifier)
            .pointerInput(tv) {
                if (tv) return@pointerInput
                val slop = viewConfiguration.touchSlop * 1.6f
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    engine.touching = true
                    engine.beginClick()
                    val t0 = SystemClock.uptimeMillis()
                    val start = HashMap<PointerId, androidx.compose.ui.geometry.Offset>()
                    start[first.id] = first.position
                    var maxPointers = 1
                    var maxMoved = 0f
                    var scrolling = false
                    var held = false
                    var accX = 0f
                    var accY = 0f

                    while (true) {
                        val canLongPress = !held && !scrolling && maxPointers == 1
                        val event = if (canLongPress) {
                            val left = LONG_PRESS_MS - (SystemClock.uptimeMillis() - t0)
                            if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Long press: start a drag, and let the pointer follow the phone again.
                            held = true
                            engine.dragging = true
                            hid.setButton(HidController.BUTTON_LEFT, true)
                            Haptics.tick(view, settings)
                            onDrag(true)
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        event.changes.forEach { if (it.pressed && it.id !in start) start[it.id] = it.position }
                        if (pressed.size > maxPointers) maxPointers = pressed.size
                        pressed.forEach { ch ->
                            val d = ch.position - (start[ch.id] ?: ch.position)
                            maxMoved = maxOf(maxMoved, hypot(d.x, d.y))
                        }
                        if (pressed.isEmpty()) {
                            event.changes.forEach { it.consume() }
                            break
                        }
                        if (!held && maxMoved > slop) scrolling = true
                        if (scrolling) {
                            val dx = pressed.map { it.positionChange().x }.average().toFloat()
                            val dy = pressed.map { it.positionChange().y }.average().toFloat()
                            accX += dx
                            accY += dy
                            val vy = (accY / PX_PER_NOTCH).toInt()
                            val vx = (accX / PX_PER_NOTCH).toInt()
                            if (vy != 0 || vx != 0) {
                                val sgn = if (settings.naturalScroll) 1 else -1
                                hid.scroll(vy * sgn, -vx * sgn)
                                accY -= vy * PX_PER_NOTCH
                                accX -= vx * PX_PER_NOTCH
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }

                    val duration = SystemClock.uptimeMillis() - t0
                    if (held) {
                        hid.setButton(HidController.BUTTON_LEFT, false)
                        onDrag(false)
                    } else if (!scrolling && duration < TAP_MS && maxMoved < slop) {
                        Haptics.tap(view, settings)
                        when (maxPointers) {
                            1 -> hid.click(HidController.BUTTON_LEFT)
                            2 -> hid.click(HidController.BUTTON_RIGHT)
                            else -> hid.click(HidController.BUTTON_MIDDLE)
                        }
                    }
                    engine.dragging = false
                    engine.touching = false
                    engine.freezeFor(160)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        if (tv) {
            DpadGlyph(c)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                AirHint(Icons.Rounded.TouchApp, "click", c)
                AirHint(Icons.Rounded.PanTool, "hold = drag", c)
                AirHint(Icons.Rounded.Swipe, "swipe = scroll", c)
            }
        }
    }
}

@Composable
private fun AirHint(icon: ImageVector, caption: String, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(caption, color = tint, fontSize = 11.sp)
    }
}
