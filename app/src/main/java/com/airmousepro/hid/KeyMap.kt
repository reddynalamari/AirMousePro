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
    /** Right Alt, which European layouts use as AltGr for symbols such as @ { [. */
    const val ALTGR = 0x40
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

/**
 * Keys are sent as physical key positions, so the same letter needs a different code depending on the layout the
 * computer is using. Pick the layout that matches the computer, not the phone.
 */
enum class KbLayout(val label: String) {
    US("US"), UK("UK"), DE("German"), FR("French"), ES("Spanish"),
}

object KeyMap {
    private const val S = Mod.SHIFT
    private const val G = Mod.ALTGR

    /** US layout. Also the base for letters, digits and whitespace in every layout. */
    private fun us(c: Char): Pair<Int, Int>? = when (c) {
        in 'a'..'z' -> 0 to 0x04 + (c - 'a')
        in 'A'..'Z' -> S to 0x04 + (c - 'A')
        in '1'..'9' -> 0 to 0x1E + (c - '1')
        '0' -> 0 to 0x27
        '!' -> S to 0x1E
        '@' -> S to 0x1F
        '#' -> S to 0x20
        '$' -> S to 0x21
        '%' -> S to 0x22
        '^' -> S to 0x23
        '&' -> S to 0x24
        '*' -> S to 0x25
        '(' -> S to 0x26
        ')' -> S to 0x27
        '\n' -> 0 to Key.ENTER
        '\t' -> 0 to Key.TAB
        ' ' -> 0 to Key.SPACE
        '-' -> 0 to 0x2D
        '_' -> S to 0x2D
        '=' -> 0 to 0x2E
        '+' -> S to 0x2E
        '[' -> 0 to 0x2F
        '{' -> S to 0x2F
        ']' -> 0 to 0x30
        '}' -> S to 0x30
        '\\' -> 0 to 0x31
        '|' -> S to 0x31
        ';' -> 0 to 0x33
        ':' -> S to 0x33
        '\'' -> 0 to 0x34
        '"' -> S to 0x34
        '`' -> 0 to 0x35
        '~' -> S to 0x35
        ',' -> 0 to 0x36
        '<' -> S to 0x36
        '.' -> 0 to 0x37
        '>' -> S to 0x37
        '/' -> 0 to 0x38
        '?' -> S to 0x38
        else -> null
    }

    /** UK: shares most of US; differs in quotes, @, #, ~, backslash and the pound sign. */
    private val uk = mapOf(
        '"' to (S to 0x1F), '@' to (S to 0x34), '\'' to (0 to 0x34), '#' to (0 to 0x32), '~' to (S to 0x32),
        '\\' to (0 to 0x64), '|' to (S to 0x64), '£' to (S to 0x20), '€' to (G to 0x21),
    )

    private val de = mapOf(
        'y' to (0 to 0x1D), 'Y' to (S to 0x1D), 'z' to (0 to 0x1C), 'Z' to (S to 0x1C),
        '!' to (S to 0x1E), '"' to (S to 0x1F), '§' to (S to 0x20), '$' to (S to 0x21), '%' to (S to 0x22),
        '&' to (S to 0x23), '/' to (S to 0x24), '(' to (S to 0x25), ')' to (S to 0x26), '=' to (S to 0x27),
        'ß' to (0 to 0x2D), '?' to (S to 0x2D), '\\' to (G to 0x2D),
        '+' to (0 to 0x30), '*' to (S to 0x30), '~' to (G to 0x30),
        '#' to (0 to 0x32), '\'' to (S to 0x32),
        'ü' to (0 to 0x2F), 'Ü' to (S to 0x2F), 'ö' to (0 to 0x33), 'Ö' to (S to 0x33), 'ä' to (0 to 0x34), 'Ä' to (S to 0x34),
        ',' to (0 to 0x36), ';' to (S to 0x36), '.' to (0 to 0x37), ':' to (S to 0x37), '-' to (0 to 0x38), '_' to (S to 0x38),
        '<' to (0 to 0x64), '>' to (S to 0x64), '|' to (G to 0x64),
        '@' to (G to 0x14), '€' to (G to 0x08),
        '{' to (G to 0x24), '[' to (G to 0x25), ']' to (G to 0x26), '}' to (G to 0x27),
    )

    private val fr = mapOf(
        'a' to (0 to 0x14), 'A' to (S to 0x14), 'q' to (0 to 0x04), 'Q' to (S to 0x04),
        'z' to (0 to 0x1A), 'Z' to (S to 0x1A), 'w' to (0 to 0x1D), 'W' to (S to 0x1D),
        'm' to (0 to 0x33), 'M' to (S to 0x33),
        // Digits need Shift on AZERTY; the unshifted row gives & é " ' ( - è _ ç à.
        '1' to (S to 0x1E), '2' to (S to 0x1F), '3' to (S to 0x20), '4' to (S to 0x21), '5' to (S to 0x22),
        '6' to (S to 0x23), '7' to (S to 0x24), '8' to (S to 0x25), '9' to (S to 0x26), '0' to (S to 0x27),
        '&' to (0 to 0x1E), 'é' to (0 to 0x1F), '"' to (0 to 0x20), '\'' to (0 to 0x21), '(' to (0 to 0x22),
        '-' to (0 to 0x23), 'è' to (0 to 0x24), '_' to (0 to 0x25), 'ç' to (0 to 0x26), 'à' to (0 to 0x27),
        ')' to (0 to 0x2D), '=' to (0 to 0x2E), '°' to (S to 0x2D), '+' to (S to 0x2E),
        '$' to (0 to 0x30), '£' to (S to 0x30), '*' to (0 to 0x32), 'µ' to (S to 0x32),
        'ù' to (0 to 0x34), '%' to (S to 0x34),
        ',' to (0 to 0x10), '?' to (S to 0x10), ';' to (0 to 0x36), '.' to (S to 0x36),
        ':' to (0 to 0x37), '/' to (S to 0x37), '!' to (0 to 0x38), '§' to (S to 0x38),
        '<' to (0 to 0x64), '>' to (S to 0x64),
        '@' to (G to 0x27), '#' to (G to 0x20), '{' to (G to 0x21), '[' to (G to 0x22), '|' to (G to 0x23),
        '\\' to (G to 0x25), ']' to (G to 0x2D), '}' to (G to 0x2E), '€' to (G to 0x08),
    )

    private val es = mapOf(
        '!' to (S to 0x1E), '"' to (S to 0x1F), '·' to (S to 0x20), '$' to (S to 0x21), '%' to (S to 0x22),
        '&' to (S to 0x23), '/' to (S to 0x24), '(' to (S to 0x25), ')' to (S to 0x26), '=' to (S to 0x27),
        '\'' to (0 to 0x2D), '?' to (S to 0x2D), '¡' to (0 to 0x2E), '¿' to (S to 0x2E),
        '+' to (0 to 0x30), '*' to (S to 0x30), 'ñ' to (0 to 0x33), 'Ñ' to (S to 0x33),
        'ç' to (0 to 0x32), 'Ç' to (S to 0x32),
        ',' to (0 to 0x36), ';' to (S to 0x36), '.' to (0 to 0x37), ':' to (S to 0x37), '-' to (0 to 0x38), '_' to (S to 0x38),
        '<' to (0 to 0x64), '>' to (S to 0x64),
        '@' to (G to 0x1F), '#' to (G to 0x20), '|' to (G to 0x1E), '€' to (G to 0x22),
        '\\' to (G to 0x35), '[' to (G to 0x2F), ']' to (G to 0x30), '{' to (G to 0x34), '}' to (G to 0x32),
    )

    /**
     * Returns (modifiers, usage) for [c] on [layout], or null when the character cannot be typed there.
     * Non-US layouts only fall back to the US table for letters, digits and whitespace, because punctuation
     * moves around and a wrong guess would type the wrong symbol.
     */
    fun forChar(c: Char, layout: KbLayout = KbLayout.US): Pair<Int, Int>? {
        val table = when (layout) {
            KbLayout.US -> return us(c)
            KbLayout.UK -> uk
            KbLayout.DE -> de
            KbLayout.FR -> fr
            KbLayout.ES -> es
        }
        table[c]?.let { return it }
        val base = us(c) ?: return null
        val plain = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == ' ' || c == '\n' || c == '\t'
        return if (plain || layout == KbLayout.UK) base else null
    }
}
