package com.airmousepro.data

import org.json.JSONObject

/** Saves and restores every preference, including shortcuts and the dial, as one JSON file. */
object Backup {
    // These belong to this phone and this pairing, so they are not carried between installs.
    private val skip = setOf("last_host", "original_bt_name", "update_last_check", "connected_history", "setup_done", "whats_new_seen", "perm_asked", "scan_asked")

    fun export(settings: AppSettings): String {
        val prefs = JSONObject()
        for ((key, value) in settings.prefs.all) {
            if (key in skip || value == null) continue
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                else -> continue
            }
            prefs.put(key, entry)
        }
        return JSONObject().put("app", "AirMousePro").put("format", 1).put("prefs", prefs).toString(2)
    }

    /** Returns how many settings were restored, or throws if the file is not an AirMouse Pro backup. */
    fun import(settings: AppSettings, json: String): Int {
        val root = JSONObject(json)
        require(root.optString("app") == "AirMousePro") { "This is not an AirMouse Pro backup." }
        val prefs = root.getJSONObject("prefs")
        val edit = settings.prefs.edit()
        var count = 0
        for (key in prefs.keys()) {
            if (key in skip) continue
            val e = prefs.getJSONObject(key)
            when (e.getString("t")) {
                "b" -> edit.putBoolean(key, e.getBoolean("v"))
                "i" -> edit.putInt(key, e.getInt("v"))
                "f" -> edit.putFloat(key, e.getDouble("v").toFloat())
                "s" -> edit.putString(key, e.getString("v"))
                else -> continue
            }
            count++
        }
        edit.commit()
        return count
    }
}
