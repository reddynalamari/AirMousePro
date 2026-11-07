package com.airmousepro.ui

import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NavigateBefore
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod
import com.airmousepro.input.AirMouseEngine
import com.airmousepro.input.HardwareKeys
import kotlinx.coroutines.delay
import kotlin.math.abs

private val countdownChoices = listOf(0, 5, 10, 15, 20, 30, 45, 60)

private fun clock(totalSeconds: Int): String {
    val a = abs(totalSeconds)
    return "%s%02d:%02d".format(if (totalSeconds < 0) "+" else "", a / 60, a % 60)
}

/**
 * Slide clicker with a laser: big next and previous buttons, the volume keys as next and previous, a timer with
 * vibration cues, and a laser that follows the phone while the Laser button is held (Ctrl+click in PowerPoint).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresentScreen(hid: HidController) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val view = LocalView.current
    val engine = remember { AirMouseEngine(ctx, hid, settings).also { it.active = false } }
    var laser by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(0) }

    DisposableEffect(engine) {
        engine.start()
        onDispose { engine.stop() }
    }
    // The screen must stay awake while presenting.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Volume down = next slide, volume up = previous, like a hardware clicker.
    DisposableEffect(Unit) {
        HardwareKeys.handler = { e ->
            if (e.keyCode != KeyEvent.KEYCODE_VOLUME_UP && e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
                false
            } else {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                    Haptics.tap(view, settings)
                    hid.keyCombo(0, if (e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) Key.RIGHT else Key.LEFT)
                }
                true
            }
        }
        onDispose { HardwareKeys.handler = null }
    }

    val vibrator = remember { ctx.getSystemService(Vibrator::class.java) }
    LaunchedEffect(running, target) {
        while (running) {
            delay(1000)
            secs++
            if (target > 0) {
                val left = target * 60 - secs
                if (left == 60) vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                if (left == 0) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1))
            }
        }
    }

    fun pressLaser(down: Boolean) {
        laser = down
        if (down) {
            Haptics.tap(view, settings)
            hid.keyDown(Mod.CTRL)
            hid.setButton(HidController.BUTTON_LEFT, true)
            engine.active = true
        } else {
            hid.setButton(HidController.BUTTON_LEFT, false)
            hid.keyUp()
            engine.active = false
            engine.freezeFor(120)
        }
    }

    val shown = if (target > 0) target * 60 - secs else secs

    val timerCard = @Composable {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clock(shown), fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    color = if (target > 0 && shown < 0) Color(0xFFFF453A) else MaterialTheme.colorScheme.onSurface,
                )
                FilledTonalIconButton(onClick = { running = !running }) {
                    Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (running) "Pause timer" else "Start timer")
                }
                FilledTonalIconButton(onClick = { running = false; secs = 0 }) { Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset timer") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                countdownChoices.forEach { m ->
                    FilterChip(selected = target == m, onClick = { target = m; secs = 0 }, label = { Text(if (m == 0) "Count up" else "$m min") })
                }
            }
        }
    }

    val extras = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ControlKey(Icons.Rounded.PlayArrow, "Start slideshow") { hid.keyCombo(0, Key.f(5)) }
            ControlKey(Icons.Rounded.PlayCircle, "Start from this slide") { hid.keyCombo(Mod.SHIFT, Key.f(5)) }
            ControlKey(Icons.Rounded.Stop, "End slideshow") { hid.keyCombo(0, Key.ESC) }
            ControlKey(Icons.Rounded.VisibilityOff, "Black screen") { hid.keyCombo(0, 0x05) }
            ControlKey(Icons.Rounded.LightMode, "White screen") { hid.keyCombo(0, 0x1A) }
            // Google Slides toggles its laser with the L key rather than Ctrl+click.
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { hid.keyCombo(0, 0x0F) },
                contentAlignment = Alignment.Center,
            ) { Text("L", fontWeight = FontWeight.Bold) }
        }
    }

    val laserButton = @Composable { m: Modifier ->
        Box(
            m
                .clip(RoundedCornerShape(26.dp))
                .background(if (laser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressLaser(true)
                        tryAwaitRelease()
                        pressLaser(false)
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.AdsClick, contentDescription = null, modifier = Modifier.size(34.dp), tint = if (laser) Color.White else MaterialTheme.colorScheme.primary)
                Text("Hold for laser", fontSize = 13.sp, color = if (laser) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    val prevNext = @Composable { vertical: Boolean ->
        val prev = @Composable { m: Modifier -> BigKey(Icons.Rounded.NavigateBefore, "Previous slide", m) { hid.keyCombo(0, Key.LEFT) } }
        val next = @Composable { m: Modifier -> BigKey(Icons.Rounded.NavigateNext, "Next slide", m) { hid.keyCombo(0, Key.RIGHT) } }
        if (vertical) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                next(Modifier.weight(1.4f).fillMaxWidth())
                prev(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                prev(Modifier.weight(1f).fillMaxHeight())
                next(Modifier.weight(1.4f).fillMaxHeight())
            }
        }
    }

    if (isLandscape()) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                timerCard()
                extras()
            }
            laserButton(Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) { prevNext(true) }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            timerCard()
            laserButton(Modifier.weight(1f).fillMaxWidth())
            prevNext(false)
            extras()
        }
    }
}

@Composable
private fun BigKey(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) { detectTapGestures(onPress = { Haptics.tap(view, settings); onClick() }) },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(48.dp)) }
}

@Composable
private fun ControlKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp)) }
}
