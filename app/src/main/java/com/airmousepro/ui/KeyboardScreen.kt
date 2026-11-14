package com.airmousepro.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.app
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController
import com.airmousepro.hid.KbLayout
import com.airmousepro.hid.Key
import com.airmousepro.hid.KeyMap
import com.airmousepro.hid.Mod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Key height chosen by the landscape layout. Unspecified means the usual portrait and fallback sizes. */
private val LocalKeyHeight = compositionLocalOf { Dp.Unspecified }

private const val REPEAT_DELAY_MS = 380L
private const val REPEAT_RATE_MS = 55L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardScreen(hid: HidController) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val view = LocalView.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val spoken = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) ctx.app.macros.typeText(spoken)
    }

    val landscape = isLandscape()
    // "More" (navigation, shortcuts and function keys) only exists in landscape. After a rotation back to portrait the
    // keys come up instead.
    val shown = if (!landscape && mode == 2) 0 else mode

    val toolbar = @Composable {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(if (landscape) listOf("Keys", "Text", "More") else listOf("Keys", "Text"), shown, { mode = it }, Modifier.weight(1f))
            ToolButton(Icons.Rounded.Mic, "Voice typing") {
                try {
                    speech.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(ctx, "No speech recogniser on this phone", Toast.LENGTH_SHORT).show()
                }
            }
            if (hid.typingPending > 0) {
                // While text is still going out, the paste button becomes Stop. One tap drops what is queued.
                ToolButton(Icons.Rounded.Stop, "Stop typing") { ctx.app.macros.stop() }
            } else ToolButton(Icons.Rounded.ContentPaste, "Type clipboard") {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (clip.isNullOrEmpty()) {
                    Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                } else {
                    Haptics.tap(view, settings)
                    ctx.app.macros.typeText(clip.take(2000))
                }
            }
            // The layout of the computer being typed on; tap to cycle.
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) { detectTapGestures { settings.kbLayout = (settings.kbLayout + 1) % KbLayout.values().size } }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) { Text(hid.layout().label, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }

    val keysPane = @Composable { fieldModifier: Modifier ->
        if (shown != 1) {
            OnScreenKeyboard(hid)
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    // Mirror edits at the end of the field to the host; backspace and typed chars are sent live.
                    if (new.length > text.length && new.startsWith(text)) {
                        // Queued, so a pasted block is typed at the speed the link can carry.
                        hid.typeString(new.substring(text.length))
                    } else if (new.length < text.length && text.startsWith(new)) {
                        repeat(text.length - new.length) { hid.typeKey(Key.BACKSPACE) }
                    }
                    text = if (new.length > 200) new.takeLast(100) else new
                },
                label = { Text("Type here, sent as you go") },
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
    }

    if (landscape) {
        // The pad on top, the keyboard below, so one hand-held screen does both jobs with nothing wasted.
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            // About two thirds of the height goes to the keyboard block; the rest is the pad.
            val block = (maxHeight * 0.64f).coerceIn(170.dp, 250.dp)
            val keysArea = block - 50.dp
            val keyH = ((keysArea - 20.dp) / 5).coerceIn(26.dp, 40.dp)
            val tv = hid.tvMode
            val left = settings.leftHanded
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val buttons = @Composable {
                        Column(Modifier.width(96.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tv) {
                                Box(Modifier.weight(1f)) { HoldButton("Back", icon = Icons.Rounded.ArrowBack) { if (it) hid.consumer(com.airmousepro.hid.Consumer.BACK) } }
                                Box(Modifier.weight(1f)) { HoldButton("OK", icon = Icons.Rounded.RadioButtonChecked) { if (it) hid.keyCombo(0, Key.ENTER) } }
                            } else {
                                val pair = listOf(
                                    Triple(Icons.Rounded.TouchApp, "Left", HidController.BUTTON_LEFT),
                                    Triple(Icons.Rounded.Mouse, "Right", HidController.BUTTON_RIGHT),
                                ).let { if (left) it.reversed() else it }
                                pair.forEach { (icon, label, mask) ->
                                    Box(Modifier.weight(1f)) { HoldButton(label, icon = icon) { hid.setButton(mask, it) } }
                                }
                            }
                        }
                    }
                    if (left) buttons()
                    TouchSurface(hid, Modifier.weight(1f).fillMaxHeight())
                    if (!left) buttons()
                }
                Column(Modifier.fillMaxWidth().height(block), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    toolbar()
                    Box(Modifier.fillMaxWidth().height(keysArea)) {
                        when (shown) {
                            2 -> ExtraKeysPane(hid)
                            else -> CompositionLocalProvider(LocalKeyHeight provides keyH) { keysPane(Modifier.fillMaxHeight()) }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            toolbar()
            keysPane(Modifier)
            NavigationSection(hid)
            ShortcutsSection(hid)
            FunctionKeysSection(hid)
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp)) }
}

// ---------------------------------------------------------------------------------------------
// On-screen keyboard
// ---------------------------------------------------------------------------------------------

private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val symbolRows = listOf("1234567890", "@#$%&*-+=/", "()[]{}<>\\|", ";:\"',.?!~`")

/**
 * A compact keyboard that sends key codes straight to the host, so there is no autocorrect and no text field to
 * fight with. Ctrl, Alt, Win and Shift are sticky: tap once for the next key, twice to lock, a third time to release.
 */
@Composable
private fun OnScreenKeyboard(hid: HidController) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    var once by remember { mutableIntStateOf(0) }
    var locked by remember { mutableIntStateOf(0) }
    var symbols by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val layout = hid.layout()
    val apple = hid.target == 1 || hid.target == 5

    fun mods() = once or locked

    fun tick() = Haptics.tick(view, settings)

    fun sendKey(usage: Int) {
        hid.keyCombo(mods(), usage)
        once = 0
    }

    fun sendChar(c: Char) {
        val (m, u) = KeyMap.forChar(c, layout) ?: return
        hid.keyCombo(mods() or m, u)
        once = 0
    }

    // off -> once -> locked -> off
    fun toggle(bit: Int) {
        when {
            locked and bit != 0 -> locked = locked and bit.inv()
            once and bit != 0 -> { once = once and bit.inv(); locked = locked or bit }
            else -> once = once or bit
        }
    }

    val upper = mods() and Mod.SHIFT != 0

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyCap(text = "Esc", onPress = { tick(); sendKey(Key.ESC) })
            KeyCap(text = "Tab", onPress = { tick(); sendKey(Key.TAB) })
            KeyCap(text = "Ctrl", active = mods() and Mod.CTRL != 0, onPress = { tick(); toggle(Mod.CTRL) })
            KeyCap(text = if (apple) "Opt" else "Alt", active = mods() and Mod.ALT != 0, onPress = { tick(); toggle(Mod.ALT) })
            KeyCap(text = if (apple) "Cmd" else "Win", active = mods() and Mod.GUI != 0, onPress = { tick(); toggle(Mod.GUI) })
            KeyCap(icon = Icons.Rounded.ArrowBack, repeat = true, scope = scope, onPress = { tick(); sendKey(Key.LEFT) })
            KeyCap(icon = Icons.Rounded.ArrowUpward, repeat = true, scope = scope, onPress = { tick(); sendKey(Key.UP) })
            KeyCap(icon = Icons.Rounded.ArrowDownward, repeat = true, scope = scope, onPress = { tick(); sendKey(Key.DOWN) })
            KeyCap(icon = Icons.Rounded.ArrowForward, repeat = true, scope = scope, onPress = { tick(); sendKey(Key.RIGHT) })
        }

        if (!symbols) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                letterRows[0].forEach { c -> KeyCap(text = if (upper) c.uppercase() else c.toString(), onPress = { tick(); sendChar(c) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.weight(0.5f))
                letterRows[1].forEach { c -> KeyCap(text = if (upper) c.uppercase() else c.toString(), onPress = { tick(); sendChar(c) }) }
                Box(Modifier.weight(0.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                KeyCap(icon = Icons.Rounded.KeyboardCapslock, weight = 1.5f, active = mods() and Mod.SHIFT != 0, onPress = { tick(); toggle(Mod.SHIFT) })
                letterRows[2].forEach { c -> KeyCap(text = if (upper) c.uppercase() else c.toString(), onPress = { tick(); sendChar(c) }) }
                KeyCap(icon = Icons.AutoMirrored.Rounded.Backspace, weight = 1.5f, repeat = true, scope = scope, onPress = { tick(); sendKey(Key.BACKSPACE) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                KeyCap(text = "?123", weight = 1.5f, onPress = { tick(); symbols = true })
                KeyCap(text = ",", onPress = { tick(); sendChar(',') })
                KeyCap(icon = Icons.Rounded.SpaceBar, weight = 4f, onPress = { tick(); sendKey(Key.SPACE) })
                KeyCap(text = ".", onPress = { tick(); sendChar('.') })
                KeyCap(icon = Icons.AutoMirrored.Rounded.KeyboardReturn, weight = 1.5f, onPress = { tick(); sendKey(Key.ENTER) })
            }
        } else {
            symbolRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { c -> KeyCap(text = c.toString(), onPress = { tick(); sendChar(c) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                KeyCap(text = "ABC", weight = 1.5f, onPress = { tick(); symbols = false })
                KeyCap(icon = Icons.Rounded.SpaceBar, weight = 4f, onPress = { tick(); sendKey(Key.SPACE) })
                KeyCap(icon = Icons.AutoMirrored.Rounded.Backspace, weight = 1.5f, repeat = true, scope = scope, onPress = { tick(); sendKey(Key.BACKSPACE) })
                KeyCap(icon = Icons.AutoMirrored.Rounded.KeyboardReturn, weight = 1.5f, onPress = { tick(); sendKey(Key.ENTER) })
            }
        }
    }
}

/** One key. It fires on touch-down for the lowest latency, and optionally repeats while held. */
@Composable
private fun RowScope.KeyCap(
    text: String? = null,
    icon: ImageVector? = null,
    weight: Float = 1f,
    active: Boolean = false,
    repeat: Boolean = false,
    scope: kotlinx.coroutines.CoroutineScope? = null,
    onPress: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val bg = when {
        active -> MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier
            .weight(weight)
            // Landscape sizes the keys to the space left under the pad, so all five rows always fit.
            .height(LocalKeyHeight.current.takeIf { it != Dp.Unspecified } ?: if (isLandscape()) 36.dp else 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    onPress()
                    val job = if (repeat && scope != null) {
                        scope.launch {
                            delay(REPEAT_DELAY_MS)
                            while (true) {
                                onPress()
                                delay(REPEAT_RATE_MS)
                            }
                        }
                    } else {
                        null
                    }
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        else Text(text.orEmpty(), color = tint, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Extra key groups
// ---------------------------------------------------------------------------------------------

@Composable
private fun NavigationSection(hid: HidController) = Section("Navigate") {
    KeyBtn("Home") { hid.keyCombo(0, Key.HOME) }
    KeyBtn("End") { hid.keyCombo(0, Key.END) }
    KeyBtn("PgUp") { hid.keyCombo(0, Key.PAGE_UP) }
    KeyBtn("PgDn") { hid.keyCombo(0, Key.PAGE_DOWN) }
    KeyBtn("Del") { hid.keyCombo(0, Key.DELETE) }
}

/** A labelled shortcut: modifiers plus an optional key (0 means the modifier alone). */
private class KeyShortcut(val label: String, val mods: Int, val key: Int = 0)

/** The shortcuts that mean something on the device being controlled. Apple devices use Cmd where others use Ctrl. */
private fun shortcutsFor(target: Int): List<KeyShortcut> = if (target == 1 || target == 5) {
    listOf(
        KeyShortcut("Cmd+Tab", Mod.GUI, Key.TAB), KeyShortcut("Cmd+Space", Mod.GUI, Key.SPACE),
        KeyShortcut("Cmd+C", Mod.GUI, 0x06), KeyShortcut("Cmd+V", Mod.GUI, 0x19),
        KeyShortcut("Cmd+X", Mod.GUI, 0x1B), KeyShortcut("Cmd+Z", Mod.GUI, 0x1D),
        KeyShortcut("Cmd+A", Mod.GUI, 0x04), KeyShortcut("Cmd+W", Mod.GUI, 0x1A),
        KeyShortcut("Cmd+Q", Mod.GUI, 0x14), KeyShortcut("Cmd+H", Mod.GUI, 0x0B),
        KeyShortcut("Cmd+Shift+3", Mod.GUI or Mod.SHIFT, 0x20),
    )
} else {
    listOf(
        KeyShortcut("Win", Mod.GUI), KeyShortcut("Alt+Tab", Mod.ALT, Key.TAB), KeyShortcut("Alt+F4", Mod.ALT, Key.f(4)),
        KeyShortcut("Ctrl+C", Mod.CTRL, 0x06), KeyShortcut("Ctrl+V", Mod.CTRL, 0x19), KeyShortcut("Ctrl+X", Mod.CTRL, 0x1B),
        KeyShortcut("Ctrl+Z", Mod.CTRL, 0x1D), KeyShortcut("Ctrl+A", Mod.CTRL, 0x04),
        KeyShortcut("Win+D", Mod.GUI, 0x07), KeyShortcut("Win+L", Mod.GUI, 0x0F),
        KeyShortcut("Ctrl+Alt+Del", Mod.CTRL or Mod.ALT, Key.DELETE),
    )
}

private fun HidController.press(s: KeyShortcut) = if (s.key == 0) keyCombo(s.mods) else keyCombo(s.mods, s.key)

@Composable
private fun ShortcutsSection(hid: HidController) = Section("Shortcuts") {
    shortcutsFor(hid.target).forEach { s -> KeyBtn(s.label) { hid.press(s) } }
}

@Composable
private fun FunctionKeysSection(hid: HidController) = Section("Function keys") {
    for (n in 1..12) KeyBtn("F$n") { hid.keyCombo(0, Key.f(n)) }
}

/**
 * Landscape only: navigation, shortcuts and function keys side by side where the keyboard normally is, so each is one
 * tap on "More" away instead of a long scroll. Slim keys, and each column scrolls if a small screen needs it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraKeysPane(hid: HidController) {
    @Composable
    fun RowScope.Group(title: String, weight: Float, content: @Composable () -> Unit) {
        Column(Modifier.weight(weight).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
        }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Group("Navigate", 0.8f) {
            SlimKey("Home") { hid.keyCombo(0, Key.HOME) }
            SlimKey("End") { hid.keyCombo(0, Key.END) }
            SlimKey("PgUp") { hid.keyCombo(0, Key.PAGE_UP) }
            SlimKey("PgDn") { hid.keyCombo(0, Key.PAGE_DOWN) }
            SlimKey("Del") { hid.keyCombo(0, Key.DELETE) }
        }
        Group("Shortcuts", 1.5f) {
            shortcutsFor(hid.target).forEach { s -> SlimKey(s.label) { hid.press(s) } }
        }
        Group("Function keys", 1f) {
            for (n in 1..12) SlimKey("F$n") { hid.keyCombo(0, Key.f(n)) }
        }
    }
}

@Composable
private fun SlimKey(label: String, onClick: () -> Unit) {
    val settings = LocalContext.current.appSettings
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (pressed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 11.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    Haptics.tick(view, settings)
                    onClick()
                    tryAwaitRelease()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun KeyBtn(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(label) }
}
