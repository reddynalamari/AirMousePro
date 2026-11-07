package com.airmousepro.hid

/** HID keyboard usages (page 0x07) and consumer usages (page 0x0C). */
object Key {
    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val DELETE = 0x4C
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val HOME = 0x4A
    const val END = 0x4D
    const val PAGE_UP = 0x4B
    const val PAGE_DOWN = 0x4E
    fun f(n: Int) = 0x3A + (n - 1)
}

object Mod {
    const val CTRL = 0x01
    const val SHIFT = 0x02
    const val ALT = 0x04
    const val GUI = 0x08
}

object Consumer {
    const val PLAY_PAUSE = 0xCD
    const val NEXT = 0xB5
    const val PREV = 0xB6
    const val STOP = 0xB7
    const val VOL_UP = 0xE9
    const val VOL_DOWN = 0xEA
    const val MUTE = 0xE2
    const val HOME = 0x223
    const val BACK = 0x224
    const val MENU = 0x40
}

object KeyMap {
    private const val SHIFTED = "!@#$%^&*()"

    /** Returns (modifiers, usage) for a US-layout character, or null if unsupported. */
    fun forChar(c: Char): Pair<Int, Int>? = when (c) {
        in 'a'..'z' -> 0 to 0x04 + (c - 'a')
        in 'A'..'Z' -> Mod.SHIFT to 0x04 + (c - 'A')
        in '1'..'9' -> 0 to 0x1E + (c - '1')
        '0' -> 0 to 0x27
        in SHIFTED -> Mod.SHIFT to if (c == ')') 0x27 else 0x1E + SHIFTED.indexOf(c)
        '\n' -> 0 to Key.ENTER
        '\t' -> 0 to Key.TAB
        ' ' -> 0 to Key.SPACE
        '-' -> 0 to 0x2D
        '_' -> Mod.SHIFT to 0x2D
        '=' -> 0 to 0x2E
        '+' -> Mod.SHIFT to 0x2E
        '[' -> 0 to 0x2F
        '{' -> Mod.SHIFT to 0x2F
        ']' -> 0 to 0x30
        '}' -> Mod.SHIFT to 0x30
        '\\' -> 0 to 0x31
        '|' -> Mod.SHIFT to 0x31
        ';' -> 0 to 0x33
        ':' -> Mod.SHIFT to 0x33
        '\'' -> 0 to 0x34
        '"' -> Mod.SHIFT to 0x34
        '`' -> 0 to 0x35
        '~' -> Mod.SHIFT to 0x35
        ',' -> 0 to 0x36
        '<' -> Mod.SHIFT to 0x36
        '.' -> 0 to 0x37
        '>' -> Mod.SHIFT to 0x37
        '/' -> 0 to 0x38
        '?' -> Mod.SHIFT to 0x38
        else -> null
    }
}
