package com.airmousepro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.app
import com.airmousepro.appSettings
import com.airmousepro.data.KeyNames
import com.airmousepro.data.Shortcut
import com.airmousepro.data.Step
import com.airmousepro.data.Templates
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.hid.KeyMap
import com.airmousepro.hid.Mod

val OsNames = listOf("Windows", "macOS", "Linux", "Android TV")

/** A grid of one-tap shortcuts. Tap runs one, long-press edits it. */
@Composable
fun DeckScreen(hid: HidController) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val store = ctx.app.shortcuts
    val runner = ctx.app.macros
    val view = LocalView.current
    var editing by remember { mutableStateOf<Shortcut?>(null) }

    editing?.let { sc ->
        ShortcutEditor(sc, isNew = store.find(sc.id) == null, onClose = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Deck", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // Target OS decides how "open website/app" shortcuts and gestures are sent.
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { settings.os = (settings.os + 1) % OsNames.size }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(OsNames[settings.os.coerceIn(0, OsNames.lastIndex)], fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(store.all, key = { it.id }) { sc ->
                Tile(
                    icon = IconRegistry.get(sc.icon), label = sc.name,
                    onTap = {
                        Haptics.tap(view, settings)
                        runner.run(sc)
                    },
                    onLong = { editing = sc },
                )
            }
            item {
                Tile(Icons.Rounded.Add, "New", onTap = { editing = Shortcut.create("", "bolt", emptyList()) }, onLong = {}, dim = true)
            }
        }
    }
}

@Composable
private fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onTap: () -> Unit, onLong: () -> Unit, dim: Boolean = false) {
    Column(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val tint = if (dim) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutEditor(initial: Shortcut, isNew: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = ctx.app.shortcuts
    val runner = ctx.app.macros
    val settings = ctx.appSettings
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.icon) }
    var steps by remember { mutableStateOf(initial.steps) }
    var adding by remember { mutableStateOf("") }
    val draft = Shortcut(initial.id, name.ifBlank { "Shortcut" }, icon, steps)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(if (isNew) "New shortcut" else "Edit shortcut", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!isNew) IconButton(onClick = { store.delete(initial.id); onClose() }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            IconButton(onClick = { runner.run(draft) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Test") }
            IconButton(onClick = { store.save(draft); onClose() }) { Icon(Icons.Rounded.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary) }
        }

        OutlinedTextField(value = name, onValueChange = { name = it.take(18) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconRegistry.all.forEach { (key, vec) ->
                val on = key == icon
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { icon = key },
                    contentAlignment = Alignment.Center,
                ) { Icon(vec, contentDescription = key, tint = if (on) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
            }
        }

        Text("Steps", fontWeight = FontWeight.SemiBold)
        if (steps.isEmpty()) Text("Nothing yet. Add a step below or start from a template.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        steps.forEachIndexed { i, st ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(st.describe(), modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 2)
                IconButton(onClick = { if (i > 0) steps = steps.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) } }) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(onClick = { if (i < steps.lastIndex) steps = steps.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) } }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down") }
                IconButton(onClick = { steps = steps.toMutableList().also { l -> l.removeAt(i) } }) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("combo" to "Keys", "text" to "Text", "media" to "Media", "wait" to "Wait", "click" to "Click", "web" to "Website", "app" to "App").forEach { (id, label) ->
                FilterChip(selected = adding == id, onClick = { adding = if (adding == id) "" else id }, label = { Text(label) })
            }
        }

        when (adding) {
            "combo" -> ComboAdder { steps = steps + it; adding = "" }
            "text" -> TextAdder("Text to type") { steps = steps + Step.Text(it); adding = "" }
            "media" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Consumer.PLAY_PAUSE, Consumer.NEXT, Consumer.PREV, Consumer.VOL_UP, Consumer.VOL_DOWN,
                    Consumer.MUTE, Consumer.HOME, Consumer.BACK, Consumer.MENU, 0x221,
                ).forEach { u -> FilledTonalButton(onClick = { steps = steps + Step.Media(u); adding = "" }) { Text(KeyNames.consumer(u)) } }
            }
            "wait" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(250, 500, 1000, 2000, 3000).forEach { ms -> FilledTonalButton(onClick = { steps = steps + Step.Wait(ms); adding = "" }) { Text("${ms} ms") } }
            }
            "click" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { steps = steps + Step.Click(HidController.BUTTON_LEFT); adding = "" }) { Text("Left click") }
                FilledTonalButton(onClick = { steps = steps + Step.Click(HidController.BUTTON_RIGHT); adding = "" }) { Text("Right click") }
            }
            "web" -> TextAdder("Website, for example youtube.com", "Open on ${OsNames[settings.os.coerceIn(0, OsNames.lastIndex)]}") {
                steps = steps + Templates.openWebsite(settings.os, it.trim()); adding = ""
                if (name.isBlank()) name = it.trim().removePrefix("https://").removePrefix("www.").substringBefore('/').take(18)
                if (icon == "bolt") icon = "web"
            }
            "app" -> TextAdder("App name, for example Spotify", "Open on ${OsNames[settings.os.coerceIn(0, OsNames.lastIndex)]}") {
                steps = steps + Templates.openApp(settings.os, it.trim()); adding = ""
                if (name.isBlank()) name = it.trim().take(18)
                if (icon == "bolt") icon = "rocket"
            }
        }

        if (settings.os == 3) {
            Text(
                "Android TV can't be told to launch an app directly over Bluetooth. This opens search and types the name; " +
                    "test it on your TV and adjust the waits.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun TextAdder(hint: String, button: String = "Add", onAdd: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = v, onValueChange = { v = it }, label = { Text(hint) }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { if (v.isNotBlank()) onAdd(v) }) { Text(button) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComboAdder(onAdd: (Step.Combo) -> Unit) {
    var mods by remember { mutableIntStateOf(0) }
    var key by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val specials = listOf(
        "Enter" to Key.ENTER, "Esc" to Key.ESC, "Tab" to Key.TAB, "Space" to Key.SPACE, "Backspace" to Key.BACKSPACE, "Del" to Key.DELETE,
        "Up" to Key.UP, "Down" to Key.DOWN, "Left" to Key.LEFT, "Right" to Key.RIGHT,
        "Home" to Key.HOME, "End" to Key.END, "PgUp" to Key.PAGE_UP, "PgDn" to Key.PAGE_DOWN,
    ) + (1..12).map { "F$it" to Key.f(it) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Ctrl" to Mod.CTRL, "Alt" to Mod.ALT, "Shift" to Mod.SHIFT, "Win" to Mod.GUI).forEach { (label, bit) ->
                FilterChip(selected = mods and bit != 0, onClick = { mods = mods xor bit }, label = { Text(label) })
            }
        }
        OutlinedTextField(
            value = typed,
            onValueChange = { s ->
                typed = s.takeLast(1)
                typed.firstOrNull()?.let { c -> KeyMap.forChar(c)?.let { (m, u) -> key = u; mods = mods or m } }
            },
            label = { Text("Letter, digit or symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            specials.forEach { (label, usage) ->
                FilterChip(selected = key == usage, onClick = { key = usage; typed = "" }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Step.Combo(mods, if (key == 0) emptyList() else listOf(key)).describe().ifEmpty { "Pick keys" }, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Button(onClick = { if (mods != 0 || key != 0) onAdd(Step.Combo(mods, if (key == 0) emptyList() else listOf(key))) }) { Text("Add") }
        }
    }
}
