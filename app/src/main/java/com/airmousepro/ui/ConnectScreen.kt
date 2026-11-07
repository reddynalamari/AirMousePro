package com.airmousepro.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.hid.HidController
import com.airmousepro.hid.HidController.State

val StatusGreen = Color(0xFF30D158)
val StatusOrange = Color(0xFFFF9F0A)
val StatusGrey = Color(0xFF8E8E93)

/** Title, explanation and indicator colour for the current connection state. */
data class StatusInfo(val title: String, val detail: String, val color: Color)

fun statusInfo(hid: HidController): StatusInfo = when (hid.state) {
    State.Connected -> StatusInfo("Connected", "Controlling ${hid.hostName}", StatusGreen)
    State.Connecting -> StatusInfo("Connecting…", "Keep Bluetooth on for both devices.", StatusOrange)
    State.Ready -> StatusInfo("Not connected", "Pick a paired device below, or pair a new one.", StatusOrange)
    State.Registering -> StatusInfo("Starting…", "Setting up Bluetooth.", StatusGrey)
    State.Failed -> StatusInfo("Setup failed", "Tap Refresh to try again. Details are in Settings → Developer options.", StatusGrey)
    State.Unsupported -> StatusInfo("Not supported", "This phone cannot act as a Bluetooth keyboard or mouse.", StatusGrey)
    State.Idle -> when {
        !hid.hasPermission() -> StatusInfo("Permission needed", "Tap Refresh and allow Nearby devices.", StatusGrey)
        !hid.bluetoothEnabled() -> StatusInfo("Bluetooth is off", "Turn Bluetooth on, then tap Refresh.", StatusGrey)
        else -> StatusInfo("Starting…", "Setting up Bluetooth.", StatusGrey)
    }
}

@SuppressLint("MissingPermission")
private fun isLikelyHost(d: BluetoothDevice): Boolean {
    val c = d.bluetoothClass ?: return true
    if (c.majorDeviceClass != BluetoothClass.Device.Major.AUDIO_VIDEO) return true
    // Headphones, speakers and car kits are audio; TVs and monitors are the audio/video devices worth keeping.
    return c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER ||
        c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(hid: HidController, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    val all = remember(refresh, hid.state) { hid.pairedDevices() }
    val (likely, others) = remember(all) { all.partition(::isLikelyHost) }
    val shown = if (showAll) all else likely

    // Cap the width so cards do not stretch across a landscape screen.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxHeight(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(hid) }

        hid.connectFailure?.let { name ->
            item {
                Card(tint = StatusOrange.copy(alpha = 0.16f)) {
                    Text("$name did not accept the connection", fontWeight = FontWeight.SemiBold)
                    Text(
                        "This usually means it was paired before AirMouse was open. Remove this phone from $name's Bluetooth list, " +
                            "remove $name from this phone's list, then pair again while this app is open.",
                        fontSize = 14.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
                        TextButton(onClick = { hid.dismissFailure() }) { Text("Dismiss") }
                    }
                }
            }
        }

        item {
            Card {
                Text("Pair a new device", fontWeight = FontWeight.SemiBold)
                Text("1. Keep this app open and tap Make discoverable.", fontSize = 14.sp)
                Text("2. On the laptop or TV, open Bluetooth settings and add this phone.", fontSize = 14.sp)
                Text("3. Accept the code on both, then tap the device below.", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                        )
                    }) { Text("Make discoverable") }
                    OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Paired devices", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    refresh++
                    if (!hid.hasPermission()) onRetry() else if (hid.state != State.Connected) hid.restart()
                }) { Text("Refresh") }
            }
        }

        if (shown.isEmpty()) {
            item { Text("No computers or TVs paired yet.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
        }
        items(shown) { d ->
            val enabled = hid.state == State.Ready
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = enabled) { hid.connect(d) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.name ?: "Unknown device", fontWeight = FontWeight.Medium)
                    Text(d.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                if (enabled) Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }

        if (others.isNotEmpty()) {
            item {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Hide other devices" else "Show ${others.size} other devices (headphones, car…)")
                }
            }
        }
    }
    }
}

@Composable
private fun StatusCard(hid: HidController) {
    val info = statusInfo(hid)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(info.color))
            Text(info.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(info.detail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        if (hid.state == State.Connected) OutlinedButton(onClick = { hid.disconnect() }) { Text("Disconnect") }
    }
}

@Composable
private fun Card(tint: Color? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tint ?: MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
