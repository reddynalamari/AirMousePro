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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.Key
import com.airmousepro.input.tvPad
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

    // Controlling a TV: swipes press arrow keys and the buttons are TV keys, not mouse buttons.
    val tv = hid.tvMode

    if (isLandscape()) {
        val side: @Composable () -> Unit = {
            Column(Modifier.width(132.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tv) {
                    TvButtons(hid, vertical = true)
                } else {
                    buttons.forEach { (icon, label, mask) ->
                        HoldButton(label, Modifier.weight(if (mask == HidController.BUTTON_MIDDLE) 0.6f else 1f), icon = icon) { hid.setButton(mask, it) }
                    }
                    SpeedSlider()
                }
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
            if (tv) {
                Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvButtons(hid, vertical = false) }
            } else {
                Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { (icon, label, mask) ->
                        HoldButton(label, Modifier.weight(if (mask == HidController.BUTTON_MIDDLE) 0.6f else 1f), icon = icon) { hid.setButton(mask, it) }
                    }
                }
                SpeedSlider()
            }
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
    val tv = hid.tvMode

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
            // A TV is navigated with arrow keys and OK, so it gets the remote-style pad instead of a pointer.
            .then(if (tv) Modifier.tvPad(hid, settings, view) else Modifier.trackpad(hid, settings, view, scope) { speed }),
        contentAlignment = Alignment.Center,
    ) {
        if (touches.isEmpty()) {
            if (tv) {
                DpadGlyph(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    HintIcon(Icons.Rounded.TouchApp, "tap")
                    HintIcon(Icons.Rounded.Swipe, "2 fingers")
                    HintIcon(Icons.Rounded.ZoomIn, "pinch")
                }
            }
        }
    }
}

/** A faint D-pad drawn on the pad when it is acting as a TV remote. Just a picture, no words. */
@Composable
fun DpadGlyph(tint: Color) {
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).clip(CircleShape).border(2.dp, tint, CircleShape))
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = tint, modifier = Modifier.align(Alignment.TopCenter).size(34.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = tint, modifier = Modifier.align(Alignment.BottomCenter).size(34.dp))
        Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = null, tint = tint, modifier = Modifier.align(Alignment.CenterStart).size(34.dp))
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = tint, modifier = Modifier.align(Alignment.CenterEnd).size(34.dp))
    }
}

/** The four keys a TV remote always has: Back, Home, OK and Menu. They sit where the mouse buttons normally are. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.TvButtons(hid: HidController, @Suppress("UNUSED_PARAMETER") vertical: Boolean) {
    TvKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.weight(1f)) { hid.consumer(Consumer.BACK) }
    TvKey(Icons.Rounded.Home, "Home", Modifier.weight(1f)) { hid.consumer(Consumer.HOME) }
    TvKey(Icons.Rounded.RadioButtonChecked, "OK", Modifier.weight(1f)) { hid.keyCombo(0, Key.ENTER) }
    TvKey(Icons.Rounded.Menu, "Menu", Modifier.weight(1f)) { hid.consumer(Consumer.MENU) }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.TvButtons(hid: HidController, vertical: Boolean) {
    TvKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.weight(1f)) { hid.consumer(Consumer.BACK) }
    TvKey(Icons.Rounded.Home, "Home", Modifier.weight(1f)) { hid.consumer(Consumer.HOME) }
    TvKey(Icons.Rounded.RadioButtonChecked, "OK", Modifier.weight(1f)) { hid.keyCombo(0, Key.ENTER) }
    TvKey(Icons.Rounded.Menu, "Menu", Modifier.weight(1f)) { hid.consumer(Consumer.MENU) }
}

@Composable
private fun TvKey(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) { detectTapGestures(onPress = { Haptics.tap(view, settings); onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
