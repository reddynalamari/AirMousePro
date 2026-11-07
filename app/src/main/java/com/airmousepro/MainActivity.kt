package com.airmousepro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airmousepro.hid.HidController
import com.airmousepro.service.HidService
import com.airmousepro.ui.ConnectScreen
import com.airmousepro.ui.KeyboardScreen
import com.airmousepro.ui.MouseScreen
import com.airmousepro.ui.RemoteScreen
import com.airmousepro.ui.SettingsScreen
import com.airmousepro.ui.isLandscape
import com.airmousepro.ui.statusInfo

class MainActivity : ComponentActivity() {
    private lateinit var hid: HidController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = (application as AirMouseApp).hid
        // Let landscape content run under a camera cutout instead of leaving a black bar.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent { AppTheme { App(hid) } }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            hid.stop()
            stopService(Intent(this, HidService::class.java))
        }
    }
}

private val Blue = Color(0xFF0A84FF)

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Blue, background = Color(0xFF000000), surface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFF2C2C2E), onBackground = Color.White, onSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = Blue, background = Color(0xFFF2F2F7), surface = Color.White,
            surfaceVariant = Color(0xFFE5E5EA), onBackground = Color.Black, onSurface = Color.Black,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val label: String, val icon: Int)

private val tabs = listOf(
    Tab("Devices", R.drawable.ic_nav_devices),
    Tab("Mouse", R.drawable.ic_nav_mouse),
    Tab("Keys", R.drawable.ic_nav_keyboard),
    Tab("Remote", R.drawable.ic_nav_remote),
)

@Composable
private fun App(hid: HidController) {
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val landscape = isLandscape()
    val ctx = LocalContext.current

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

    // Landscape: hide the status bar for more room; a swipe from the edge brings it back temporarily.
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(landscape) {
        val window = (ctx as android.app.Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    val screen: @Composable () -> Unit = {
        when {
            showSettings -> SettingsScreen(hid, onBack = { showSettings = false })
            tab == 0 -> ConnectScreen(hid, onRetry = { if (permissions.isEmpty()) startAll() else launcher.launch(permissions) })
            tab == 1 -> MouseScreen(hid)
            tab == 2 -> KeyboardScreen(hid)
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
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    },
                ) {
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(painterResource(t.icon), contentDescription = t.label) },
                            label = { Text(t.label) },
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
                                icon = { Icon(painterResource(t.icon), contentDescription = t.label) },
                                label = { Text(t.label) },
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
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}
