package com.airmousepro.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KProperty

/** User preferences, backed by SharedPreferences and observable from Compose and from plain coroutines. */
class AppSettings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Bumped on every write so non-Compose code (the overlay service) can react to changes. */
    val version = MutableStateFlow(0)

    private fun changed() { version.value++ }

    private inner class BoolPref(val key: String, def: Boolean) {
        private val s = mutableStateOf(prefs.getBoolean(key, def))
        operator fun getValue(t: Any?, p: KProperty<*>) = s.value
        operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) { s.value = v; prefs.edit().putBoolean(key, v).apply(); changed() }
    }

    private inner class IntPref(val key: String, def: Int) {
        private val s = mutableStateOf(prefs.getInt(key, def))
        operator fun getValue(t: Any?, p: KProperty<*>) = s.value
        operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { s.value = v; prefs.edit().putInt(key, v).apply(); changed() }
    }

    private inner class FloatPref(val key: String, def: Float) {
        private val s = mutableStateOf(prefs.getFloat(key, def))
        operator fun getValue(t: Any?, p: KProperty<*>) = s.value
        operator fun setValue(t: Any?, p: KProperty<*>, v: Float) { s.value = v; prefs.edit().putFloat(key, v).apply(); changed() }
    }

    private inner class StringPref(val key: String, def: String) {
        private val s = mutableStateOf(prefs.getString(key, def) ?: def)
        operator fun getValue(t: Any?, p: KProperty<*>) = s.value
        operator fun setValue(t: Any?, p: KProperty<*>, v: String) { s.value = v; prefs.edit().putString(key, v).apply(); changed() }
    }

    // ---- Look ----
    /** 0 system, 1 dark, 2 AMOLED black, 3 light. */
    var themeMode by IntPref("theme_mode", 2)
    /** Index into [Accents]; -1 means Material You wallpaper colours. */
    var accent by IntPref("accent", 0)
    /** 0 off, 1 soft, 2 crisp. */
    var haptics by IntPref("haptic_level", 2)
    /** 0 follow the phone's rotation lock, 1 always auto, 2 portrait, 3 landscape. */
    var orientation by IntPref("orientation", 0)
    var keepAwake by BoolPref("keep_awake", true)

    // ---- Keyboard ----
    /** Index into [com.airmousepro.hid.KbLayout]: the layout the computer is using. */
    var kbLayout by IntPref("kb_layout", 0)

    // ---- Touchpad ----
    var tapToClick by BoolPref("tap_to_click", true)
    var naturalScroll by BoolPref("natural_scroll", false)
    /** 0 precise, 1 balanced, 2 fast. */
    var accelPreset by IntPref("accel_preset", 1)
    var padSpeed by FloatPref("pad_speed", 1.2f)
    var scrollSpeed by FloatPref("scroll_speed", 1f)
    var threeFingerGestures by BoolPref("three_finger", true)
    var pinchZoom by BoolPref("pinch_zoom", true)
    var inertia by BoolPref("inertia", true)
    var leftHanded by BoolPref("left_handed", false)

    // ---- Air mouse ----
    var airSensitivity by FloatPref("air_sensitivity", 1f)
    /** Which edge of the phone points at the screen: 0 top, 1 right, 2 left. */
    var airPointing by IntPref("air_pointing", 0)
    var airVolumeKeys by BoolPref("air_volume_keys", true)
    var airShake by BoolPref("air_shake", false)
    var airTwist by BoolPref("air_twist", false)

    // ---- Connection ----
    var autoReconnect by BoolPref("auto_reconnect", true)
    var lastHost by StringPref("last_host", "")
    /** Devices that have connected before, newest first, as "address|epochMillis" pairs. */
    var connectedHistory by StringPref("connected_history", "")

    fun history(): List<Pair<String, Long>> = connectedHistory.split(',').filter { it.isNotBlank() }.mapNotNull {
        val p = it.split('|')
        if (p.size == 2) p[0] to (p[1].toLongOrNull() ?: 0L) else null
    }

    fun recordConnected(address: String) {
        val rest = history().filterNot { it.first == address }
        connectedHistory = (listOf(address to System.currentTimeMillis()) + rest).take(6).joinToString(",") { "${it.first}|${it.second}" }
    }

    fun forgetRecent(address: String) {
        connectedHistory = history().filterNot { it.first == address }.joinToString(",") { "${it.first}|${it.second}" }
    }
    /** 0 Windows, 1 macOS, 2 Linux, 3 Android TV. */
    var os by IntPref("os_profile", 0)

    // ---- Quick menu ----
    var quickMenu by BoolPref("quick_menu", false)
    var bubbleSize by IntPref("bubble_size", 52)
    var bubbleOpacity by FloatPref("bubble_opacity", 0.9f)
    var bubbleSide by IntPref("bubble_side", 1)          // 0 left, 1 right
    var bubbleY by FloatPref("bubble_y", 0.45f)          // fraction of screen height
    /** Dial contents in order, e.g. "b:touchpad,b:keys,s:abc123". */
    var dialSlots by StringPref("dial_slots", DEFAULT_DIAL)
    /** Set once the dial has been upgraded to include brightness. */
    var dialMigrated by BoolPref("dial_v2", false)

    // ---- First run and guides ----
    /** True once the setup guide has been finished or skipped. Existing installs are marked done at startup. */
    var setupDone by BoolPref("setup_done", false)
    /** Highest changelog revision the user has seen; see [CHANGELOG_REV]. */
    var whatsNewSeen by IntPref("whats_new_seen", 0)
    /** True once the Bluetooth permission dialog has been shown, so it is never raised again on its own. */
    var permAsked by BoolPref("perm_asked", false)

    // ---- Updates ----
    var updateAuto by BoolPref("update_auto", true)
    /** Epoch milliseconds of the last automatic check, kept as text because only Int and Float prefs exist. */
    var updateLastCheck by StringPref("update_last_check", "0")
    /** A release tag the user chose to skip; it is not offered again at launch. */
    var updateSkip by StringPref("update_skip", "")

    // ---- Data ----
    var shortcutsJson by StringPref("shortcuts_json", "")
}

const val OLD_DEFAULT_DIAL = "b:touchpad,b:keys,b:volume,b:playpause"
const val DEFAULT_DIAL = "b:touchpad,b:keys,b:volume,b:brightness,b:playpause"

val Accents = listOf(
    "Blue" to 0xFF0A84FF,
    "Violet" to 0xFFBF5AF2,
    "Green" to 0xFF30D158,
    "Orange" to 0xFFFF9F0A,
    "Pink" to 0xFFFF375F,
)
