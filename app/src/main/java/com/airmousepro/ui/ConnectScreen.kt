package com.airmousepro.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController
import com.airmousepro.hid.HidController.AutoStatus
import com.airmousepro.hid.HidController.State

val StatusGreen = Color(0xFF30D158)
val StatusOrange = Color(0xFFFF9F0A)
val StatusGrey = Color(0xFF8E8E93)

/** Title, explanation and indicator colour for the current connection state. */
data class StatusInfo(val title: String, val detail: String, val color: Color)

fun statusInfo(hid: HidController): StatusInfo = when (hid.state) {
    State.Connected -> StatusInfo("Connected", "Controlling ${hid.hostName}", StatusGreen)
    State.Connecting -> StatusInfo("Connecting…", "Keep Bluetooth on for both devices.", StatusOrange)
    State.Ready -> StatusInfo("Not connected", "Pick a device below, or pair a new one.", StatusOrange)
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
private fun deviceIcon(d: BluetoothDevice): ImageVector {
    val c = d.bluetoothClass ?: return Icons.Rounded.Devices
    return when (c.majorDeviceClass) {
        BluetoothClass.Device.Major.COMPUTER -> Icons.Rounded.Laptop
        BluetoothClass.Device.Major.PHONE -> Icons.Rounded.PhoneAndroid
        BluetoothClass.Device.Major.AUDIO_VIDEO -> if (isLikelyHost(d)) Icons.Rounded.Tv else Icons.Rounded.Headphones
        else -> Icons.Rounded.Devices
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(hid: HidController, onRetry: () -> Unit, onScan: () -> Unit) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    var refresh by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }

    val paired = remember(refresh, hid.state) { hid.pairedDevices() }
    val byAddress = paired.associateBy { it.address }
    // Devices that have connected before, newest first. Unpaired ones are dropped.
    val recent = settings.history().mapNotNull { (a, t) -> byAddress[a]?.let { it to t } }
    val recentAddresses = recent.map { it.first.address }.toSet()
    val others = paired.filter { it.address !in recentAddresses }
    val (likely, hidden) = others.partition(::isLikelyHost)
    val shownOthers = if (showAll) others else likely
    val ready = hid.state == State.Ready

    // In landscape the status ring sits in its own pane on the left, so the lists get the full height on the right.
    val landscape = isLandscape()
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        LazyColumn(
            listModifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!landscape) item { Hero(hid, recent.firstOrNull()?.first) }

            hid.connectFailure?.let { name ->
                item {
                    Card(tint = StatusOrange.copy(alpha = 0.16f)) {
                        Text("$name did not accept the connection", fontWeight = FontWeight.SemiBold)
                        val apple = HidController.detectOs(name, null, null) == 5
                        Text(
                            if (apple) {
                                // iPhones and iPads do not take a connection started from the Android side. They connect to the accessory.
                                "iPhones and iPads connect from their own side. On $name, open Settings, then Bluetooth, and tap this phone's name " +
                                    "under My Devices. If it is not there, pair first using the steps below."
                            } else {
                                "This usually means it was paired before AirMouse was open. Remove this phone from $name's Bluetooth list, " +
                                    "remove $name from this phone's list, then pair again while this app is open."
                            },
                            fontSize = 14.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
                            TextButton(onClick = { hid.dismissFailure() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionLabel("Recent") }
                items(recent.size) { i ->
                    val (d, time) = recent[i]
                    val connected = hid.hostName != null && hid.hostAddress == d.address
                    DeviceRow(
                        icon = deviceIcon(d),
                        name = d.name ?: "Unknown device",
                        subtitle = if (connected) "Connected now" else "Last connected " + DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                        highlight = connected,
                        enabled = ready,
                        onClick = { hid.connect(d) },
                        trailing = {
                            IconButton(onClick = { settings.forgetRecent(d.address) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove from recent", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionLabel(if (recent.isEmpty()) "Paired devices" else "Other paired devices") }
                    TextButton(onClick = {
                        refresh++
                        if (!hid.hasPermission()) {
                            onRetry()
                        } else if (hid.state != State.Connected) {
                            hid.restart()
                            // Also makes sure the background service is running now that Bluetooth is allowed.
                            onRetry()
                        }
                    }) { Text("Refresh") }
                }
            }
            if (shownOthers.isEmpty()) {
                item {
                    Text(
                        if (recent.isEmpty()) "No computers or TVs paired yet." else "No other computers or TVs.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
            items(shownOthers.size) { i ->
                val d = shownOthers[i]
                DeviceRow(
                    icon = deviceIcon(d), name = d.name ?: "Unknown device", subtitle = d.address,
                    highlight = false, enabled = ready, onClick = { hid.connect(d) },
                    trailing = { if (ready) Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp)) },
                )
            }
            if (hidden.isNotEmpty()) {
                item {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Hide headphones and car kits" else "Show ${hidden.size} more (headphones, car kits)")
                    }
                }
            }

            // New devices: scan, then tap one to pair. This is how a TV that hides the phone from its own list is reached.
            hid.pairFailure?.let { name ->
                item {
                    Card(tint = StatusOrange.copy(alpha = 0.16f)) {
                        Text("Pairing with $name did not finish", fontWeight = FontWeight.SemiBold)
                        Text("Try again, and accept the pairing code on both devices.", fontSize = 14.sp)
                        TextButton(onClick = { hid.dismissPairFailure() }) { Text("Dismiss") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionLabel("Nearby devices") }
                    TextButton(onClick = { if (hid.scanning) hid.stopScan() else onScan() }) { Text(if (hid.scanning) "Stop" else "Scan") }
                }
            }
            if (hid.scanning) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Looking for devices…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            }
            items(hid.nearby.size) { i ->
                val n = hid.nearby[i]
                val pairing = hid.pairingAddress == n.device.address
                DeviceRow(
                    icon = deviceIcon(n.device), name = n.name,
                    subtitle = if (pairing) "Pairing… accept the code on both devices" else "Tap to pair",
                    highlight = false, enabled = !pairing && hid.pairingAddress == null, onClick = { hid.pair(n) },
                    trailing = {
                        if (pairing) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Pair", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                        }
                    },
                )
            }
            if (!hid.scanning && hid.nearby.isEmpty()) {
                item {
                    Text(
                        if (hid.scanFinished) {
                            "Nothing new found. Make sure the TV or computer is showing its Bluetooth settings so it can be seen, then scan again. " +
                                "If your phone's own Bluetooth screen finds it but this does not, copy the log from Settings > Developer options and send it to me."
                        } else {
                            "Tap Scan to find devices around you. On a TV or computer, open its Bluetooth settings first so it can be found."
                        },
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
            }

            item { PairCard(hid) }
            item { AppleCard() }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.85f).fillMaxHeight().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Hero(hid, recent.firstOrNull()?.first)
                }
                list(Modifier.weight(1.15f).fillMaxHeight())
            }
        } else {
            list(Modifier.widthIn(max = 640.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/**
 * Large status ring that spins while a connection is being made, with the one action that makes sense right now.
 * Reading a device name needs the Bluetooth permission, which is always held by the time a device is shown here:
 * the connect button only appears once the app has registered with Bluetooth.
 */
@SuppressLint("MissingPermission")
@Composable
private fun Hero(hid: HidController, mostRecent: BluetoothDevice?) {
    val info = statusInfo(hid)
    val auto = hid.autoStatus
    val searching = auto is AutoStatus.Trying || hid.state == State.Connecting
    val connected = hid.state == State.Connected
    // A smaller ring in landscape, where the screen is only about 360dp tall.
    val compact = isLandscape()

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val color = if (searching) MaterialTheme.colorScheme.primary else info.color
    val icon = when {
        connected -> Icons.Rounded.BluetoothConnected
        searching -> Icons.Rounded.BluetoothSearching
        !hid.bluetoothEnabled() -> Icons.Rounded.BluetoothDisabled
        else -> Icons.Rounded.Bluetooth
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .padding(vertical = if (compact) 14.dp else 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp),
    ) {
        Box(Modifier.size(if (compact) 88.dp else 124.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                if (searching) {
                    drawArc(color, angle, 100f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                } else {
                    drawArc(color, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                }
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (compact) 34.dp else 46.dp))
        }

        val title = when {
            connected -> hid.hostName ?: "Connected"
            auto is AutoStatus.Trying -> "Looking for ${auto.name}"
            else -> info.title
        }
        val detail = when {
            connected -> "Connected and ready"
            auto is AutoStatus.Trying -> if (auto.total > 1) "Recent device ${auto.index} of ${auto.total}" else "Your most recent device"
            else -> info.detail
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                connected -> OutlinedButton(onClick = { hid.disconnect() }) { Text("Disconnect") }
                auto is AutoStatus.Trying -> OutlinedButton(onClick = { hid.cancelAutoConnect() }) { Text("Cancel") }
                hid.state == State.Ready && mostRecent != null ->
                    Button(onClick = { hid.connect(mostRecent) }) { Text("Connect to ${mostRecent.name ?: "last device"}") }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    subtitle: String,
    highlight: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (highlight) Modifier.border(1.5.dp, StatusGreen.copy(alpha = 0.7f), RoundedCornerShape(18.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (highlight) StatusGreen else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = if (highlight) StatusGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
        trailing()
    }
}

@Composable
private fun PairCard(hid: HidController) {
    val ctx = LocalContext.current
    Card {
        Text("Pair a new device", fontWeight = FontWeight.SemiBold)
        Step(1, "Keep this app open and tap Make discoverable.")
        Step(2, "On the computer or TV, open Bluetooth settings and add this phone.")
        Step(3, "Accept the code on both. It then appears here to connect.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = {
                // Being found and paired from the other side must not be interrupted by reconnecting to recent devices.
                hid.cancelAutoConnect()
                ctx.startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                )
            }) { Text("Make discoverable") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
        }
    }
}

/** Steps that are different on an iPhone or iPad: it connects from its side, and a pointer needs AssistiveTouch. */
@Composable
private fun AppleCard() {
    Card {
        Text("iPhone or iPad", fontWeight = FontWeight.SemiBold)
        Step(1, "Tap Make discoverable above. On the iPhone open Settings, then Bluetooth, and tap this phone's name.")
        Step(2, "Accept the code on both. If this phone does not connect by itself, tap its name again on the iPhone.")
        Step(3, "The keyboard works at once. For the mouse, turn on Settings, Accessibility, Touch, AssistiveTouch, then open Devices there and connect this phone.")
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        Text(text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Card(tint: Color? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(tint ?: MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
