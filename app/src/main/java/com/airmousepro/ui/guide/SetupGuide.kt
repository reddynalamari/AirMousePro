package com.airmousepro.ui.guide

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airmousepro.app
import com.airmousepro.appSettings
import com.airmousepro.hid.HidController
import com.airmousepro.ui.openXiaomiPermissions

private val Green = Color(0xFF30D158)

/**
 * The first-run walkthrough. This is the only place the app asks for permissions on its own, and it asks once:
 * anything skipped here stays off until it is switched on by hand in Settings or on the Devices page.
 */
@Composable
fun SetupGuide(
    hid: HidController,
    permissionTick: Int,
    onRequestPermissions: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = ctx.appSettings
    val menu = ctx.app.quickMenu

    // The overlay permission is granted on a system screen, so re-check whenever the app comes back to the front.
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val canDraw = remember(resumed) { menu.canDraw() }
    var askedOverlay by remember { mutableStateOf(false) }
    // Coming back from the permission screen having allowed it means the user wanted the menu on.
    LaunchedEffect(canDraw, askedOverlay) { if (canDraw && askedOverlay) settings.quickMenu = true }

    val granted = remember(permissionTick) { hid.hasPermission() }
    val connected = hid.state == HidController.State.Connected

    val pages: List<GuidePage> = listOf(
        { active ->
            GuidePageLayout(
                active, hero = { WelcomeHero() },
                title = "Welcome to AirMouse Pro",
                body = "Your phone becomes a Bluetooth mouse, keyboard, gamepad and remote. There is nothing to install on the computer or TV.",
            )
        },
        { active ->
            GuidePageLayout(
                active, hero = { PermissionHero(granted) },
                title = if (granted) "Bluetooth is allowed" else "Allow Bluetooth",
                body = "Android needs your OK before this phone can appear as a Bluetooth device. You are asked once, here. " +
                    "If you skip it, you can allow it later from the Devices page.",
                extra = {
                    if (granted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                            Text("  Allowed", color = Green, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(onClick = onRequestPermissions) { Text("Allow") }
                    }
                },
            )
        },
        { active ->
            GuidePageLayout(
                active, hero = { PairHero(connected) },
                title = if (connected) "Connected to ${hid.hostName}" else "Pair your first device",
                body = "On your computer or TV, open Bluetooth settings, add this phone, and accept the code on both. " +
                    "You can do this later too. The app reconnects to your devices on its own.",
                extra = {
                    if (connected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                            Text("  Ready to use", color = Green, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(onClick = {
                            ctx.startActivity(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                            )
                        }) { Text("Make discoverable") }
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
                    }
                },
            )
        },
        { active ->
            GuidePageLayout(
                active, hero = { GestureHero() },
                title = "Use it like a laptop trackpad",
                body = "Tap to click, two fingers to scroll or right-click, pinch to zoom, and three fingers to switch apps.",
            )
        },
        { active ->
            GuidePageLayout(
                active, hero = { AirHero() },
                title = "Point and click",
                body = "Tilt the phone to move the cursor. Tap the screen to click, hold to drag, swipe to scroll, or use the volume keys as buttons.",
            )
        },
        { active ->
            GuidePageLayout(
                active, hero = { QuickMenuHero() },
                title = "Quick menu",
                body = "A bubble over your other apps opens a dial for volume, brightness and your own shortcuts. " +
                    "It only shows while this app is in the background.",
                extra = {
                    when {
                        canDraw && settings.quickMenu -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                            Text("  Quick menu is on", color = Green, fontWeight = FontWeight.SemiBold)
                        }
                        canDraw -> Button(onClick = { settings.quickMenu = true }) { Text("Turn on") }
                        else -> {
                            Button(onClick = {
                                askedOverlay = true
                                ctx.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text("Allow and turn on") }
                            Text(
                                "On Xiaomi phones, also allow pop-ups while running in the background in the app's permissions.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                            )
                            TextButton(onClick = { openXiaomiPermissions(ctx) }) { Text("Open app permissions") }
                        }
                    }
                },
            )
        },
        { active ->
            GuidePageLayout(
                active, hero = { DoneHero() },
                title = "You're ready",
                body = "Connect on the Devices page, then try the Mouse tab. You can replay this guide any time from Settings.",
            )
        },
    )

    GuideHost(pages = pages, lastLabel = "Start", onFinish = onFinish, onSkip = onSkip)
}
