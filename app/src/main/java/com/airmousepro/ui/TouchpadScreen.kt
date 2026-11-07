package com.airmousepro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController
import com.airmousepro.input.trackpad

@Composable
fun TouchpadScreen(hid: HidController) {
    val settings = LocalContext.current.appSettings
    val left = settings.leftHanded
    // Mirror the button order for left-handed use.
    val buttons: List<Triple<ImageVector, String, Int>> = listOf(
        Triple(Icons.Rounded.TouchApp, "Left", HidController.BUTTON_LEFT),
        Triple(Icons.Rounded.UnfoldMore, "Middle", HidController.BUTTON_MIDDLE),
        Triple(Icons.Rounded.Mouse, "Right", HidController.BUTTON_RIGHT),
    ).let { if (left) it.reversed() else it }

    if (isLandscape()) {
        val side: @Composable () -> Unit = {
            Column(Modifier.width(132.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { (icon, label, mask) ->
                    HoldButton(label, Modifier.weight(if (mask == HidController.BUTTON_MIDDLE) 0.6f else 1f), icon = icon) { hid.setButton(mask, it) }
                }
                SpeedSlider()
            }
        }
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (left) side()
            TouchSurface(hid, Modifier.weight(1f).fillMaxHeight())
            if (!left) side()
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchSurface(hid, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { (icon, label, mask) ->
                    HoldButton(label, Modifier.weight(if (mask == HidController.BUTTON_MIDDLE) 0.6f else 1f), icon = icon) { hid.setButton(mask, it) }
                }
            }
            SpeedSlider()
        }
    }
}

@Composable
private fun SpeedSlider() {
    val settings = LocalContext.current.appSettings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ZoomIn, contentDescription = "Pointer speed", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
        Slider(value = settings.padSpeed, onValueChange = { settings.padSpeed = it }, valueRange = 0.4f..3f, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The pad itself: dot grid, a soft ring under each finger, and the laptop-style gesture engine. */
@Composable
fun TouchSurface(hid: HidController, modifier: Modifier) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val speed by rememberUpdatedState(settings.padSpeed)
    var touches by remember { mutableStateOf(emptyList<Offset>()) }
    val dot = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val ring = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val step = 22.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(dot, radius = 1.2.dp.toPx(), center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
                touches.forEach {
                    drawCircle(ring.copy(alpha = 0.18f), radius = 34.dp.toPx(), center = it)
                    drawCircle(ring.copy(alpha = 0.55f), radius = 34.dp.toPx(), center = it, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
            .pointerInput(Unit) {
                // Watch without consuming, so the gesture engine below still gets every event.
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        touches = e.changes.filter { it.pressed }.map { it.position }
                    }
                }
            }
            .trackpad(hid, settings, view, scope) { speed },
        contentAlignment = Alignment.Center,
    ) {
        if (touches.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                HintIcon(Icons.Rounded.TouchApp, "tap")
                HintIcon(Icons.Rounded.Swipe, "2 fingers")
                HintIcon(Icons.Rounded.ZoomIn, "pinch")
            }
        }
    }
}

@Composable
private fun HintIcon(icon: ImageVector, caption: String) {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(28.dp))
        Text(caption, color = c, fontSize = 11.sp)
    }
}

/** A button that reports press and release separately, so it can be held for dragging. */
@Composable
fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    Haptics.tap(view, settings)
                    onChange(true)
                    do {
                        val e = awaitPointerEvent()
                    } while (e.changes.any { it.pressed })
                    onChange(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            Text(label)
        }
    }
}
