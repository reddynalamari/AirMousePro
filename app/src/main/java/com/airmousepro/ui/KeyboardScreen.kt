package com.airmousepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod

@Composable
fun KeyboardScreen(hid: HidController) {
    var text by remember { mutableStateOf("") }

    val typeBox = @Composable {
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
            label = { Text("Type here — sent to the connected device") },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (isLandscape()) {
        // Two independent columns so the keys use the width instead of one long scroll.
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                typeBox()
                KeysSection(hid)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShortcutsSection(hid)
                FunctionKeysSection(hid)
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            typeBox()
            KeysSection(hid)
            ShortcutsSection(hid)
            FunctionKeysSection(hid)
        }
    }
}

@Composable
private fun KeysSection(hid: HidController) = Section("Keys") {
    KeyBtn("Esc") { hid.keyCombo(0, Key.ESC) }
    KeyBtn("Tab") { hid.keyCombo(0, Key.TAB) }
    KeyBtn("Enter") { hid.keyCombo(0, Key.ENTER) }
    KeyIcon(Icons.AutoMirrored.Rounded.Backspace, "Backspace") { hid.keyCombo(0, Key.BACKSPACE) }
    KeyBtn("Del") { hid.keyCombo(0, Key.DELETE) }
    KeyIcon(Icons.Rounded.KeyboardArrowLeft, "Left") { hid.keyCombo(0, Key.LEFT) }
    KeyIcon(Icons.Rounded.KeyboardArrowUp, "Up") { hid.keyCombo(0, Key.UP) }
    KeyIcon(Icons.Rounded.KeyboardArrowDown, "Down") { hid.keyCombo(0, Key.DOWN) }
    KeyIcon(Icons.Rounded.KeyboardArrowRight, "Right") { hid.keyCombo(0, Key.RIGHT) }
    KeyBtn("Home") { hid.keyCombo(0, Key.HOME) }
    KeyBtn("End") { hid.keyCombo(0, Key.END) }
    KeyBtn("PgUp") { hid.keyCombo(0, Key.PAGE_UP) }
    KeyBtn("PgDn") { hid.keyCombo(0, Key.PAGE_DOWN) }
}

@Composable
private fun ShortcutsSection(hid: HidController) = Section("Shortcuts") {
    KeyBtn("Win") { hid.keyCombo(Mod.GUI, 0) }
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
        Text(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun KeyBtn(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(label) }
}

@Composable
private fun KeyIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}
