package com.airmousepro.input

import java.util.Locale

/**
 * A running summary of how the air mouse is behaving, shown in the developer log so it can be copied with everything
 * else. Nothing is written to a file. The link to the computer only goes one way, so the phone reports what it sent:
 * in "point where you aim" mode that is the cursor's position on the screen.
 *
 * Each sweep (a stretch of continuous movement) is summarised: how long it took, how far the phone turned, its peak
 * speed, and how much of the screen the cursor crossed. That is enough to see whether a turn moves the cursor too
 * far, too little, or unevenly.
 */
object AirTrace {
    private const val MOVING_DPS = 6f        // faster than this counts as moving
    private const val SWEEP_END_NS = 150_000_000L
    private const val KEEP_SWEEPS = 6

    @Volatile private var samples = 0L
    @Volatile private var firstNs = 0L
    @Volatile private var lastNs = 0L
    @Volatile private var restSpeed = 0f
    @Volatile private var peakSpeed = 0f
    @Volatile private var frozenSamples = 0L
    @Volatile private var lockedSamples = 0L
    @Volatile private var calm = 0f
    @Volatile private var absolute = false
    @Volatile private var posX = Float.NaN
    @Volatile private var posY = Float.NaN
    @Volatile private var biasDps = 0f

    // The sweep being measured.
    private var inSweep = false
    private var sweepStartNs = 0L
    private var lastMoveNs = 0L
    private var sweepYaw = 0f
    private var sweepPitch = 0f
    private var sweepW = 0f
    private var sweepH = 0f
    private var sweepPeak = 0f
    private val sweeps = ArrayDeque<String>()

    fun reset() = synchronized(this) {
        samples = 0; firstNs = 0; lastNs = 0; restSpeed = 0f; peakSpeed = 0f; frozenSamples = 0; lockedSamples = 0
        inSweep = false; sweeps.clear()
    }

    /** Called for every gyro sample, on the sensor thread. Angles in degrees per second, screen moves as shares of it. */
    fun sample(
        now: Long, dt: Float, yawDps: Float, pitchDps: Float, speed: Float, dW: Float, dH: Float,
        frozen: Boolean, abs: Boolean, lock: Boolean, calmDps: Float, x: Float, y: Float, bias: Float,
    ) = synchronized(this) {
        if (firstNs == 0L) firstNs = now
        lastNs = now
        samples++
        if (frozen) frozenSamples++
        if (lock) lockedSamples++
        calm = calmDps
        absolute = abs
        posX = x
        posY = y
        biasDps = bias
        if (speed > peakSpeed) peakSpeed = speed
        if (speed < MOVING_DPS) restSpeed += (speed - restSpeed) * 0.02f

        if (speed >= MOVING_DPS && !frozen) {
            if (!inSweep) {
                inSweep = true
                sweepStartNs = now
                sweepYaw = 0f; sweepPitch = 0f; sweepW = 0f; sweepH = 0f; sweepPeak = 0f
            }
            lastMoveNs = now
        }
        if (inSweep) {
            sweepYaw += yawDps * dt
            sweepPitch += pitchDps * dt
            if (!frozen) { sweepW += dW; sweepH += dH }
            if (speed > sweepPeak) sweepPeak = speed
            if (now - lastMoveNs > SWEEP_END_NS) {
                inSweep = false
                val ms = (lastMoveNs - sweepStartNs) / 1_000_000
                sweeps.addLast(
                    String.format(
                        Locale.US, "%4d ms  turned %5.1f° across, %5.1f° up/down  peak %4.0f°/s  cursor moved %5.1f%% across, %5.1f%% up/down",
                        ms, -sweepYaw, sweepPitch, sweepPeak, sweepW * 100, -sweepH * 100,
                    ),
                )
                while (sweeps.size > KEEP_SWEEPS) sweeps.removeFirst()
            }
        }
    }

    fun report(sensitivity: Float, accel: Boolean): String = synchronized(this) {
        buildString {
            appendLine("--- Air mouse ---")
            if (samples == 0L) {
                appendLine("Not used since the app started.")
                return@buildString
            }
            val secs = (lastNs - firstNs) / 1e9
            appendLine(
                String.format(
                    Locale.US, "Gyro samples: %d over %.0f s (%.0f a second), paused %.0f%% of the time",
                    samples, secs, if (secs > 0) samples / secs else 0.0, 100.0 * frozenSamples / samples,
                ),
            )
            appendLine(
                String.format(
                    Locale.US, "Hand at rest: %.2f°/s, drift correction %.2f°/s, fastest turn %.0f°/s",
                    restSpeed, biasDps, peakSpeed,
                ),
            )
            appendLine(
                String.format(
                    Locale.US, "Tremor filter: calm below %.1f°/s, cursor held by the stillness lock %.0f%% of the time",
                    calm, 100.0 * lockedSamples / samples,
                ),
            )
            appendLine(
                String.format(
                    Locale.US, "Mode: %s, sensitivity %.2f (%.0f° crosses the screen), acceleration %s",
                    if (absolute) "point where you aim" else "mouse movement", sensitivity, 38f / sensitivity, if (accel) "on" else "off",
                ),
            )
            if (absolute && !posX.isNaN()) {
                appendLine(String.format(Locale.US, "Cursor now: %.1f%% across, %.1f%% down", posX * 100, posY * 100))
            }
            if (sweeps.isEmpty()) appendLine("No sweeps yet.") else {
                appendLine("Last sweeps (newest last):")
                sweeps.forEach { appendLine("  $it") }
            }
        }
    }
}
