package com.airmousepro.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.applyOrientation
import com.airmousepro.hid.HidController
import kotlin.math.hypot
import kotlin.math.sqrt

/** Everything the host sees in one gamepad report. */
private class PadState(private val hid: HidController) {
    var buttons = 0
    var up = false
    var down = false
    var left = false
    var right = false
    var lx = 0
    var ly = 0
    var rx = 0
    var ry = 0
    var lt = 0
    var rt = 0
    // Gyro aim is added on top of the right stick.
    @Volatile var gx = 0
    @Volatile var gy = 0

    private fun hat(): Int = when {
        up && right -> 2
        right && down -> 4
        down && left -> 6
        left && up -> 8
        up -> 1
        right -> 3
        down -> 5
        left -> 7
        else -> 0
    }

    @Synchronized
    fun push() {
        hid.gamepad(buttons, hat(), lx, ly, (rx + gx).coerceIn(-127, 127), (ry + gy).coerceIn(-127, 127), lt, rt)
    }

    fun button(bit: Int, down: Boolean) {
        buttons = if (down) buttons or (1 shl bit) else buttons and (1 shl bit).inv()
        push()
    }

    fun reset() {
        buttons = 0; up = false; down = false; left = false; right = false
        lx = 0; ly = 0; rx = 0; ry = 0; lt = 0; rt = 0; gx = 0; gy = 0
        push()
    }
}

// Button numbers match the common DirectInput order, so most games map them without setup.
private const val B_A = 0
private const val B_B = 1
private const val B_X = 2
private const val B_Y = 3
private const val B_LB = 4
private const val B_RB = 5
private const val B_BACK = 6
private const val B_START = 7
private const val B_L3 = 8
private const val B_R3 = 9
private const val B_LT = 10
private const val B_RT = 11

private const val GYRO_COUNTS_PER_RAD_S = 45f
private const val GYRO_DEAD_ZONE = 0.04f

/**
 * Phone as a Bluetooth gamepad: two sticks, D-pad, face buttons, bumpers and triggers, plus optional gyro aiming
 * on the right stick. It needs landscape, so the screen asks for it while this page is showing.
 */
@Composable
fun GamepadScreen(hid: HidController) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val pad = remember { PadState(hid) }
    var gyro by remember { mutableStateOf(false) }

    // Landscape while this page is visible, then back to the user's own rotation policy.
    DisposableEffect(Unit) {
        (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            pad.reset()
            (ctx as? ComponentActivity)?.let { applyOrientation(it, settings) }
        }
    }

    DisposableEffect(gyro) {
        if (!gyro) {
            pad.gx = 0
            pad.gy = 0
            return@DisposableEffect onDispose { }
        }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val up = floatArrayOf(0f, 0f, 9.81f)
        var sx = 0f
        var sy = 0f
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type != Sensor.TYPE_GYROSCOPE) {
                    // Low-pass the raw accelerometer if there is no fused gravity sensor.
                    val a = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) 0.1f else 1f
                    for (i in 0..2) up[i] = up[i] * (1 - a) + e.values[i] * a
                    return
                }
                val gn = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]).coerceAtLeast(0.001f)
                val ux = up[0] / gn
                val uy = up[1] / gn
                val uz = up[2] / gn
                // Aiming through the screen: pointing = -z, so the pitch axis is (uy, -ux, 0).
                val rn = hypot(ux, uy)
                val yaw = e.values[0] * ux + e.values[1] * uy + e.values[2] * uz
                val pitch = if (rn > 0.3f) (e.values[0] * uy - e.values[1] * ux) / rn else e.values[0]
                sx += (yaw - sx) * 0.6f
                sy += (pitch - sy) * 0.6f
                val ox = if (kotlin.math.abs(sx) < GYRO_DEAD_ZONE) 0f else sx
                val oy = if (kotlin.math.abs(sy) < GYRO_DEAD_ZONE) 0f else sy
                pad.gx = (-ox * GYRO_COUNTS_PER_RAD_S).toInt().coerceIn(-127, 127)
                pad.gy = (-oy * GYRO_COUNTS_PER_RAD_S).toInt().coerceIn(-127, 127)
                pad.push()
            }
        }
        gyroSensor?.let { sm.registerListener(listener, it, 8_000) }
        gravitySensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose {
            sm.unregisterListener(listener)
            pad.gx = 0
            pad.gy = 0
            pad.push()
        }
    }

    if (!isLandscape()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.ScreenRotation, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Turning to landscape…", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        // The page is short in landscape, so everything scales with the available height.
        val k = (maxHeight.value / 290f).coerceIn(0.62f, 1.25f)
        fun s(v: Float): Dp = (v * k).dp

        // Left side: bumpers, left stick, D-pad.
        Column(Modifier.align(Alignment.CenterStart), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s(8f))) {
            Row(horizontalArrangement = Arrangement.spacedBy(s(8f))) {
                Shoulder("LT", Modifier.size(s(64f), s(34f))) { pad.lt = if (it) 255 else 0; pad.button(B_LT, it) }
                Shoulder("LB", Modifier.size(s(64f), s(34f))) { pad.button(B_LB, it) }
            }
            Stick(s(108f)) { x, y -> pad.lx = x; pad.ly = y; pad.push() }
            DPad(s(40f), pad)
        }

        // Centre: system buttons and the gyro switch.
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s(10f))) {
            Row(horizontalArrangement = Arrangement.spacedBy(s(10f))) {
                Shoulder("Back", Modifier.size(s(58f), s(32f))) { pad.button(B_BACK, it) }
                Shoulder("Start", Modifier.size(s(58f), s(32f))) { pad.button(B_START, it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(s(10f))) {
                Shoulder("L3", Modifier.size(s(44f), s(32f))) { pad.button(B_L3, it) }
                Shoulder("R3", Modifier.size(s(44f), s(32f))) { pad.button(B_R3, it) }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (gyro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { gyro = !gyro }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Gyro aim", fontSize = 13.sp, color = if (gyro) Color.White else MaterialTheme.colorScheme.onSurface) }
        }

        // Right side: bumpers, face buttons, right stick.
        Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s(8f))) {
            Row(horizontalArrangement = Arrangement.spacedBy(s(8f))) {
                Shoulder("RB", Modifier.size(s(64f), s(34f))) { pad.button(B_RB, it) }
                Shoulder("RT", Modifier.size(s(64f), s(34f))) { pad.rt = if (it) 255 else 0; pad.button(B_RT, it) }
            }
            Box(Modifier.size(s(124f))) {
                Face("Y", Color(0xFFFFB800), Modifier.align(Alignment.TopCenter), s(42f)) { pad.button(B_Y, it) }
                Face("A", Color(0xFF30D158), Modifier.align(Alignment.BottomCenter), s(42f)) { pad.button(B_A, it) }
                Face("X", Color(0xFF0A84FF), Modifier.align(Alignment.CenterStart), s(42f)) { pad.button(B_X, it) }
                Face("B", Color(0xFFFF453A), Modifier.align(Alignment.CenterEnd), s(42f)) { pad.button(B_B, it) }
            }
            Stick(s(108f)) { x, y -> pad.rx = x; pad.ry = y; pad.push() }
        }
    }
}

@Composable
private fun DPad(cell: Dp, pad: PadState) {
    Box(Modifier.size(cell * 3)) {
        Face("", MaterialTheme.colorScheme.surfaceVariant, Modifier.align(Alignment.TopCenter), cell, arrow = Arrow.Up) { pad.up = it; pad.push() }
        Face("", MaterialTheme.colorScheme.surfaceVariant, Modifier.align(Alignment.BottomCenter), cell, arrow = Arrow.Down) { pad.down = it; pad.push() }
        Face("", MaterialTheme.colorScheme.surfaceVariant, Modifier.align(Alignment.CenterStart), cell, arrow = Arrow.Left) { pad.left = it; pad.push() }
        Face("", MaterialTheme.colorScheme.surfaceVariant, Modifier.align(Alignment.CenterEnd), cell, arrow = Arrow.Right) { pad.right = it; pad.push() }
    }
}

private enum class Arrow { None, Up, Down, Left, Right }

/** A round, hold-able button. Arrows are drawn as a small triangle rather than a text symbol. */
@Composable
private fun Face(label: String, color: Color, modifier: Modifier, size: Dp, arrow: Arrow = Arrow.None, onChange: (Boolean) -> Unit) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val tri = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) color.copy(alpha = 0.55f) else color)
            .drawBehind {
                if (arrow == Arrow.None) return@drawBehind
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val r = this.size.width * 0.18f
                val path = androidx.compose.ui.graphics.Path().apply {
                    when (arrow) {
                        Arrow.Up -> { moveTo(c.x, c.y - r); lineTo(c.x - r, c.y + r); lineTo(c.x + r, c.y + r) }
                        Arrow.Down -> { moveTo(c.x, c.y + r); lineTo(c.x - r, c.y - r); lineTo(c.x + r, c.y - r) }
                        Arrow.Left -> { moveTo(c.x - r, c.y); lineTo(c.x + r, c.y - r); lineTo(c.x + r, c.y + r) }
                        Arrow.Right -> { moveTo(c.x + r, c.y); lineTo(c.x - r, c.y - r); lineTo(c.x - r, c.y + r) }
                        Arrow.None -> {}
                    }
                    close()
                }
                drawPath(path, tri)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    Haptics.tap(view, settings)
                    onChange(true)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    pressed = false
                    onChange(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun Shoulder(label: String, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Box(modifier) { HoldButton(label, Modifier, onChange = onChange) }
}

/** Virtual analogue stick: the knob follows the finger inside the ring and springs back on release. */
@Composable
private fun Stick(diameter: Dp, onMove: (Int, Int) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val base = MaterialTheme.colorScheme.surface
    val ring = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(base)
            .border(2.dp, ring, CircleShape)
            .drawBehind {
                val r = size.width / 2f
                drawCircle(accent.copy(alpha = 0.9f), radius = r * 0.38f, center = Offset(r, r) + knob)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val r = size.width / 2f
                    val travel = r * 0.62f
                    val c = Offset(r, r)
                    fun update(p: Offset) {
                        var d = p - c
                        val len = hypot(d.x, d.y)
                        if (len > travel) d = d * (travel / len)
                        knob = d
                        onMove((d.x / travel * 127f).toInt(), (d.y / travel * 127f).toInt())
                    }
                    update(down.position)
                    do {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: e.changes.first()
                        if (ch.pressed) update(ch.position)
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    knob = Offset.Zero
                    onMove(0, 0)
                }
            },
    )
}
