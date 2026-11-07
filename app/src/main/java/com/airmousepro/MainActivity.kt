package com.airmousepro

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airmousepro.data.AppSettings
import com.airmousepro.hid.HidController
import com.airmousepro.input.HardwareKeys
import com.airmousepro.service.HidService
import com.airmousepro.ui.AirMouseTheme
import com.airmousepro.ui.ConnectScreen
import com.airmousepro.ui.DeckScreen
import com.airmousepro.ui.KeyboardScreen
import com.airmousepro.ui.MouseScreen
import com.airmousepro.ui.RemoteScreen
import com.airmousepro.ui.SettingsScreen
import com.airmousepro.ui.UpdatePrompt
import com.airmousepro.ui.isLandscape
import com.airmousepro.ui.statusInfo

/** Lets the floating quick menu ask the open app to switch tab. */
object NavRequests {
    var tab by mutableIntStateOf(-1)
}

class MainActivity : ComponentActivity() {
    private lateinit var hid: HidController
    private val handler = Handler(Looper.getMainLooper())
    private val rotationObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = applyOrientation(this@MainActivity, appSettings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = app.hid
        // Let landscape content run under a camera cutout instead of leaving a black bar.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applyOrientation(this, appSettings)
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, rotationObserver)
        intent?.let { handleIntent(it) }
        setContent { AirMouseTheme { App(hid) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent) {
        val t = i.getIntExtra(EXTRA_TAB, -1)
        if (t >= 0) NavRequests.tab = t
    }

    override fun onResume() {
        super.onResume()
        // The overlay permission may have just been granted in system settings.
        app.quickMenu.refresh()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = HardwareKeys.handle(event) || super.dispatchKeyEvent(event)

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(rotationObserver)
        if (isFinishing) {
            hid.stop()
            stopService(Intent(this, HidService::class.java))
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

/**
 * Orientation policy. "Follow system" honours the phone's auto-rotate switch exactly: when it is off the screen stays
 * where it is, when it is on all orientations are allowed. The other modes override the switch.
 */
fun applyOrientation(activity: ComponentActivity, settings: AppSettings) {
    val autoOn = Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    activity.requestedOrientation = when (settings.orientation) {
        1 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> if (autoOn) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Devices", Icons.Rounded.Bluetooth),
    Tab("Mouse", Icons.Rounded.Mouse),
    Tab("Keys", Icons.Rounded.Keyboard),
    Tab("Deck", Icons.Rounded.GridView),
    Tab("Remote", Icons.Rounded.SettingsRemote),
)

@Composable
private fun App(hid: HidController) {
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val landscape = isLandscape()
    val ctx = LocalContext.current
    val settings = ctx.appSettings

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        // Lets the "AirMouse is running" notification show; the app works without it.
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun startAll() {
        hid.start()
        if (hid.hasPermission()) ContextCompat.startForegroundService(ctx, Intent(ctx, HidService::class.java))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startAll() }
    LaunchedEffect(Unit) {
        if (permissions.isEmpty()) startAll() else launcher.launch(permissions)
    }

    // Tab requests from the floating menu.
    LaunchedEffect(NavRequests.tab) {
        if (NavRequests.tab >= 0) {
            tab = NavRequests.tab.coerceIn(0, tabs.lastIndex)
            showSettings = false
            NavRequests.tab = -1
        }
    }

    // Re-apply the orientation policy whenever the user changes it in Settings.
    LaunchedEffect(settings.orientation) { applyOrientation(ctx as ComponentActivity, settings) }

    // Landscape: hide the status bar for more room; a swipe from the edge brings it back temporarily.
    val view = LocalView.current
    LaunchedEffect(landscape) {
        val window = (ctx as android.app.Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    // Looks for a newer release when the app opens and offers to install it.
    UpdatePrompt()

    val screen: @Composable () -> Unit = {
        when {
            showSettings -> SettingsScreen(hid, onBack = { showSettings = false })
            tab == 0 -> ConnectScreen(hid, onRetry = { if (permissions.isEmpty()) startAll() else launcher.launch(permissions) })
            tab == 1 -> MouseScreen(hid)
            tab == 2 -> KeyboardScreen(hid)
            tab == 3 -> DeckScreen(hid)
            else -> RemoteScreen(hid)
        }
    }

    if (landscape) {
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.displayCutout),
        ) {
            if (!showSettings) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        // Connection dot and settings stay reachable without a top bar.
                        val info = statusInfo(hid)
                        Box(
                            Modifier.padding(top = 8.dp).size(40.dp).clip(CircleShape).clickable { tab = 0 },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(12.dp).clip(CircleShape).background(info.color)) }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                    },
                ) {
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { screen() }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!showSettings) StatusBar(hid, onStatus = { tab = 0 }, onSettings = { showSettings = true })
            },
            bottomBar = {
                if (!showSettings) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                                alwaysShowLabel = false,
                            )
                        }
                    }
                }
            },
        ) { pad -> Box(Modifier.fillMaxSize().padding(pad)) { screen() } }
    }
}

/** Always-visible connection status, so you never have to switch tabs to know if you are connected. */
@Composable
private fun StatusBar(hid: HidController, onStatus: () -> Unit, onSettings: () -> Unit) {
    val info = statusInfo(hid)
    val label = if (hid.state == HidController.State.Connected) "Connected · ${hid.hostName}" else info.title
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onStatus)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(info.color))
            Text(label, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
    }
}
