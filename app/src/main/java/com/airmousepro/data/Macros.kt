package com.airmousepro.data

import androidx.compose.runtime.mutableStateOf
import com.airmousepro.hid.Consumer
import com.airmousepro.hid.HidController
import com.airmousepro.hid.Key
import com.airmousepro.hid.Mod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One action in a macro. */
sealed interface Step {
    /** Modifier mask plus up to six key usages pressed together. */
    data class Combo(val mods: Int, val keys: List<Int>) : Step
    data class Text(val text: String) : Step
    data class Media(val usage: Int) : Step
    data class Wait(val ms: Int) : Step
    data class Click(val button: Int) : Step

    fun describe(): String = when (this) {
        is Combo -> buildString {
            if (mods and Mod.CTRL != 0) append("Ctrl+")
            if (mods and Mod.ALT != 0) append("Alt+")
            if (mods and Mod.SHIFT != 0) append("Shift+")
            if (mods and Mod.GUI != 0) append("Win+")
            append(keys.joinToString("+") { KeyNames.name(it) })
        }.trimEnd('+')
        is Text -> "Type “$text”"
        is Media -> "Media: ${KeyNames.consumer(usage)}"
        is Wait -> "Wait $ms ms"
        is Click -> if (button == HidController.BUTTON_RIGHT) "Right click" else "Click"
    }
}

data class Shortcut(val id: String, val name: String, val icon: String, val steps: List<Step>) {
    companion object {
        fun create(name: String, icon: String, steps: List<Step>) = Shortcut(UUID.randomUUID().toString().take(8), name, icon, steps)
    }
}

/** Display names for key usages, used in the editor. */
object KeyNames {
    private val special = mapOf(
        Key.ENTER to "Enter", Key.ESC to "Esc", Key.BACKSPACE to "Backspace", Key.TAB to "Tab", Key.SPACE to "Space",
        Key.DELETE to "Del", Key.UP to "Up", Key.DOWN to "Down", Key.LEFT to "Left", Key.RIGHT to "Right",
        Key.HOME to "Home", Key.END to "End", Key.PAGE_UP to "PgUp", Key.PAGE_DOWN to "PgDn",
    )

    fun name(usage: Int): String = when (usage) {
        in special.keys -> special.getValue(usage)
        in 0x04..0x1D -> ('A' + (usage - 0x04)).toString()
        in 0x1E..0x26 -> ('1' + (usage - 0x1E)).toString()
        0x27 -> "0"
        in 0x3A..0x45 -> "F${usage - 0x3A + 1}"
        0x00 -> ""
        else -> "0x%02X".format(usage)
    }

    fun consumer(usage: Int): String = when (usage) {
        Consumer.PLAY_PAUSE -> "Play/Pause"
        Consumer.NEXT -> "Next"
        Consumer.PREV -> "Previous"
        Consumer.STOP -> "Stop"
        Consumer.VOL_UP -> "Volume up"
        Consumer.VOL_DOWN -> "Volume down"
        Consumer.MUTE -> "Mute"
        Consumer.HOME -> "Home"
        Consumer.BACK -> "Back"
        Consumer.MENU -> "Menu"
        0x221 -> "Search"
        else -> "0x%X".format(usage)
    }
}

/** Ready-made step lists for common "open something" shortcuts, per target operating system. */
object Templates {
    private const val OS_WINDOWS = 0
    private const val OS_MAC = 1
    private const val OS_ANDROID_TV = 3
    private const val KEY_R = 0x15
    private const val KEY_SPACE = Key.SPACE
    private const val KEY_F2 = 0x3B

    fun openWebsite(os: Int, url: String): List<Step> = when (os) {
        OS_WINDOWS -> listOf(Step.Combo(Mod.GUI, listOf(KEY_R)), Step.Wait(600), Step.Text(url), Step.Wait(150), Step.Combo(0, listOf(Key.ENTER)))
        OS_MAC -> listOf(Step.Combo(Mod.GUI, listOf(KEY_SPACE)), Step.Wait(600), Step.Text(url), Step.Wait(300), Step.Combo(0, listOf(Key.ENTER)))
        // GNOME: Alt+F2 opens the run prompt.
        else -> listOf(Step.Combo(Mod.ALT, listOf(KEY_F2)), Step.Wait(700), Step.Text("xdg-open $url"), Step.Wait(150), Step.Combo(0, listOf(Key.ENTER)))
    }

    fun openApp(os: Int, name: String): List<Step> = when (os) {
        OS_WINDOWS -> listOf(Step.Combo(Mod.GUI, emptyList()), Step.Wait(700), Step.Text(name), Step.Wait(900), Step.Combo(0, listOf(Key.ENTER)))
        OS_MAC -> listOf(Step.Combo(Mod.GUI, listOf(KEY_SPACE)), Step.Wait(600), Step.Text(name), Step.Wait(600), Step.Combo(0, listOf(Key.ENTER)))
        OS_ANDROID_TV -> listOf(Step.Media(0x221), Step.Wait(2200), Step.Text(name), Step.Wait(1800), Step.Combo(0, listOf(Key.ENTER)))
        else -> listOf(Step.Combo(Mod.GUI, emptyList()), Step.Wait(800), Step.Text(name), Step.Wait(900), Step.Combo(0, listOf(Key.ENTER)))
    }
}

/** Persists shortcuts as JSON in settings. */
class ShortcutStore(private val settings: AppSettings) {
    private val state = mutableStateOf(load())

    var all: List<Shortcut>
        get() = state.value
        private set(v) { state.value = v; settings.shortcutsJson = encode(v) }

    fun find(id: String) = all.firstOrNull { it.id == id }

    fun save(sc: Shortcut) {
        all = if (find(sc.id) != null) all.map { if (it.id == sc.id) sc else it } else all + sc
    }

    fun delete(id: String) { all = all.filterNot { it.id == id } }

    private fun load(): List<Shortcut> {
        val raw = settings.shortcutsJson
        if (raw.isEmpty()) return defaults()
        return runCatching { decode(raw) }.getOrElse { defaults() }
    }

    companion object {
        fun defaults(): List<Shortcut> = listOf(
            Shortcut("d_copy", "Copy", "copy", listOf(Step.Combo(Mod.CTRL, listOf(0x06)))),
            Shortcut("d_paste", "Paste", "paste", listOf(Step.Combo(Mod.CTRL, listOf(0x19)))),
            Shortcut("d_desk", "Desktop", "home", listOf(Step.Combo(Mod.GUI, listOf(0x07)))),
            Shortcut("d_lock", "Lock", "lock", listOf(Step.Combo(Mod.GUI, listOf(0x0F)))),
            Shortcut("d_snip", "Snip", "screenshot", listOf(Step.Combo(Mod.GUI or Mod.SHIFT, listOf(0x16)))),
            Shortcut("d_mute", "Mic mute", "micoff", listOf(Step.Combo(Mod.CTRL or Mod.SHIFT, listOf(0x10)))),
        )

        fun encode(list: List<Shortcut>): String = JSONArray().also { arr ->
            list.forEach { sc ->
                arr.put(
                    JSONObject().put("id", sc.id).put("name", sc.name).put("icon", sc.icon)
                        .put("steps", JSONArray().also { st -> sc.steps.forEach { st.put(encodeStep(it)) } }),
                )
            }
        }.toString()

        private fun encodeStep(s: Step): JSONObject = when (s) {
            is Step.Combo -> JSONObject().put("t", "combo").put("m", s.mods).put("k", JSONArray(s.keys))
            is Step.Text -> JSONObject().put("t", "text").put("v", s.text)
            is Step.Media -> JSONObject().put("t", "media").put("v", s.usage)
            is Step.Wait -> JSONObject().put("t", "wait").put("v", s.ms)
            is Step.Click -> JSONObject().put("t", "click").put("v", s.button)
        }

        fun decode(raw: String): List<Shortcut> {
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val st = o.getJSONArray("steps")
                Shortcut(o.getString("id"), o.getString("name"), o.optString("icon", "bolt"), (0 until st.length()).map { decodeStep(st.getJSONObject(it)) })
            }
        }

        private fun decodeStep(o: JSONObject): Step = when (o.getString("t")) {
            "combo" -> Step.Combo(o.getInt("m"), o.getJSONArray("k").let { a -> (0 until a.length()).map { a.getInt(it) } })
            "text" -> Step.Text(o.getString("v"))
            "media" -> Step.Media(o.getInt("v"))
            "wait" -> Step.Wait(o.getInt("v"))
            else -> Step.Click(o.getInt("v"))
        }
    }
}

/** Plays a shortcut's steps in order. A new run cancels the previous one. */
class MacroRunner(private val hid: HidController, private val scope: CoroutineScope) {
    private var job: Job? = null

    fun run(sc: Shortcut) {
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            for (step in sc.steps) {
                when (step) {
                    is Step.Combo -> hid.keyCombo(step.mods, *step.keys.toIntArray())
                    is Step.Text -> for (c in step.text) {
                        // Characters outside US ASCII have no key code and are skipped.
                        hid.typeChar(c)
                        delay(14)
                    }
                    is Step.Media -> hid.consumer(step.usage)
                    is Step.Wait -> delay(step.ms.toLong())
                    is Step.Click -> hid.click(step.button)
                }
                delay(40)
            }
        }
    }
}
