package com.airmousepro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.R
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController

private const val PREFS = "settings"
private const val KEY_DEV = "developer_options"

@Composable
fun SettingsScreen(hid: HidController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val prefs = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var dev by remember { mutableStateOf(prefs.getBoolean(KEY_DEV, false)) }
    var rename by remember { mutableStateOf(hid.showAsAirMouse) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            SettingsCard("Controls") {
                SettingRow("Haptic feedback", "A short vibration on clicks and drags.", settings.haptics) { settings.haptics = it }
                SettingRow("Keep screen on", "Stops the screen sleeping while you use the Mouse tab.", settings.keepAwake) { settings.keepAwake = it }
                SettingRow("Natural scrolling", "Two-finger scroll moves content with your fingers, like on a phone.", settings.naturalScroll) { settings.naturalScroll = it }
            }

            SettingsCard("Bluetooth") {
                SettingRow(
                    "Show as “AirMouse - your phone name”",
                    "Renames your phone's Bluetooth name while the app is running, so you can spot it in a device list. " +
                        "Your original name is restored when you close the app or turn this off.",
                    rename,
                ) { rename = it; hid.showAsAirMouse = it }
            }

            AboutCard()

            SettingsCard("Advanced") {
                SettingRow("Developer options", "Show Bluetooth logs and device info.", dev) {
                    dev = it
                    prefs.edit().putBoolean(KEY_DEV, it).apply()
                }
            }
            if (dev) DeveloperSection(hid)
            Box(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), modifier = Modifier.padding(start = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

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
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_nav_mouse), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text("AirMouse Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Version $version (build $build)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text("Developed by Shashidhar Reddy", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Why it's different", fontWeight = FontWeight.SemiBold)
            Feature("Nothing to install on the laptop or TV — it pairs as a normal Bluetooth mouse and keyboard.")
            Feature("Touchpad, air mouse, keyboard, shortcuts, media keys and TV remote in one app.")
            Feature("Air mouse: hold the pad and point your phone like a laser pointer.")
            Feature("Drag and drop, two-finger scroll, right click, and an acceleration curve for precise pointing.")
            Feature("Landscape mode with a side rail and a wide touchpad.")
            Feature("Free: no ads, no account, and the app has no internet permission at all.")
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
