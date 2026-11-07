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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /**
     * Keys mode: instead of a gamepad report, the same controls press keyboard keys and mouse buttons. Almost every
     * PC game understands those, while many ignore a generic Bluetooth gamepad.
     */
    var keysMode = false

    private var lastKeys = ""
    private var firing = false
    private var aiming = false

    private fun held(bit: Int) = buttons and (1 shl bit) != 0

    @Synchronized
    fun push() {
        if (keysMode) pushKeys()
        else hid.gamepad(buttons, hat(), lx, ly, (rx + gx).coerceIn(-127, 127), (ry + gy).coerceIn(-127, 127), lt, rt)
    }

    /** Right stick plus gyro, -127..127, used by the mouse in keys mode. */
    fun lookX() = (rx + gx).coerceIn(-127, 127)
    fun lookY() = (ry + gy).coerceIn(-127, 127)

    private fun pushKeys() {
        val keys = ArrayList<Int>(6)
        // Left stick as WASD, with a dead zone so a resting thumb sends nothing.
        if (ly < -KEY_ZONE) keys += KEY_W
        if (ly > KEY_ZONE) keys += KEY_S
        if (lx < -KEY_ZONE) keys += KEY_A
        if (lx > KEY_ZONE) keys += KEY_D
        if (up) keys += Key.UP
        if (down) keys += Key.DOWN
        if (left) keys += Key.LEFT
        if (right) keys += Key.RIGHT
        if (held(B_A)) keys += Key.SPACE
        if (held(B_B)) keys += KEY_C
        if (held(B_X)) keys += KEY_E
        if (held(B_Y)) keys += KEY_R
        if (held(B_LB)) keys += KEY_Q
        if (held(B_RB)) keys += KEY_F
        if (held(B_BACK)) keys += Key.TAB
        if (held(B_START)) keys += Key.ESC
        val mods = (if (held(B_L3)) Mod.SHIFT else 0) or (if (held(B_R3)) Mod.CTRL else 0)
        val list = keys.distinct().take(6)
        val signature = "$mods:${list.joinToString(",")}"
        if (signature != lastKeys) {
            lastKeys = signature
            if (list.isEmpty() && mods == 0) hid.keyUp() else hid.keyDown(mods, *list.toIntArray())
        }
        // Right trigger fires (left click), left trigger aims (right click).
        val fire = rt > 0
        val aim = lt > 0
        if (fire != firing) { firing = fire; hid.setButton(HidController.BUTTON_LEFT, fire) }
        if (aim != aiming) { aiming = aim; hid.setButton(HidController.BUTTON_RIGHT, aim) }
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

    /** Releases everything in the old mode before switching, so no key or button is left stuck down. */
    fun switchMode(keys: Boolean) {
        reset()
        keysMode = keys
        push()
    }
}

private const val KEY_ZONE = 45
private const val KEY_W = 0x1A
private const val KEY_A = 0x04
private const val KEY_S = 0x16
private const val KEY_D = 0x07
private const val KEY_C = 0x06
private const val KEY_E = 0x08
private const val KEY_R = 0x15
private const val KEY_Q = 0x14
private const val KEY_F = 0x09
private const val MOUSE_PIXELS_PER_TICK = 16f

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
    var testing by remember { mutableStateOf(false) }
    var sent by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Refresh the report counters a few times a second.
    LaunchedEffect(Unit) {
        while (true) {
            sent = hid.gamepadSent
            failed = hid.gamepadFailed
            delay(400)
        }
    }

    var keysMode by remember { mutableStateOf(settings.gamepadKeys) }
    LaunchedEffect(Unit) { pad.switchMode(settings.gamepadKeys) }

    // The page never rotates the screen by itself. Leaving it puts the user's own rotation policy back, which
    // matters when the "Rotate to landscape" button below was used.
    DisposableEffect(Unit) {
        onDispose {
            pad.reset()
            (ctx as? ComponentActivity)?.let { applyOrientation(it, settings) }
        }
    }

    // In keys mode the right stick (and gyro) moves the mouse. The mouse needs a steady stream even while the stick
    // is held still, so this ticks about 125 times a second.
    LaunchedEffect(keysMode) {
        if (!keysMode) return@LaunchedEffect
        var remX = 0f
        var remY = 0f
        while (true) {
            val sx = pad.lookX() / 127f
            val sy = pad.lookY() / 127f
            // Squared response: fine control near the centre, fast at the edge.
            remX += sx * kotlin.math.abs(sx) * MOUSE_PIXELS_PER_TICK
            remY += sy * kotlin.math.abs(sy) * MOUSE_PIXELS_PER_TICK
            val ix = remX.toInt()
            val iy = remY.toInt()
            if (ix != 0 || iy != 0) {
                hid.moveMouse(ix, iy)
                remX -= ix
                remY -= iy
            }
            delay(8)
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
        // Rotation is the user's call: this only happens when they tap the button.
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.ScreenRotation, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Text("The gamepad needs landscape", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Turn your phone sideways if auto-rotate is on, or tap below to rotate just for the gamepad.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                androidx.compose.material3.Button(onClick = {
                    (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }) { Text("Rotate to landscape") }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (gyro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { gyro = !gyro }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Gyro aim", fontSize = 13.sp, color = if (gyro) Color.White else MaterialTheme.colorScheme.onSurface) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (keysMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            keysMode = !keysMode
                            settings.gamepadKeys = keysMode
                            pad.switchMode(keysMode)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Keys", fontSize = 13.sp, color = if (keysMode) Color.White else MaterialTheme.colorScheme.onSurface) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (testing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        // The self-test would type into whatever is focused on the computer, so it is gamepad-only.
                        .clickable(enabled = !testing && !keysMode && hid.state == HidController.State.Connected) {
                            scope.launch {
                                testing = true
                                runSelfTest(pad)
                                testing = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(if (testing) "Testing…" else "Test", fontSize = 13.sp, color = if (testing) Color.White else MaterialTheme.colorScheme.onSurface) }
            }
            // Live proof that reports are leaving the phone, to tell a link problem from a game that ignores the pad.
            val line = when {
                hid.state != HidController.State.Connected -> "Not connected. Connect on the Devices tab."
                keysMode -> "Keys mode: left stick WASD, right stick mouse, RT click, LT aim, A space, B C, X E, Y R"
                failed > 0 -> "Sending, but $failed reports failed ($sent ok)"
                else -> "Connected to ${hid.hostName} · $sent reports sent"
            }
            Text(line, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), maxLines = 3)
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

/**
 * Presses A, B, X and Y in turn, sweeps the left stick in a circle, then walks the D-pad around. Open the Windows
 * "Set up USB game controllers" page (Win+R, joy.cpl) or a browser gamepad tester to watch it respond.
 */
private suspend fun runSelfTest(pad: PadState) {
    for (bit in listOf(B_A, B_B, B_X, B_Y)) {
        pad.button(bit, true)
        delay(300)
        pad.button(bit, false)
        delay(150)
    }
    repeat(2) {
        for (i in 0 until 36) {
            val a = Math.toRadians(i * 10.0)
            pad.lx = (kotlin.math.cos(a) * 100).toInt()
            pad.ly = (kotlin.math.sin(a) * 100).toInt()
            pad.push()
            delay(25)
        }
    }
    pad.lx = 0
    pad.ly = 0
    pad.push()
    // Up, up-right, right, down-right, down, down-left, left, up-left.
    val dirs = listOf(
        booleanArrayOf(true, false, false, false), booleanArrayOf(true, true, false, false), booleanArrayOf(false, true, false, false),
        booleanArrayOf(false, true, true, false), booleanArrayOf(false, false, true, false), booleanArrayOf(false, false, true, true),
        booleanArrayOf(false, false, false, true), booleanArrayOf(true, false, false, true),
    )
    for (d in dirs) {
        pad.up = d[0]; pad.right = d[1]; pad.down = d[2]; pad.left = d[3]
        pad.push()
        delay(260)
    }
    pad.up = false; pad.right = false; pad.down = false; pad.left = false
    pad.push()
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
