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
import androidx.compose.foundation.layout.Column
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

    val toolbar = @Composable {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf("Keys", "Text"), mode, { mode = it }, Modifier.weight(1f))
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
            ToolButton(Icons.Rounded.ContentPaste, "Type clipboard") {
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

    val keysPane = @Composable {
        if (mode == 0) {
            OnScreenKeyboard(hid)
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    // Mirror edits at the end of the field to the host; backspace and typed chars are sent live.
                    if (new.length > text.length && new.startsWith(text)) {
                        new.substring(text.length).forEach { hid.typeChar(it) }
                    } else if (new.length < text.length && text.startsWith(new)) {
                        repeat(text.length - new.length) { hid.keyCombo(0, Key.BACKSPACE) }
                    }
                    text = if (new.length > 200) new.takeLast(100) else new
                },
                label = { Text("Type here, sent as you go") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (isLandscape()) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1.5f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                toolbar()
                keysPane()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NavigationSection(hid)
                ShortcutsSection(hid)
                FunctionKeysSection(hid)
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            toolbar()
            keysPane()
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
            KeyCap(text = "Alt", active = mods() and Mod.ALT != 0, onPress = { tick(); toggle(Mod.ALT) })
            KeyCap(text = "Win", active = mods() and Mod.GUI != 0, onPress = { tick(); toggle(Mod.GUI) })
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
            // Shorter keys in landscape so all five rows fit without scrolling.
            .height(if (isLandscape()) 36.dp else 46.dp)
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

@Composable
private fun ShortcutsSection(hid: HidController) = Section("Shortcuts") {
    KeyBtn("Win") { hid.keyCombo(Mod.GUI) }
    KeyBtn("Alt+Tab") { hid.keyCombo(Mod.ALT, Key.TAB) }
    KeyBtn("Alt+F4") { hid.keyCombo(Mod.ALT, Key.f(4)) }
    KeyBtn("Ctrl+C") { hid.keyCombo(Mod.CTRL, 0x06) }
    KeyBtn("Ctrl+V") { hid.keyCombo(Mod.CTRL, 0x19) }
    KeyBtn("Ctrl+X") { hid.keyCombo(Mod.CTRL, 0x1B) }
    KeyBtn("Ctrl+Z") { hid.keyCombo(Mod.CTRL, 0x1D) }
    KeyBtn("Ctrl+A") { hid.keyCombo(Mod.CTRL, 0x04) }
    KeyBtn("Win+D") { hid.keyCombo(Mod.GUI, 0x07) }
    KeyBtn("Win+L") { hid.keyCombo(Mod.GUI, 0x0F) }
    KeyBtn("Ctrl+Alt+Del") { hid.keyCombo(Mod.CTRL or Mod.ALT, Key.DELETE) }
}

@Composable
private fun FunctionKeysSection(hid: HidController) = Section("Function keys") {
    for (n in 1..12) KeyBtn("F$n") { hid.keyCombo(0, Key.f(n)) }
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
