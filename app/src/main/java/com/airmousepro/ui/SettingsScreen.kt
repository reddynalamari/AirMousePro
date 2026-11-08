package com.airmousepro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airmousepro.app
import com.airmousepro.appSettings
import com.airmousepro.data.Accents
import com.airmousepro.hid.HidController

private const val KEY_DEV = "developer_options"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(hid: HidController, onBack: () -> Unit, onReplaySetup: () -> Unit, onReplayWhatsNew: () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.appSettings
    var dev by remember { mutableStateOf(s.prefs.getBoolean(KEY_DEV, false)) }
    var rename by remember { mutableStateOf(hid.showAsAirMouse) }
    var page by remember { mutableStateOf("main") }

    if (page == "quick") {
        QuickMenuSettings(onBack = { page = "main" })
        return
    }
    if (page == "versions") {
        VersionHistoryPage(onBack = { page = "main" })
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }

            SettingsCard("Appearance") {
                ChoiceRow("Theme", listOf("System", "Dark", "AMOLED", "Light"), s.themeMode) { s.themeMode = it }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Accent", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Accents.forEachIndexed { i, (name, argb) ->
                            Swatch(Color(argb), name, selected = s.accent == i) { s.accent = i }
                        }
                        if (Build.VERSION.SDK_INT >= 31) {
                            Swatch(Brush.sweepGradient(listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4))), "Wallpaper colours", selected = s.accent == -1) { s.accent = -1 }
                        }
                    }
                }
                ChoiceRow("Haptics", listOf("Off", "Soft", "Crisp"), s.haptics) { s.haptics = it }
                ChoiceRow("Rotation", listOf("System", "Auto", "Portrait", "Landscape"), s.orientation, detail = "System follows your phone's auto-rotate switch. With the switch off, the app stays in portrait.") { s.orientation = it }
                SettingRow("Keep screen on", "While the Mouse tab is open.", s.keepAwake) { s.keepAwake = it }
            }

            SettingsCard("Keyboard") {
                ChoiceRow(
                    "Computer's keyboard layout", com.airmousepro.hid.KbLayout.values().map { it.label }, s.kbLayout,
                    detail = "Pick the layout the computer uses, not the phone. Keys are sent as key positions.",
                ) { s.kbLayout = it }
            }

            SettingsCard("Touchpad") {
                SettingRow("Tap to click", "A quick tap clicks. Two-finger tap right-clicks, three-finger tap middle-clicks.", s.tapToClick) { s.tapToClick = it }
                SettingRow("Natural scrolling", "Content follows your fingers, like on a phone.", s.naturalScroll) { s.naturalScroll = it }
                SettingRow("Scroll momentum", "Scrolling keeps gliding after you lift your fingers.", s.inertia) { s.inertia = it }
                SettingRow("Pinch to zoom", "Sends Ctrl and the mouse wheel.", s.pinchZoom) { s.pinchZoom = it }
                SettingRow("Two-finger swipe for back and forward", "A quick sideways swipe goes back (left) or forward (right). Slow sideways movement still scrolls.", s.twoFingerNav) { s.twoFingerNav = it }
                SettingRow("Three-finger swipes", "Task view, show desktop and app switch for your target system.", s.threeFingerGestures) { s.threeFingerGestures = it }
                SettingRow("Left-handed buttons", "Mirrors the click buttons.", s.leftHanded) { s.leftHanded = it }
                ChoiceRow("Pointer feel", listOf("Precise", "Balanced", "Fast"), s.accelPreset) { s.accelPreset = it }
                SliderRow("Scroll speed", s.scrollSpeed, 0.5f..2.5f) { s.scrollSpeed = it }
            }

            SettingsCard("Air mouse") {
                SettingRow("Volume keys as buttons", "Volume up clicks (hold to drag), volume down right-clicks.", s.airVolumeKeys) { s.airVolumeKeys = it }
                SettingRow("Shake to pause", "Shake twice to pause or resume the pointer.", s.airShake) { s.airShake = it }
                SettingRow("Wrist twist for back and forward", "A quick roll of your wrist presses Alt+Left or Alt+Right.", s.airTwist) { s.airTwist = it }
            }

            SettingsCard("Connection") {
                SettingRow("Reconnect automatically", "Connects to the last device when the app starts.", s.autoReconnect) { s.autoReconnect = it }
                ChoiceRow("Controlling", OsNames, s.os, detail = "Used by gestures and by “open website” and “open app” shortcuts.") { s.os = it }
                ChoiceRow(
                    "TV remote mode", listOf("Auto", "Always", "Never"), s.tvMode,
                    detail = "On a TV, swipes press arrow keys and a tap is OK. Auto turns it on when the connected device looks like a TV.",
                ) { s.tvMode = it }
                SettingRow(
                    "Show as “AirMouse - your phone name”",
                    "Renames your phone's Bluetooth name while the app runs. Your original name is restored on exit.",
                    rename,
                ) { rename = it; hid.showAsAirMouse = it }
            }

            SettingsCard("Quick menu") {
                Row(
                    Modifier.fillMaxWidth().clickable { page = "quick" }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating dial", fontWeight = FontWeight.Medium)
                        Text(
                            if (s.quickMenu) "On · ${s.dialSlots.split(',').count { it.isNotBlank() }} items" else "Off",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }

            SettingsCard("Guides") {
                NavRow("Setup guide", "Replay the first-run walkthrough.", onReplaySetup)
                NavRow("What's new", "See the latest changes again.", onReplayWhatsNew)
            }

            UpdatesCard(onHistory = { page = "versions" })

            BackupCard()

            AboutCard()

            SettingsCard("Advanced") {
                SettingRow("Developer options", "Show Bluetooth logs and device info.", dev) {
                    dev = it
                    s.prefs.edit().putBoolean(KEY_DEV, it).apply()
                }
            }
            if (dev) DeveloperSection(hid)
            Box(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NavRow(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun Swatch(fill: Any, name: String, selected: Boolean, onClick: () -> Unit) {
    val base = Modifier
        .size(28.dp)
        .clip(CircleShape)
    val shaped = when (fill) {
        is Color -> base.background(fill)
        is Brush -> base.background(fill)
        else -> base
    }
    Box(
        shaped
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "$name, selected", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), modifier = Modifier.padding(start = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
fun SettingRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (detail.isNotEmpty()) Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, detail: String = "", onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            if (detail.isNotEmpty()) Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 12.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// Quick menu settings
// ---------------------------------------------------------------------------------------------

private const val MAX_ITEMS = 10
private const val MAX_CUSTOM = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickMenuSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.appSettings
    val menu = ctx.app.quickMenu
    val store = ctx.app.shortcuts

    // Re-check the overlay permission whenever the user comes back from system settings.
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val canDraw = remember(resumed) { menu.canDraw() }

    val slots = s.dialSlots.split(',').filter { it.isNotBlank() }
    val builtIns = menu.builtInItems()
    fun label(slot: String): String = menu.itemsFor(slot).firstOrNull()?.label ?: "Missing shortcut"
    fun save(list: List<String>) { s.dialSlots = list.joinToString(",") }
    val customCount = slots.count { it.startsWith("s:") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("Quick menu", style = MaterialTheme.typography.titleLarge)
            }

            SettingsCard("Floating dial") {
                SettingRow("Show while connected", "A bubble on the screen edge. Tap it to open a rotary dial.", s.quickMenu) { s.quickMenu = it }
            }

            if (s.quickMenu && !canDraw) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x29FF9F0A)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Permission needed", fontWeight = FontWeight.SemiBold)
                    Text("Android needs your OK to draw the bubble over other apps.", fontSize = 14.sp)
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Allow display over other apps") }
                    Text(
                        "On Xiaomi phones also open App permissions and allow “Display pop-up windows while running in the background”.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    OutlinedButton(onClick = { openXiaomiPermissions(ctx) }) { Text("Open app permissions") }
                }
            }

            SettingsCard("Bubble") {
                SliderRow("Size", s.bubbleSize.toFloat(), 40f..72f) { s.bubbleSize = it.toInt() }
                SliderRow("Opacity", s.bubbleOpacity, 0.3f..1f) { s.bubbleOpacity = it }
            }

            SettingsCard("On the dial (${slots.size} of $MAX_ITEMS)") {
                if (slots.isEmpty()) Text("Nothing yet. Add items below.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                slots.forEachIndexed { i, slot ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            menu.itemsFor(slot).firstOrNull()?.icon ?: Icons.Rounded.Mouse, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).size(22.dp),
                        )
                        Text(label(slot), modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (i > 0) save(slots.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(onClick = { if (i < slots.lastIndex) save(slots.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) }) }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down") }
                        IconButton(onClick = { save(slots.toMutableList().also { l -> l.removeAt(i) }) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
                    }
                }
            }

            SettingsCard("Add built-in") {
                val missing = builtIns.filter { it.id !in slots }
                if (missing.isEmpty()) Text("All built-in actions are on the dial.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    missing.forEach { item ->
                        FilterChip(
                            selected = false, enabled = slots.size < MAX_ITEMS,
                            onClick = { save(slots + item.id) },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }

            SettingsCard("Add your shortcuts ($customCount of $MAX_CUSTOM)") {
                val available = store.all.filter { "s:${it.id}" !in slots }
                if (store.all.isEmpty()) Text("Create shortcuts in the Deck tab first.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    available.forEach { sc ->
                        FilterChip(
                            selected = false, enabled = slots.size < MAX_ITEMS && customCount < MAX_CUSTOM,
                            onClick = { save(slots + "s:${sc.id}") },
                            leadingIcon = { Icon(IconRegistry.get(sc.icon), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(sc.name) },
                        )
                    }
                }
            }
            Box(Modifier.size(16.dp))
        }
    }
}

/** MIUI/HyperOS keeps the extra overlay permission on its own screen; fall back to the normal app info page. */
fun openXiaomiPermissions(ctx: Context) {
    val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(miui) }.onFailure { runCatching { ctx.startActivity(fallback) } }
}

// ---------------------------------------------------------------------------------------------
// About and developer
// ---------------------------------------------------------------------------------------------

@Composable
private fun AboutCard() {
    val ctx = LocalContext.current
    val (version, build) = remember {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (info.versionName ?: "?") to info.longVersionCode
        }.getOrDefault("?" to 0L)
    }

    SettingsCard("About") {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mouse, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text("AirMouse Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Version $version (build $build)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text("Developed by Shashidhar Reddy", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Why it's different", fontWeight = FontWeight.SemiBold)
            Feature("Nothing to install on the laptop or TV: it pairs as a normal Bluetooth mouse and keyboard.")
            Feature("A laptop-style trackpad with tap, two-finger scroll, pinch and three-finger gestures.")
            Feature("Air mouse that keeps the cursor still while you tap, with volume keys as buttons.")
            Feature("A rotary quick menu that floats over any app, loaded with your own shortcuts.")
            Feature("Free: no ads and no account. The only internet use is checking GitHub for updates, and you can turn that off.")
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row {
        Text("•  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun DeveloperSection(hid: HidController) {
    val ctx = LocalContext.current
    // Info block first, then the log, so copying gives the full picture in one paste.
    val report = hid.diagnostics() + "\n--- Log ---\n" + hid.log.joinToString("\n").ifEmpty { "(empty)" }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AirMouse Pro log", report))
            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy all") }
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
            ctx.startActivity(Intent.createChooser(send, "Share log"))
        }) { Text("Share") }
        OutlinedButton(onClick = { hid.clearLog() }) { Text("Clear") }
    }
    OutlinedButton(onClick = { hid.restart() }) { Text("Re-register HID") }

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        // Long-press to select and copy part of the text.
        SelectionContainer {
            Text(report, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
        }
    }
}
