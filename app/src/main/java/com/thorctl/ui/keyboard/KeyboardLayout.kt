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
        val (mainRows, alternateRows) = when (layer) {
            Layer.LETTERS -> LETTER_ROWS to SYMBOL_ROWS
            Layer.SYMBOLS -> SYMBOL_ROWS to MORE_ROWS
            Layer.MORE -> MORE_ROWS to null
        }
        fun row(rowIndex: Int): List<Ch> {
            val alternates = alternateRows?.get(rowIndex)?.split(' ')
            return mainRows[rowIndex].split(' ').mapIndexed { keyIndex, character -> Ch(character, alternates?.getOrNull(keyIndex)) }
        }
        val secondRow = row(1).let { keys -> keys.mapIndexed { keyIndex, charKey -> charKey.copy(lead = if (keyIndex == 0) 0.5f else 0f, trail = if (keyIndex == keys.lastIndex) 0.5f else 0f) } }
        val bottomRow = row(3)
        val (thirdRowLeft, bottomRowLeft) = when (layer) {
            Layer.LETTERS -> FnKind.SHIFT to FnKind.TO_SYMBOLS
            Layer.SYMBOLS -> FnKind.TO_MORE to FnKind.TO_LETTERS
            Layer.MORE -> FnKind.TO_SYMBOLS to FnKind.TO_LETTERS
        }
        return listOf(
            row(0),
            secondRow,
            listOf(Fn(thirdRowLeft, 1.5f)) + row(2) + Fn(FnKind.BACK, 1.5f),
            listOf(Fn(bottomRowLeft, 1.5f), bottomRow[0], Fn(FnKind.SPACE, 5f), bottomRow[1], Fn(FnKind.ENTER, 1.5f)),
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
