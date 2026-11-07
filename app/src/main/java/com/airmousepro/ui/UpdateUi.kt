package com.airmousepro.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.app
import com.airmousepro.appSettings
import com.airmousepro.data.Backup
import com.airmousepro.update.RELEASE_WORKFLOW_URL
import com.airmousepro.update.ReleaseInfo
import com.airmousepro.update.UpdateStatus

/** Turns GitHub's markdown release notes into a few plain lines. */
private fun plainNotes(md: String): String = md.lines()
    .map { it.trim().trimStart('#', '*', '-', ' ').replace("`", "").replace(Regex("https?://\\S+"), "").trim() }
    .filter { it.isNotEmpty() && !it.startsWith("Full Changelog") && !it.startsWith("What's Changed") }
    .take(6)
    .joinToString("\n")

private fun statusLine(s: UpdateStatus, installed: String): String = when (s) {
    UpdateStatus.Idle -> "Installed: v$installed"
    UpdateStatus.Checking -> "Checking GitHub…"
    UpdateStatus.UpToDate -> "You're up to date (v$installed)"
    is UpdateStatus.Available -> "${s.release.tag} is available"
    is UpdateStatus.Downloading -> "Downloading ${s.release.tag}… ${(s.progress * 100).toInt()}%"
    is UpdateStatus.Installing -> "Installing ${s.release.tag}…"
    is UpdateStatus.NeedsPermission -> "Allow installing from AirMouse Pro, come back, then tap Install again"
    is UpdateStatus.Error -> s.message
}

// ---------------------------------------------------------------------------------------------
// Prompt shown when the app opens and finds a newer release
// ---------------------------------------------------------------------------------------------

@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    val um = ctx.app.updates
    LaunchedEffect(Unit) { um.checkOnLaunch() }

    val release = um.prompt ?: return
    val busy = um.status is UpdateStatus.Downloading || um.status is UpdateStatus.Installing

    AlertDialog(
        onDismissRequest = { if (!busy) um.prompt = null },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AirMouse Pro ${release.tag} is ready. You have v${um.installedName}.")
                val notes = plainNotes(release.notes)
                if (notes.isNotEmpty()) Text(notes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("Installing restarts the app and drops the Bluetooth connection for a moment.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                val s = um.status
                if (s is UpdateStatus.Downloading) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                if (s !is UpdateStatus.Available && s !is UpdateStatus.UpToDate && s !is UpdateStatus.Idle && s !is UpdateStatus.Checking) {
                    Text(statusLine(s, um.installedName), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { um.install(release) }) { Text("Install") } },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { um.prompt = null }) { Text("Later") }
                TextButton(enabled = !busy, onClick = { um.skip(release) }) { Text("Skip this version") }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Settings cards
// ---------------------------------------------------------------------------------------------

@Composable
fun UpdatesCard(onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val um = ctx.app.updates
    val s = ctx.appSettings
    val status = um.status
    val busy = status is UpdateStatus.Checking || status is UpdateStatus.Downloading || status is UpdateStatus.Installing

    SettingsCard("Updates") {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(statusLine(status, um.installedName), fontWeight = FontWeight.Medium)
            if (status is UpdateStatus.Downloading) LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status is UpdateStatus.Available || status is UpdateStatus.NeedsPermission) {
                    val r = (status as? UpdateStatus.Available)?.release ?: (status as UpdateStatus.NeedsPermission).release
                    Button(enabled = !busy, onClick = { um.install(r) }) { Text("Install ${r.tag}") }
                }
                OutlinedButton(enabled = !busy, onClick = { um.check(manual = true) }) { Text("Check now") }
            }
        }
        SettingRow("Check when the app opens", "Looks at GitHub Releases at most every six hours. This is the only thing the app sends over the internet.", s.updateAuto) { s.updateAuto = it }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onHistory).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Version history", fontWeight = FontWeight.Medium)
                Text("Install a newer version, or roll back to an older one.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun BackupCard() {
    val ctx = LocalContext.current
    var restored by remember { mutableStateOf<Int?>(null) }
    val actions = rememberBackupActions { restored = it }

    SettingsCard("Backup") {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Saves your settings, shortcuts and dial to a file you choose, so they survive a reinstall.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.export) { Text("Back up") }
                OutlinedButton(onClick = actions.restore) { Text("Restore") }
            }
        }
    }

    restored?.let { n ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Settings restored") },
            text = { Text("$n settings were restored. Close AirMouse Pro and open it again to apply them.") },
            confirmButton = {
                TextButton(onClick = {
                    (ctx as? Activity)?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) { Text("Close app") }
            },
        )
    }
}

class BackupActions(val export: () -> Unit, val restore: () -> Unit)

/** File pickers for saving and loading the backup. They use Android's document picker, so no storage permission is needed. */
@Composable
fun rememberBackupActions(onRestored: (Int) -> Unit): BackupActions {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(settings).toByteArray()) } }
                .onSuccess { Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Could not save the file", Toast.LENGTH_SHORT).show() }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                Backup.import(settings, text)
            }.onSuccess(onRestored).onFailure { Toast.makeText(ctx, it.message ?: "Could not read that file", Toast.LENGTH_LONG).show() }
        }
    }
    return remember { BackupActions(export = { create.launch("AirMousePro-backup.json") }, restore = { open.launch(arrayOf("application/json", "*/*")) }) }
}

// ---------------------------------------------------------------------------------------------
// Version history and rollback
// ---------------------------------------------------------------------------------------------

@Composable
fun VersionHistoryPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val um = ctx.app.updates
    var rollback by remember { mutableStateOf<ReleaseInfo?>(null) }
    LaunchedEffect(Unit) { if (um.releases.isEmpty()) um.check(manual = false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("Version history", style = MaterialTheme.typography.titleLarge)
            }
            Text(statusLine(um.status, um.installedName), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

            if (um.releases.isEmpty() && um.status !is UpdateStatus.Checking) {
                Text("No releases found yet.", modifier = Modifier.padding(vertical = 12.dp))
            }
            um.releases.forEach { r ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.tag, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(r.publishedAt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        }
                        when {
                            um.isSame(r.tag) -> Text("Installed", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            um.isNewer(r.tag) -> Button(onClick = { um.install(r) }) { Text("Install") }
                            else -> OutlinedButton(onClick = { rollback = r }) { Text("Roll back") }
                        }
                    }
                    val notes = plainNotes(r.notes)
                    if (notes.isNotEmpty()) Text(notes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Box(Modifier.size(16.dp))
        }
    }

    rollback?.let { RollbackDialog(it) { rollback = null } }
}

@Composable
private fun RollbackDialog(release: ReleaseInfo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val um = ctx.app.updates
    val backup = rememberBackupActions { Toast.makeText(ctx, "Restored. Reopen the app to apply.", Toast.LENGTH_LONG).show() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Roll back to ${release.tag}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Android won't install an older version over a newer one, so there are two ways to go back.", fontSize = 14.sp)
                Text("Keeps your settings (recommended)", fontWeight = FontWeight.SemiBold)
                Text(
                    "On GitHub, run the Release workflow with “rollback_to” set to ${release.tag}. It rebuilds that version as a new, higher version. " +
                        "This app then offers it as a normal update.",
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_WORKFLOW_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Text("Open GitHub Actions")
                }
                Text("Manual reinstall", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Text("Back up first, because uninstalling erases your settings. Then download the old APK, uninstall, install the download, and restore.", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = backup.export) { Text("1 Back up") }
                    OutlinedButton(onClick = { um.downloadToDownloads(release); Toast.makeText(ctx, "Downloading to your Downloads folder", Toast.LENGTH_SHORT).show() }) { Text("2 Download") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { um.uninstallSelf() }) { Text("3 Uninstall") }
                    OutlinedButton(onClick = backup.restore) { Text("Restore later") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
