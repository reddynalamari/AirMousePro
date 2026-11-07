package com.airmousepro

import android.app.Application
import android.content.Context
import com.airmousepro.data.AppSettings
import com.airmousepro.data.MacroRunner
import com.airmousepro.data.ShortcutStore
import com.airmousepro.hid.HidController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns the shared objects so they outlive the activity and can be used by the foreground service and overlay. */
class AirMouseApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings: AppSettings by lazy { AppSettings(applicationContext) }
    val hid: HidController by lazy { HidController(applicationContext) }
    val shortcuts: ShortcutStore by lazy { ShortcutStore(settings) }
    val macros: MacroRunner by lazy { MacroRunner(hid, appScope) }
}

val Context.app: AirMouseApp get() = applicationContext as AirMouseApp
val Context.appSettings: AppSettings get() = app.settings
