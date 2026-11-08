package com.airmousepro.input

import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.airmousepro.data.AppSettings
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod
import com.airmousepro.ui.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val TAP_MS = 280L
private const val DRAG_WINDOW_MS = 320L
private const val PX_PER_NOTCH = 22f           // two-finger scroll distance for one wheel notch
private const val PX_PER_ZOOM_NOTCH = 36f      // pinch distance for one Ctrl+wheel notch
private const val FLING_MIN_PX_PER_MS = 0.35f
private const val OS_ANDROID_TV = 3

private enum class Mode { Undecided, Scroll, Zoom, Swipe3 }

/**
 * Laptop-style trackpad:
 *  - one finger moves, a quick tap clicks, tap-then-drag drags
 *  - two fingers scroll in both axes, pinch zooms (Ctrl+wheel), a two-finger tap right-clicks
 *  - three fingers swipe for task view / desktop / app switch, a three-finger tap middle-clicks
 */
fun Modifier.trackpad(
    hid: HidController,
    settings: AppSettings,
    view: View,
    scope: CoroutineScope,
    speed: () -> Float,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop * 1.6f
    val moveDeadzone = viewConfiguration.touchSlop * 0.5f
    val density = this.density
    var lastTapUp = 0L
    var remX = 0f
    var remY = 0f
    var fling: Job? = null

    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        fling?.cancel()
        val t0 = first.uptimeMillis
        val startPos = HashMap<PointerId, Offset>()
        startPos[first.id] = first.position

        // Touching again soon after a tap starts a drag, like a laptop trackpad.
        val dragging = settings.tapToClick && t0 - lastTapUp < DRAG_WINDOW_MS
        if (dragging) {
            hid.setButton(HidController.BUTTON_LEFT, true)
            Haptics.tick(view, settings)
        }

        var maxPointers = 1
        var maxMoved = 0f
        var mode = Mode.Undecided
        var pointerArmed = dragging           // single-finger motion starts after a tiny dead zone
        var lastCentroid = first.position
        var lastSpread = 0f
        var scrollAccX = 0f
        var scrollAccY = 0f
        var zoomAcc = 0f
        var zoomHeld = false
        var swipeAcc = Offset.Zero
        var swipeDone = false
        var lastEventTime = t0
        // Velocity of the last ~80 ms of two-finger movement, for inertia.
        var velX = 0f
        var velY = 0f
        var velTime = t0

        var swipeInit = false
        var prevMoveTime = t0
        var fingerSpeed = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            event.changes.forEach { if (it.pressed && it.id !in startPos) startPos[it.id] = it.position }
            if (pressed.size > maxPointers) maxPointers = pressed.size
            lastEventTime = event.changes.maxOf { it.uptimeMillis }
            pressed.forEach { ch ->
                val d = ch.position - (startPos[ch.id] ?: ch.position)
                maxMoved = maxOf(maxMoved, hypot(d.x, d.y))
            }
            if (pressed.isEmpty()) {
                event.changes.forEach { it.consume() }
                break
            }

            when {
                maxPointers == 1 -> {
                    val d = pressed[0].positionChange()
                    remX += d.x
                    remY += d.y
                    // Finger speed in pixels per millisecond, smoothed. Events arrive unevenly, so speed measured per
                    // event would make the same stroke accelerate differently from moment to moment.
                    val dt = (lastEventTime - prevMoveTime).coerceAtLeast(1L)
                    prevMoveTime = lastEventTime
                    fingerSpeed = fingerSpeed * 0.65f + (hypot(d.x, d.y) / dt) * 0.35f
                    // Ignore the first few pixels so a tap never nudges the cursor.
                    if (!pointerArmed && maxMoved > moveDeadzone) pointerArmed = true
                    if (pointerArmed) {
                        val gain = speed() * curve(fingerSpeed, settings.accelPreset)
                        val ix = (remX * gain).roundToInt()
                        val iy = (remY * gain).roundToInt()
                        if (ix != 0 || iy != 0) {
                            hid.moveMouse(ix, iy)
                            remX -= ix / gain
                            remY -= iy / gain
                        }
                    }
                }

                pressed.size == 2 && maxPointers == 2 -> {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    val spread = hypot(a.x - b.x, a.y - b.y)
                    if (lastSpread == 0f) lastSpread = spread
                    val dC = centroid - lastCentroid
                    val dS = spread - lastSpread
                    lastCentroid = centroid
                    lastSpread = spread

                    if (mode == Mode.Undecided && maxMoved > slop) {
                        // Fingers moving apart or together mean zoom; fingers moving together in one direction mean scroll.
                        mode = if (settings.pinchZoom && abs(dS) > hypot(dC.x, dC.y) * 1.4f && abs(dS) > 1.5f) Mode.Zoom else Mode.Scroll
                    }
                    when (mode) {
                        Mode.Scroll -> {
                            val s = settings.scrollSpeed
                            scrollAccX += dC.x * s
                            scrollAccY += dC.y * s
                            // Natural: content follows the fingers, so fingers down = wheel up and fingers right = pan left.
                            val sgn = if (settings.naturalScroll) 1 else -1
                            val vy = (scrollAccY / PX_PER_NOTCH).toInt()
                            val vx = (scrollAccX / PX_PER_NOTCH).toInt()
                            if (vy != 0 || vx != 0) {
                                hid.scroll(vy * sgn, -vx * sgn)
                                scrollAccY -= vy * PX_PER_NOTCH
                                scrollAccX -= vx * PX_PER_NOTCH
                            }
                            val dt = (lastEventTime - velTime).coerceAtLeast(1)
                            velX = velX * 0.6f + (dC.x / dt) * 0.4f
                            velY = velY * 0.6f + (dC.y / dt) * 0.4f
                            velTime = lastEventTime
                        }
                        Mode.Zoom -> {
                            if (!zoomHeld) {
                                hid.keyDown(Mod.CTRL)
                                zoomHeld = true
                            }
                            zoomAcc += dS
                            val n = (zoomAcc / PX_PER_ZOOM_NOTCH).toInt()
                            if (n != 0) {
                                hid.scroll(n)
                                zoomAcc -= n * PX_PER_ZOOM_NOTCH
                            }
                        }
                        else -> {}
                    }
                }

                pressed.size == 3 && settings.threeFingerGestures -> {
                    val cx = pressed.map { it.position.x }.average().toFloat()
                    val cy = pressed.map { it.position.y }.average().toFloat()
                    val c = Offset(cx, cy)
                    if (!swipeInit) {
                        swipeInit = true
                        lastCentroid = c
                    }
                    swipeAcc += c - lastCentroid
                    lastCentroid = c
                    val need = 64f * density
                    if (!swipeDone && (abs(swipeAcc.x) > need || abs(swipeAcc.y) > need)) {
                        swipeDone = true
                        mode = Mode.Swipe3
                        Haptics.tick(view, settings)
                        val dir = if (abs(swipeAcc.y) >= abs(swipeAcc.x)) {
                            if (swipeAcc.y < 0) SwipeDir.Up else SwipeDir.Down
                        } else {
                            if (swipeAcc.x < 0) SwipeDir.Left else SwipeDir.Right
                        }
                        threeFingerAction(hid, settings.os, dir)
                    }
                }
            }
            event.changes.forEach { it.consume() }
        }

        if (zoomHeld) hid.keyUp()

        val duration = lastEventTime - t0
        when {
            dragging -> {
                hid.setButton(HidController.BUTTON_LEFT, false)
                lastTapUp = 0L
            }
            mode == Mode.Undecided && duration < TAP_MS && maxMoved < slop && settings.tapToClick -> {
                Haptics.tap(view, settings)
                when (maxPointers) {
                    1 -> {
                        hid.click(HidController.BUTTON_LEFT)
                        lastTapUp = lastEventTime
                    }
                    2 -> hid.click(HidController.BUTTON_RIGHT)
                    else -> hid.click(HidController.BUTTON_MIDDLE)
                }
            }
            mode == Mode.Scroll && settings.inertia -> {
                val vx = velX
                val vy = velY
                if (hypot(vx, vy) > FLING_MIN_PX_PER_MS) {
                    fling = scope.launch {
                        var x = vx * settings.scrollSpeed
                        var y = vy * settings.scrollSpeed
                        var accX = 0f
                        var accY = 0f
                        val sgn = if (settings.naturalScroll) 1 else -1
                        while (isActive && hypot(x, y) > 0.06f) {
                            accX += x * 16f
                            accY += y * 16f
                            val ny = (accY / PX_PER_NOTCH).toInt()
                            val nx = (accX / PX_PER_NOTCH).toInt()
                            if (ny != 0 || nx != 0) {
                                hid.scroll(ny * sgn, -nx * sgn)
                                accY -= ny * PX_PER_NOTCH
                                accX -= nx * PX_PER_NOTCH
                            }
                            x *= 0.94f
                            y *= 0.94f
                            delay(16)
                        }
                    }
                }
            }
        }
    }
}

/** Pointer gain from finger speed in pixels per millisecond: slow strokes stay precise, quick strokes cover distance. */
private fun curve(speed: Float, preset: Int): Float = when (preset) {
    0 -> 0.8f + (speed / 1.8f).coerceAtMost(0.8f)
    2 -> 1.3f + (speed / 0.7f).coerceAtMost(3.5f)
    else -> 1f + (speed / 1.08f).coerceAtMost(2.2f)
}

enum class SwipeDir { Up, Down, Left, Right }

/** Three-finger swipes mapped to the target operating system's own gestures. */
fun threeFingerAction(hid: HidController, os: Int, dir: SwipeDir) {
    if (os == OS_ANDROID_TV) return
    val d = 0x07
    val tab = Key.TAB
    when (os) {
        1 -> when (dir) { // macOS: Mission Control, App Exposé, spaces
            SwipeDir.Up -> hid.keyCombo(Mod.CTRL, Key.UP)
            SwipeDir.Down -> hid.keyCombo(Mod.CTRL, Key.DOWN)
            SwipeDir.Left -> hid.keyCombo(Mod.CTRL, Key.RIGHT)
            SwipeDir.Right -> hid.keyCombo(Mod.CTRL, Key.LEFT)
        }
        2 -> when (dir) { // Linux (GNOME)
            SwipeDir.Up -> hid.keyCombo(Mod.GUI)
            SwipeDir.Down -> hid.keyCombo(Mod.GUI, d)
            SwipeDir.Left -> hid.keyCombo(Mod.CTRL or Mod.ALT, Key.RIGHT)
            SwipeDir.Right -> hid.keyCombo(Mod.CTRL or Mod.ALT, Key.LEFT)
        }
        else -> when (dir) { // Windows: task view, show desktop, last app
            SwipeDir.Up -> hid.keyCombo(Mod.GUI, tab)
            SwipeDir.Down -> hid.keyCombo(Mod.GUI, d)
            SwipeDir.Left, SwipeDir.Right -> hid.keyCombo(Mod.ALT, tab)
        }
    }
}
