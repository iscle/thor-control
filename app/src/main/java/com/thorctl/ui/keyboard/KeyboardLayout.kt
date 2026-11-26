package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardLayout.rows
import com.thorctl.ui.keyboard.KeyboardLayout.ACCENTS
import androidx.compose.runtime.key
import com.thorctl.platform.keyboard.KeyboardLayer as Layer

/** The keys of each layer and the accents behind them. */
internal object KeyboardLayout {
    val LETTER_ROWS = listOf("q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m", ", .")
    val SYMBOL_ROWS = listOf("1 2 3 4 5 6 7 8 9 0", "@ # \$ & * ( ) ' \"", "% - + = / ; :", "! ?")
    val MORE_ROWS = listOf("[ ] { } ^ ~ < > | \\", "_ € £ ¥ ¢ • ° … ¶", "© ® ™ ✓ × ÷ §", "¡ ¿")

    fun rows(layer: Layer): List<List<Key>> {
        val (main, alt) = when (layer) {
            Layer.LETTERS -> LETTER_ROWS to SYMBOL_ROWS
            Layer.SYMBOLS -> SYMBOL_ROWS to MORE_ROWS
            Layer.MORE -> MORE_ROWS to null
        }
        fun row(i: Int): List<Ch> {
            val alts = alt?.get(i)?.split(' ')
            return main[i].split(' ').mapIndexed { j, c -> Ch(c, alts?.getOrNull(j)) }
        }
        val r2 = row(1).let { r -> r.mapIndexed { j, k -> k.copy(lead = if (j == 0) 0.5f else 0f, trail = if (j == r.lastIndex) 0.5f else 0f) } }
        val r4 = row(3)
        val (left3, left4) = when (layer) {
            Layer.LETTERS -> FnKind.SHIFT to FnKind.TO_SYMBOLS
            Layer.SYMBOLS -> FnKind.TO_MORE to FnKind.TO_LETTERS
            Layer.MORE -> FnKind.TO_SYMBOLS to FnKind.TO_LETTERS
        }
        return listOf(
            row(0),
            r2,
            listOf(Fn(left3, 1.5f)) + row(2) + Fn(FnKind.BACK, 1.5f),
            listOf(Fn(left4, 1.5f), r4[0], Fn(FnKind.SPACE, 5f), r4[1], Fn(FnKind.ENTER, 1.5f)),
        )
    }

    /** Hold a key for these (slide along the strip, release to type). */
    val ACCENTS: Map<String, List<String>> = mapOf(
        "a" to "à á â ä æ ã å ā", "c" to "ç ć č", "d" to "ď ð", "e" to "è é ê ë ē ė ę ě", "g" to "ğ",
        "i" to "î ï í ī į ì", "l" to "ł", "n" to "ñ ń ň", "o" to "ô ö ò ó œ ø ō õ", "r" to "ř",
        "s" to "ß ś š", "t" to "ť þ", "u" to "û ü ù ú ū ů", "y" to "ÿ ý", "z" to "ž ź ż",
        "-" to "– — ·", "\$" to "€ £ ¥ ¢", "!" to "¡", "?" to "¿", "'" to "‘ ’ ‚", "\"" to "“ ” „ « »",
        "." to "…", "0" to "°", "%" to "‰", "*" to "★ †",
    ).mapValues { it.value.split(' ') }
}
