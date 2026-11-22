package com.airmousepro.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.KeyEvent
import com.airmousepro.data.AppSettings
import com.airmousepro.hid.HidController
import com.airmousepro.hid.HidDescriptor
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod
import kotlin.math.abs

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Routes the phone's physical volume keys to whichever screen wants them (the air mouse uses them as buttons). */
object HardwareKeys {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null
    fun handle(e: KeyEvent): Boolean = handler?.invoke(e) ?: false
}

/**
 * Gyro pointer. The cursor follows where the phone's edge points, independent of how the phone is rolled:
 * gravity gives "up", so turning left/right is measured around the vertical axis and tilting up/down around the
 * horizontal axis perpendicular to the pointing edge.
 */
class AirMouseEngine(context: Context, private val hid: HidController, private val settings: AppSettings) : SensorEventListener {
    @Volatile var active = true
    /** A finger is on the screen: the pointer holds still so a tap never moves the cursor. */
    @Volatile var touching = false
    /** The finger (or a volume key) has been held long enough to drag: the pointer moves again. */
    @Volatile var dragging = false
    @Volatile var onToggleRequest: (() -> Unit)? = null
    /** Asked on every gyro sample: true while the phone is controlling a TV. */
    @Volatile var tvMode: (() -> Boolean)? = null

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasGyro get() = gyro != null

    private val gravity = floatArrayOf(0f, 0f, 9.81f)
    private val bias = FloatArray(3)
    private var stillSamples = 0
    private var lastNs = 0L
    private var lastSendNs = 0L
    @Volatile private var freezeUntilNs = 0L
    private var remX = 0f
    private var remY = 0f

    private var calibrateUntilNs = 0L
    private val calSum = FloatArray(3)
    private var calCount = 0

    private var lastShakeNs = 0L
    private var shakeCount = 0
    private var lastTwistNs = 0L
    private var lastFlickNs = 0L

    private var thread: HandlerThread? = null

    fun start() {
        // Sensor samples are handled on their own thread. On the main thread they queue up behind screen drawing, and
        // the pointer stutters whenever the interface is busy.
        val t = HandlerThread("air-sensors", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        thread = t
        val h = Handler(t.looper)
        gyro?.let { sm.registerListener(this, it, SAMPLE_US, h) }
        // TYPE_GRAVITY is the fused "which way is up"; plain accelerometer is the fallback and the shake detector.
        gravitySensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, h) }
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, h) }
    }

    fun stop() {
        sm.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        lastNs = 0L
    }

    fun freezeFor(ms: Long) {
        freezeUntilNs = SystemClock.elapsedRealtimeNanos() + ms * 1_000_000L
    }

    /** Hold the phone still for half a second; the resting gyro reading becomes the new zero. */
    fun zeroDrift() {
        calSum.fill(0f)
        calCount = 0
        calibrateUntilNs = SystemClock.elapsedRealtimeNanos() + 600_000_000L
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> System.arraycopy(e.values, 0, gravity, 0, 3)
            Sensor.TYPE_ACCELEROMETER -> {
                if (gravitySensor == null) for (i in 0..2) gravity[i] = gravity[i] * 0.9f + e.values[i] * 0.1f
                if (settings.airShake) detectShake(e)
            }
            Sensor.TYPE_GYROSCOPE -> onGyro(e)
        }
    }

    private fun detectShake(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (g < SHAKE_G || e.timestamp - lastShakeNs < 120_000_000L) return
        shakeCount = if (e.timestamp - lastShakeNs < 800_000_000L) shakeCount + 1 else 1
        lastShakeNs = e.timestamp
        if (shakeCount >= 2) {
            shakeCount = 0
            onToggleRequest?.invoke()
        }
    }

    private fun onGyro(e: SensorEvent) {
        val dt = if (lastNs == 0L) 0f else (e.timestamp - lastNs) / 1e9f
        lastNs = e.timestamp
        if (dt <= 0f || dt > 0.1f) return

        // Collect the resting reading while calibrating.
        if (e.timestamp < calibrateUntilNs) {
            for (i in 0..2) calSum[i] += e.values[i]
            calCount++
            return
        }
        if (calCount > 0) {
            for (i in 0..2) bias[i] = calSum[i] / calCount
            calCount = 0
        }

        val wx = e.values[0] - bias[0]
        val wy = e.values[1] - bias[1]
        val wz = e.values[2] - bias[2]
        val speed = sqrt(wx * wx + wy * wy + wz * wz)

        // While the phone sits still, creep the bias toward the current reading so slow drift disappears.
        if (speed < STILL_RATE) {
            if (++stillSamples > 40) {
                for (i in 0..2) bias[i] += (e.values[i] - bias[i]) * 0.02f
            }
        } else {
            stillSamples = 0
        }

        // Pointing edge: 0 top, 1 right, 2 left.
        val (px, py) = when (settings.airPointing) {
            1 -> 1f to 0f
            2 -> -1f to 0f
            else -> 0f to 1f
        }

        if (settings.airTwist) detectTwist(e.timestamp, wx * px + wy * py)

        val gn = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).coerceAtLeast(0.001f)
        val ux = gravity[0] / gn
        val uy = gravity[1] / gn
        val uz = gravity[2] / gn

        // Right-hand axis = pointing x up. With p = (px, py, 0): (py*uz, -px*uz, px*uy - py*ux).
        var rx = py * uz
        var ry = -px * uz
        var rz = px * uy - py * ux
        val rn = sqrt(rx * rx + ry * ry + rz * rz)

        val yawRate = wx * ux + wy * uy + wz * uz
        val pitchRate = if (rn > 0.3f) (wx * rx + wy * ry + wz * rz) / rn else wx

        // A TV has no pointer to steer, so a quick flick of the wrist presses an arrow key instead.
        if (tvMode?.invoke() == true) {
            tvFlick(e.timestamp, yawRate, pitchRate)
            return
        }

        // ---- The pointer model ----
        // Built the way well-regarded gyro pointing is built (LG's Magic Remote, and the gyro-aiming practice documented
        // on GyroWiki): everything is in real-world degrees, a fixed turn of the wrist always covers the same part of the
        // screen, and filtering only touches the slow, tiny movements where hand tremor lives, never quick sweeps.
        val yawDeg = yawRate * RAD2DEG
        val pitchDeg = pitchRate * RAD2DEG
        val aimSpeed = sqrt(yawDeg * yawDeg + pitchDeg * pitchDeg)

        // How much this hand shakes when it is holding still, learned as it goes. Everything below adapts to it, so a
        // steadier hand gets finer control and a shakier one gets more tremor rejection.
        if (aimSpeed < TREMOR_SAMPLE_DEG) tremor += (aimSpeed - tremor) * 0.01f
        val calm = (tremor * TREMOR_MARGIN).coerceIn(TIGHT_MIN_DEG, TIGHT_MAX_DEG)

        // 1. Tightening: below the calm speed, movement fades out with the square of its speed, so the small fast
        //    wobbles of a held hand are almost cancelled while a deliberate slow move still gets through.
        val tight = if (aimSpeed < calm) (aimSpeed / calm).let { it * it } else 1f
        val tx = yawDeg * tight
        val ty = pitchDeg * tight

        // 2. Soft tiered smoothing: slow movement is averaged over the last few samples, fast movement passes straight
        //    through, and speeds in between blend the two. Precise aiming is steady and sweeps have no lag.
        ringX[ringI] = tx
        ringY[ringI] = ty
        ringI = (ringI + 1) % SMOOTH_N
        val avgX = ringX.sum() / SMOOTH_N
        val avgY = ringY.sum() / SMOOTH_N
        val direct = ((aimSpeed - SMOOTH_LO_DEG) / (SMOOTH_HI_DEG - SMOOTH_LO_DEG)).coerceIn(0f, 1f)
        val sx = tx * direct + avgX * (1f - direct)
        val sy = ty * direct + avgY * (1f - direct)

        // 3. Sensitivity in screen widths per degree. At the default, about 38 degrees of turn crosses the screen, close
        //    to how far a TV air remote is turned. Optional gentle acceleration makes slow turns finer and quick turns
        //    go further, without changing which way or when the pointer moves.
        val perDegree = settings.airSensitivity / DEG_PER_WIDTH
        val accel = if (settings.airAccel) {
            ACC_SLOW + (ACC_FAST - ACC_SLOW) * ((aimSpeed - ACC_LO_DEG) / (ACC_HI_DEG - ACC_LO_DEG)).coerceIn(0f, 1f)
        } else {
            1f
        }
        // Turning right is a negative yaw rate and tilting up a positive pitch rate, hence the minus signs. The screen
        // is about 16:9, so the same angle is a larger share of its height than of its width.
        var dW = -sx * dt * perDegree * accel
        var dH = -sy * dt * perDegree * accel * ASPECT

        // 4. Stillness lock, the part that makes a TV air remote feel planted: once the phone has been calm for a
        //    moment the cursor locks in place, and small wobbles add up inside a tiny circle without moving it. Only a
        //    move that leaves the circle (or a clearly quick turn) releases it, and then the cursor starts from where
        //    it was, minus the circle, so there is no jump.
        val now = e.timestamp
        if (aimSpeed < calm) {
            if (calmSince == 0L) calmSince = now
            if (!locked && now - calmSince > LOCK_AFTER_NS) {
                locked = true
                pendW = 0f
                pendH = 0f
            }
        } else {
            calmSince = 0L
        }
        if (locked) {
            pendW += dW
            pendH += dH / ASPECT
            val r = sqrt(pendW * pendW + pendH * pendH)
            val radius = LOCK_RADIUS_DEG * perDegree
            if (r > radius || aimSpeed > calm * LOCK_BREAK_FACTOR) {
                locked = false
                val k = if (r > radius) (r - radius) / r else 0f
                dW = pendW * k
                dH = pendH * k * ASPECT
                pendW = 0f
                pendH = 0f
            } else {
                dW = 0f
                dH = 0f
            }
        }

        val frozen = !active || (touching && !dragging) || now < freezeUntilNs
        val absolute = settings.airAbsolute && hid.target in 0..2
        if (frozen) {
            remX = 0f
            remY = 0f
        } else if (absolute) {
            // The cursor is kept here as a position on the screen. Pushing past an edge just holds it at the edge, and
            // coming back moves it at once, which also quietly lines the pointer back up with where the phone points.
            posX = (posX + dW).coerceIn(0f, 1f)
            posY = (posY + dH).coerceIn(0f, 1f)
            val ax = (posX * HidDescriptor.ABS_MAX).roundToInt()
            val ay = (posY * HidDescriptor.ABS_MAX).roundToInt()
            if ((ax != lastAbsX || ay != lastAbsY) && now - lastSendNs >= MIN_SEND_NS) {
                hid.pointTo(ax, ay)
                lastAbsX = ax
                lastAbsY = ay
                lastSendNs = now
                recordAbs(now)
            }
        } else {
            remX += dW * REL_COUNTS_WIDE
            remY += dH * REL_COUNTS_HIGH
            if (now - lastSendNs >= MIN_SEND_NS) {
                val ix = remX.roundToInt()
                val iy = remY.roundToInt()
                if (ix != 0 || iy != 0) {
                    hid.moveMouse(ix, iy)
                    record(now, ix, iy)
                    remX -= ix
                    remY -= iy
                    lastSendNs = now
                }
            }
        }

        AirTrace.sample(
            now, dt, yawDeg, pitchDeg, aimSpeed, dW, dH, frozen, absolute, locked, calm,
            if (absolute) posX else Float.NaN, if (absolute) posY else Float.NaN,
            sqrt(bias[0] * bias[0] + bias[1] * bias[1] + bias[2] * bias[2]) * RAD2DEG,
        )
    }

    // Tremor learning and the stillness lock.
    private var tremor = 2f
    private var calmSince = 0L
    private var locked = false
    private var pendW = 0f
    private var pendH = 0f

    // Recent tremor-filter input, for the tiered smoothing.
    private val ringX = FloatArray(SMOOTH_N)
    private val ringY = FloatArray(SMOOTH_N)
    private var ringI = 0

    // Absolute mode: the cursor's place on the screen, 0..1 across and down. It starts in the middle.
    private var posX = 0.5f
    private var posY = 0.5f
    private var lastAbsX = -1
    private var lastAbsY = -1

    /** Puts the cursor in the middle of the screen. Used by the re-centre button in absolute mode. */
    fun recenter() {
        if (!(settings.airAbsolute && hid.target in 0..2)) return
        posX = 0.5f
        posY = 0.5f
        lastAbsX = (posX * HidDescriptor.ABS_MAX).roundToInt()
        lastAbsY = lastAbsX
        hid.pointTo(lastAbsX, lastAbsY)
    }

    // The last moments of pointer movement, so a tap can undo the drift the thumb causes just before it lands.
    private val trailT = LongArray(TRAIL)
    private val trailX = IntArray(TRAIL)
    private val trailY = IntArray(TRAIL)
    private val trailAx = FloatArray(TRAIL)
    private val trailAy = FloatArray(TRAIL)
    private var trailN = 0

    private fun record(t: Long, x: Int, y: Int) = synchronized(trailT) {
        val i = trailN % TRAIL
        trailT[i] = t; trailX[i] = x; trailY[i] = y
        trailN++
    }

    private fun recordAbs(t: Long) = synchronized(trailT) {
        val i = trailN % TRAIL
        trailT[i] = t; trailAx[i] = posX; trailAy[i] = posY
        trailN++
    }

    /**
     * Called the instant a finger or a volume key goes down. Reaching for the screen tilts the phone, which nudges the
     * pointer just before the click. This takes back the last moments of movement so the click lands where you aimed.
     */
    fun beginClick() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (settings.airAbsolute && hid.target in 0..2) {
            var bx = Float.NaN
            var by = Float.NaN
            synchronized(trailT) {
                for (k in 1..minOf(trailN, TRAIL)) {
                    val i = (trailN - k) % TRAIL
                    if (now - trailT[i] > REWIND_NS) break
                    bx = trailAx[i]; by = trailAy[i]
                }
                trailN = 0
            }
            // A big movement was a deliberate sweep, not a nudge, so leave it alone.
            if (!bx.isNaN() && abs(bx - posX) <= REWIND_MAX_SHARE && abs(by - posY) <= REWIND_MAX_SHARE) {
                posX = bx
                posY = by
                lastAbsX = (posX * HidDescriptor.ABS_MAX).roundToInt()
                lastAbsY = (posY * HidDescriptor.ABS_MAX).roundToInt()
                hid.pointTo(lastAbsX, lastAbsY)
            }
            return
        }
        var sx = 0
        var sy = 0
        synchronized(trailT) {
            for (k in 1..minOf(trailN, TRAIL)) {
                val i = (trailN - k) % TRAIL
                if (now - trailT[i] > REWIND_NS) break
                sx += trailX[i]; sy += trailY[i]
            }
            trailN = 0
        }
        // A big movement was a deliberate sweep, not a nudge, so leave it alone.
        if ((sx != 0 || sy != 0) && abs(sx) <= REWIND_MAX_PX && abs(sy) <= REWIND_MAX_PX) hid.moveMouse(-sx, -sy)
    }

    /**
     * Flick right, left, up or down to move the TV's highlight one step. Turning right is a negative yaw rate. A short
     * rest after each flick stops the swing back from counting as a flick the other way. Sensitivity scales the speed
     * a flick has to reach. Nothing happens while a finger is on the screen or the pointer is paused.
     */
    private fun tvFlick(now: Long, yaw: Float, pitch: Float) {
        if (!active || touching || now - lastFlickNs < FLICK_REST_NS) return
        val needed = FLICK_RATE / settings.airSensitivity.coerceAtLeast(0.3f)
        val ay = abs(yaw)
        val ap = abs(pitch)
        if (ay < needed && ap < needed) return
        lastFlickNs = now
        if (ay >= ap) {
            hid.keyCombo(0, if (yaw < 0) Key.RIGHT else Key.LEFT)
        } else {
            hid.keyCombo(0, if (pitch > 0) Key.UP else Key.DOWN)
        }
    }

    /** A quick wrist roll clockwise goes Forward, anticlockwise goes Back. */
    private fun detectTwist(now: Long, rollRate: Float) {
        if (!active || abs(rollRate) < TWIST_RATE || now - lastTwistNs < 900_000_000L) return
        lastTwistNs = now
        hid.keyCombo(Mod.ALT, if (rollRate > 0) Key.RIGHT else Key.LEFT)
    }

    private companion object {
        const val SAMPLE_US = 8_000          // ask the gyro for about 125 Hz
        const val MIN_SEND_NS = 7_000_000L   // never send mouse reports faster than about 140 Hz
        const val STILL_RATE = 0.09f         // rad/s: a phone held in a hand is never perfectly still
        const val RAD2DEG = 57.29578f
        const val TREMOR_SAMPLE_DEG = 9f     // deg/s: slower than this counts toward learning the resting tremor
        const val TREMOR_MARGIN = 1.6f       // the calm speed sits this far above the resting tremor
        const val TIGHT_MIN_DEG = 2.5f       // deg/s: the calm speed never goes below this
        const val TIGHT_MAX_DEG = 8f         // or above this
        const val LOCK_AFTER_NS = 120_000_000L // calm this long and the cursor locks in place
        const val LOCK_RADIUS_DEG = 0.18f    // wobbles smaller than this turn never move a locked cursor
        const val LOCK_BREAK_FACTOR = 2.5f   // a turn this many times the calm speed releases the lock at once
        const val SMOOTH_N = 8               // samples averaged for slow movement, about 64 ms at 125 Hz
        const val SMOOTH_LO_DEG = 4f         // deg/s: fully smoothed below this
        const val SMOOTH_HI_DEG = 14f        // deg/s: not smoothed at all above this
        const val DEG_PER_WIDTH = 38f        // degrees of turn that cross the screen at sensitivity 1
        const val ASPECT = 16f / 9f          // a screen is this much wider than tall
        const val ACC_SLOW = 0.6f            // sensitivity multiplier for slow, careful turns
        const val ACC_FAST = 1.5f            // and for quick sweeps
        const val ACC_LO_DEG = 15f           // deg/s where acceleration starts
        const val ACC_HI_DEG = 140f          // deg/s where it tops out
        const val REL_COUNTS_WIDE = 1920f    // relative mode: mouse counts for one screen width
        const val REL_COUNTS_HIGH = 1080f
        const val REWIND_MAX_SHARE = 0.03f   // absolute mode: a click never undoes more than 3% of the screen
        const val TRAIL = 24
        const val REWIND_NS = 90_000_000L
        const val REWIND_MAX_PX = 70
        const val SHAKE_G = 22f              // m/s^2
        const val TWIST_RATE = 7f            // rad/s
        const val FLICK_RATE = 2.4f          // rad/s a wrist flick must reach to press an arrow on a TV
        const val FLICK_REST_NS = 380_000_000L
    }
}
