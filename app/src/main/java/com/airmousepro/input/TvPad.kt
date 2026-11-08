package com.airmousepro.input

import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.airmousepro.data.AppSettings
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.ui.Haptics
import kotlin.math.abs
import kotlin.math.hypot

private const val TAP_MS = 280L
private const val LONG_PRESS_MS = 450L

/**
 * The touch surface when the phone is controlling a TV. TVs are navigated by moving a highlight with the arrow keys,
 * so instead of a pointer this works like the touch strip on a TV remote:
 *  - one finger: every stretch of travel presses an arrow key, so a long swipe runs through a whole row or list
 *  - tap: OK. Press and hold: a long press on OK, which opens context menus
 *  - two fingers swiping up or down: volume. Sideways: rewind and fast forward
 *  - two-finger tap: Back. Three-finger tap: Home
 */
fun Modifier.tvPad(hid: HidController, settings: AppSettings, view: View): Modifier = pointerInput(Unit) {
    val density = this.density
    val slop = viewConfiguration.touchSlop * 1.4f
    val stepH = 46f * density      // one arrow press per this much sideways travel
    val stepV = 42f * density      // and per this much vertical travel
    val volumeStep = 38f * density
    val seekStep = 56f * density

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val t0 = down.uptimeMillis
        var maxPointers = 1
        var total = Offset.Zero
        var axis = 0                // 0 undecided, 1 sideways, 2 vertical. Locked for the rest of the stroke.
        var acc = 0f
        var steps = 0
        var held = false
        var total2 = Offset.Zero
        var axis2 = 0
        var acc2 = 0f
        var steps2 = 0
        var lastTime = t0

        while (true) {
            // A finger that rests without moving becomes a long press on OK.
            val event = if (maxPointers == 1 && axis == 0 && !held) {
                val left = LONG_PRESS_MS - (SystemClock.uptimeMillis() - t0)
                if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                held = true
                hid.keyDown(0, Key.ENTER)
                Haptics.tick(view, settings)
                continue
            }

            val pressed = event.changes.filter { it.pressed }
            if (pressed.size > maxPointers) maxPointers = pressed.size
            lastTime = event.changes.maxOf { it.uptimeMillis }
            if (pressed.isEmpty()) {
                event.changes.forEach { it.consume() }
                break
            }

            var dx = 0f
            var dy = 0f
            pressed.forEach { val c = it.positionChange(); dx += c.x; dy += c.y }
            val d = Offset(dx / pressed.size, dy / pressed.size)

            if (maxPointers == 1) {
                total += d
                if (axis == 0) {
                    if (hypot(total.x, total.y) > slop) {
                        axis = if (abs(total.x) >= abs(total.y)) 1 else 2
                        acc = if (axis == 1) total.x else total.y
                    }
                } else {
                    acc += if (axis == 1) d.x else d.y
                }
                if (axis != 0) {
                    val step = if (axis == 1) stepH else stepV
                    while (abs(acc) >= step) {
                        val forward = acc > 0
                        acc -= if (forward) step else -step
                        steps++
                        arrow(hid, settings, view, axis, forward)
                    }
                }
            } else if (pressed.size >= 2) {
                total2 += d
                if (axis2 == 0) {
                    if (hypot(total2.x, total2.y) > slop) {
                        axis2 = if (abs(total2.x) >= abs(total2.y)) 1 else 2
                        acc2 = if (axis2 == 1) total2.x else total2.y
                    }
                } else {
                    acc2 += if (axis2 == 1) d.x else d.y
                }
                if (axis2 != 0) {
                    val step = if (axis2 == 1) seekStep else volumeStep
                    while (abs(acc2) >= step) {
                        val forward = acc2 > 0
                        acc2 -= if (forward) step else -step
                        steps2++
                        Haptics.tick(view, settings)
                        if (axis2 == 1) {
                            hid.consumer(if (forward) Consumer.FAST_FORWARD else Consumer.REWIND)
                        } else {
                            // Fingers moving up raise the volume, like pushing a slider up.
                            hid.consumer(if (forward) Consumer.VOL_DOWN else Consumer.VOL_UP)
                        }
                    }
                }
            }
            event.changes.forEach { it.consume() }
        }

        if (held) hid.keyUp()

        // A short flick that did not travel a full step still counts as one press, so quick flicks feel responsive.
        if (maxPointers == 1 && axis != 0 && steps == 0 && abs(acc) > 16f * density) {
            arrow(hid, settings, view, axis, acc > 0)
        }

        val moved = axis != 0 || axis2 != 0
        if (!held && !moved && lastTime - t0 < TAP_MS) {
            Haptics.tap(view, settings)
            when (maxPointers) {
                1 -> hid.keyCombo(0, Key.ENTER)
                2 -> hid.consumer(Consumer.BACK)
                else -> hid.consumer(Consumer.HOME)
            }
        }
    }
}

private fun arrow(hid: HidController, settings: AppSettings, view: View, axis: Int, forward: Boolean) {
    Haptics.tick(view, settings)
    hid.keyCombo(
        0,
        if (axis == 1) (if (forward) Key.RIGHT else Key.LEFT) else (if (forward) Key.DOWN else Key.UP),
    )
}
