package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.KeyboardTop
import com.thorctl.ui.keyboard.KeyboardColors.KeyboardBottom
import com.thorctl.ui.keyboard.KeyboardColors.KeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyPressed
import com.thorctl.ui.keyboard.KeyboardColors.FunctionKeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import com.thorctl.ui.keyboard.KeyboardColors.KeyInkSoft
import com.thorctl.ui.keyboard.KeyboardColors.Accent
import com.thorctl.ui.keyboard.KeyboardColors.AccentDeep
import com.thorctl.ui.keyboard.KeyboardColors.PadFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyShape
import com.thorctl.ui.keyboard.KeyboardColors.KeyShadow
import com.thorctl.ui.keyboard.KeyboardColors.LipColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Cocoon's keyboard look: white rounded keys with a soft shadow on the bottom screen's gradient. */
internal object KeyboardColors {
    val KeyboardTop = Color(0xFFEFEFF3)
    val KeyboardBottom = Color(0xFFCFCFDA)
    val KeyFill = Color(0xFFFFFFFF)
    val KeyPressed = Color(0xFFE2E2EA)
    /** Function keys (shift, backspace, layer keys, enter): iOS's darker grey. */
    val FunctionKeyFill = Color(0xFFADB3BC)
    val KeyInk = Color(0xFF1B1B1F)
    val KeyInkSoft = Color(0xFF6B6B78)
    val Accent = Color(0xFF6AA6F8)
    val AccentDeep = Color(0xFF4F8EF0)
    val PadFill = Color(0x8CFFFFFF)
    val KeyShape = RoundedCornerShape(12.dp)
    val KeyShadow = Color(0x40000000)
    val LipColor = Color(0xFF9A9BA6)
}
