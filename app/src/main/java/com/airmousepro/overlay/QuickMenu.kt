package com.airmousepro.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import com.airmousepro.AirMouseApp
import com.airmousepro.MainActivity
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.HidController
import com.airmousepro.ui.AirMouseTheme
import com.airmousepro.ui.IconRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One entry on the dial: a built-in action or one of the user's shortcuts. */
data class DialItem(val id: String, val label: String, val icon: ImageVector)

/**
 * The floating quick menu: a small draggable bubble that opens a rotary dial.
 * It exists only while a device is connected, the setting is on, and "display over other apps" is allowed.
 */
class QuickMenu(private val app: AirMouseApp) {
    enum class Mode { Bubble, Dial, Pad }

    private val ctx: Context = app.applicationContext
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val settings = app.settings
    private val hid = app.hid

    private var root: OverlayRoot? = null
    private var owner: OverlayLifecycleOwner? = null
    private var lp: WindowManager.LayoutParams? = null
    private var job: Job? = null

    private var mode by mutableStateOf(Mode.Bubble)
    private var screenW by mutableIntStateOf(0)
    private var screenH by mutableIntStateOf(0)
    private var padX = 60
    private var padY = 240

    fun start() {
        if (job != null) return
        job = app.appScope.launch {
            combine(hid.stateFlow, settings.version) { s, _ -> s }.collect { refresh() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        hide()
    }

    fun canDraw() = Settings.canDrawOverlays(ctx)

    fun refresh() {
        val want = settings.quickMenu && canDraw() && hid.state == HidController.State.Connected
        if (want) show() else hide()
        if (want) applyBubbleSize()
    }

    // ---- Window management ----

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun measureScreen() {
        val dm = DisplayMetrics()
        val display = (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        display.getRealMetrics(dm)
        screenW = dm.widthPixels
        screenH = dm.heightPixels
    }

    private fun bubblePx() = dp(settings.bubbleSize.coerceIn(36, 80))

    private fun bubbleTop(size: Int) = (settings.bubbleY * screenH - size / 2f).roundToInt().coerceIn(0, (screenH - size).coerceAtLeast(0))

    private fun bubbleLeft(size: Int) = if (settings.bubbleSide == 0) 0 else (screenW - size).coerceAtLeast(0)

    private fun show() {
        if (root != null) return
        measureScreen()
        val lifecycleOwner = OverlayLifecycleOwner().also { it.create() }
        val rootView = OverlayRoot(ctx) { onConfigChanged() }
        lifecycleOwner.attachTo(rootView)
        val compose = ComposeView(ctx).apply {
            setContent { AirMouseTheme { QuickMenuContent() } }
        }
        rootView.addView(compose)

        val size = bubblePx()
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleLeft(size)
            y = bubbleTop(size)
        }
        runCatching { wm.addView(rootView, params) }.onFailure { return }
        lifecycleOwner.resume()
        root = rootView
        owner = lifecycleOwner
        lp = params
        mode = Mode.Bubble
    }

    private fun hide() {
        val r = root ?: return
        owner?.destroy()
        runCatching { wm.removeView(r) }
        root = null
        owner = null
        lp = null
        mode = Mode.Bubble
    }

    private fun update() {
        val r = root ?: return
        val p = lp ?: return
        runCatching { wm.updateViewLayout(r, p) }
    }

    private fun applyBubbleSize() {
        if (mode != Mode.Bubble) return
        val p = lp ?: return
        val size = bubblePx()
        if (p.width != size) {
            p.width = size
            p.height = size
            p.x = bubbleLeft(size)
            p.y = bubbleTop(size)
            update()
        }
    }

    private fun onConfigChanged() {
        measureScreen()
        val p = lp ?: return
        when (mode) {
            Mode.Bubble -> {
                val size = bubblePx()
                p.x = bubbleLeft(size)
                p.y = bubbleTop(size)
            }
            Mode.Dial -> { p.x = 0; p.y = 0 }
            Mode.Pad -> {
                p.x = padX.coerceIn(0, (screenW - p.width).coerceAtLeast(0))
                p.y = padY.coerceIn(0, (screenH - p.height).coerceAtLeast(0))
            }
        }
        update()
    }

    private fun switchTo(m: Mode) {
        val p = lp ?: return
        when (m) {
            Mode.Bubble -> {
                val size = bubblePx()
                p.width = size; p.height = size
                p.x = bubbleLeft(size); p.y = bubbleTop(size)
            }
            Mode.Dial -> {
                p.width = WindowManager.LayoutParams.MATCH_PARENT
                p.height = WindowManager.LayoutParams.MATCH_PARENT
                p.x = 0; p.y = 0
            }
            Mode.Pad -> {
                p.width = dp(300); p.height = dp(260)
                p.x = padX.coerceIn(0, (screenW - p.width).coerceAtLeast(0))
                p.y = padY.coerceIn(0, (screenH - p.height).coerceAtLeast(0))
            }
        }
        mode = m
        update()
    }

    private fun dragBubble(dx: Float, dy: Float) {
        val p = lp ?: return
        p.x = (p.x + dx).roundToInt().coerceIn(0, (screenW - p.width).coerceAtLeast(0))
        p.y = (p.y + dy).roundToInt().coerceIn(0, (screenH - p.height).coerceAtLeast(0))
        update()
    }

    /** Snap to the nearest screen edge and remember where the bubble lives. */
    private fun releaseBubble() {
        val p = lp ?: return
        val size = p.width
        val side = if (p.x + size / 2 < screenW / 2) 0 else 1
        settings.bubbleSide = side
        settings.bubbleY = ((p.y + size / 2f) / screenH).coerceIn(0.05f, 0.95f)
        val target = bubbleLeft(size)
        ValueAnimator.ofInt(p.x, target).apply {
            duration = 180
            addUpdateListener { p.x = it.animatedValue as Int; update() }
            start()
        }
    }

    private fun dragPad(dx: Float, dy: Float) {
        val p = lp ?: return
        padX = (p.x + dx).roundToInt().coerceIn(0, (screenW - p.width).coerceAtLeast(0))
        padY = (p.y + dy).roundToInt().coerceIn(0, (screenH - p.height).coerceAtLeast(0))
        p.x = padX
        p.y = padY
        update()
    }

    // ---- Dial contents ----

    private val builtIns = mapOf(
        "touchpad" to DialItem("b:touchpad", "Touchpad", Icons.Rounded.TouchApp),
        "keys" to DialItem("b:keys", "Keyboard", Icons.Rounded.Keyboard),
        "volume" to DialItem("b:volume", "Volume", Icons.Rounded.VolumeUp),
        "playpause" to DialItem("b:playpause", "Play / pause", Icons.Rounded.PlayArrow),
    )

    fun builtInItems(): List<DialItem> = builtIns.values.toList()

    fun itemsFor(slots: String): List<DialItem> = slots.split(',').filter { it.isNotBlank() }.mapNotNull { slot ->
        when {
            slot.startsWith("b:") -> builtIns[slot.removePrefix("b:")]
            slot.startsWith("s:") -> app.shortcuts.find(slot.removePrefix("s:"))?.let { DialItem(slot, it.name, IconRegistry.get(it.icon)) }
            else -> null
        }
    }

    private fun execute(item: DialItem) {
        when (item.id) {
            "b:touchpad" -> switchTo(Mode.Pad)
            "b:keys" -> {
                openApp(tab = 2)
                switchTo(Mode.Bubble)
            }
            "b:playpause" -> {
                hid.consumer(Consumer.PLAY_PAUSE)
                switchTo(Mode.Bubble)
            }
            else -> {
                if (item.id.startsWith("s:")) app.shortcuts.find(item.id.removePrefix("s:"))?.let { app.macros.run(it) }
                switchTo(Mode.Bubble)
            }
        }
    }

    private fun openApp(tab: Int) {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, tab),
        )
    }

    @androidx.compose.runtime.Composable
    private fun QuickMenuContent() {
        when (mode) {
            Mode.Bubble -> BubbleUi(
                sizeDp = settings.bubbleSize,
                opacity = settings.bubbleOpacity,
                onTap = { switchTo(Mode.Dial) },
                onDrag = ::dragBubble,
                onRelease = ::releaseBubble,
            )
            Mode.Dial -> DialUi(
                items = itemsFor(settings.dialSlots),
                side = settings.bubbleSide,
                cyFraction = settings.bubbleY,
                screenW = screenW,
                screenH = screenH,
                onExecute = ::execute,
                onVolume = { up -> hid.consumer(if (up) Consumer.VOL_UP else Consumer.VOL_DOWN) },
                onDismiss = { switchTo(Mode.Bubble) },
            )
            Mode.Pad -> PadUi(hid = hid, onDrag = ::dragPad, onClose = { switchTo(Mode.Bubble) })
        }
    }
}
