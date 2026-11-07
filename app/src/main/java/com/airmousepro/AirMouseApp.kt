package com.airmousepro

import android.app.Application
import android.content.Context
import com.airmousepro.data.AppSettings
import com.airmousepro.data.MacroRunner
import com.airmousepro.data.ShortcutStore
import com.airmousepro.hid.HidController
import com.airmousepro.overlay.QuickMenu
import com.airmousepro.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Owns the shared objects so they outlive the activity and can be used by the foreground service and overlay. */
class AirMouseApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings: AppSettings by lazy { AppSettings(applicationContext) }
    val hid: HidController by lazy { HidController(applicationContext) }
    val shortcuts: ShortcutStore by lazy { ShortcutStore(settings) }
    val macros: MacroRunner by lazy { MacroRunner(hid, appScope) }
    val quickMenu: QuickMenu by lazy { QuickMenu(this) }
    val updates: UpdateManager by lazy { UpdateManager(this) }

    /** True while the app's screen is showing. The floating menu is only drawn while this is false. */
    val inForeground = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        // An install that already has saved settings predates the setup guide: do not make that user sit through it.
        // A brand new install has none of these keys yet, so it gets the guide on first launch.
        val p = settings.prefs
        if (!p.contains("setup_done") && (p.contains("dial_v2") || p.contains("update_last_check") || p.contains("last_host"))) {
            settings.setupDone = true
        }
    }
}

val Context.app: AirMouseApp get() = applicationContext as AirMouseApp
val Context.appSettings: AppSettings get() = app.settings
