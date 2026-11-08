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
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod
import kotlin.math.abs
import kotlin.math.min
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
    private val filterX = AdaptiveFilter()
    private val filterY = AdaptiveFilter()
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

    /**
     * Smooths a turn rate with a filter whose strength depends on how fast the phone is turning (a "1 euro" filter).
     * Slow and steady: heavy smoothing, so hand tremor does not move the pointer. Fast: almost none, so there is no lag.
     */
    private class AdaptiveFilter {
        private var x = 0f
        private var dx = 0f
        private var primed = false

        fun filter(v: Float, dt: Float): Float {
            if (!primed) { primed = true; x = v; return v }
            val rate = (v - x) / dt
            dx += (rate - dx) * alpha(D_CUTOFF, dt)
            val cutoff = MIN_CUTOFF + BETA * abs(dx)
            x += (v - x) * alpha(cutoff, dt)
            return x
        }

        private fun alpha(cutoff: Float, dt: Float): Float {
            val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
            return 1f / (1f + tau / dt)
        }
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

        // Adaptive smoothing: a steady hand is filtered hard so the pointer sits still, and the filter opens up as soon as
        // the phone really moves, so quick sweeps do not lag. Then a soft dead zone removes the last of the tremor
        // without a jump when movement starts.
        val fx = filterX.filter(yawRate, dt)
        val fy = filterY.filter(pitchRate, dt)
        val ox = soft(fx, DEAD_ZONE)
        val oy = soft(fy, DEAD_ZONE)

        // Slow movement is damped for precision, fast movement is boosted to cross the screen. Only the aiming turn
        // counts: rolling the wrist must not speed the pointer up.
        val aim = sqrt(ox * ox + oy * oy)
        val boost = 0.55f + 1.1f * min(aim / 3f, 1f)
        val gain = BASE_GAIN * settings.airSensitivity * boost
        remX += -ox * dt * gain
        remY += -oy * dt * gain

        val now = e.timestamp
        val frozen = !active || (touching && !dragging) || now < freezeUntilNs
        if (frozen) {
            remX = 0f
            remY = 0f
            return
        }
        if (now - lastSendNs < MIN_SEND_NS) return
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

    private fun soft(v: Float, dead: Float): Float = when {
        v > dead -> v - dead
        v < -dead -> v + dead
        else -> 0f
    }

    // The last moments of pointer movement, so a tap can undo the drift the thumb causes just before it lands.
    private val trailT = LongArray(TRAIL)
    private val trailX = IntArray(TRAIL)
    private val trailY = IntArray(TRAIL)
    private var trailN = 0

    private fun record(t: Long, x: Int, y: Int) = synchronized(trailT) {
        val i = trailN % TRAIL
        trailT[i] = t; trailX[i] = x; trailY[i] = y
        trailN++
    }

    /**
     * Called the instant a finger or a volume key goes down. Reaching for the screen tilts the phone, which nudges the
     * pointer just before the click. This takes back the last moments of movement so the click lands where you aimed.
     */
    fun beginClick() {
        val now = SystemClock.elapsedRealtimeNanos()
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
        const val BASE_GAIN = 1000f          // counts per radian before sensitivity and boost
        const val DEAD_ZONE = 0.035f         // rad/s, hand tremor at rest
        const val STILL_RATE = 0.09f         // a phone held in a hand is never perfectly still
        const val MIN_CUTOFF = 3.2f          // Hz: smoothing when the phone is steady
        const val BETA = 0.4f                // how quickly smoothing opens up as turning speeds up
        const val D_CUTOFF = 1.5f
        const val TRAIL = 24
        const val REWIND_NS = 90_000_000L
        const val REWIND_MAX_PX = 70
        const val SHAKE_G = 22f              // m/s^2
        const val TWIST_RATE = 7f            // rad/s
        const val FLICK_RATE = 2.4f          // rad/s a wrist flick must reach to press an arrow on a TV
        const val FLICK_REST_NS = 380_000_000L
    }
}
