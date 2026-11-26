package com.thorctl.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorctl.ui.keyboard.KeyboardColors.Accent
import com.thorctl.ui.keyboard.KeyboardColors.KeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import com.thorctl.ui.keyboard.KeyboardColors.KeyShadow
import com.thorctl.ui.keyboard.KeyboardColors.KeyShape
import kotlin.math.roundToInt

@Composable
internal fun AccentPopup(popup: KeyPopup) {
    if (popup.options.isEmpty()) return
    val density = LocalDensity.current
    Row(
        Modifier
            .offset { IntOffset(popup.left.roundToInt(), popup.top.roundToInt()) }
            .size(
                with(density) { (popup.cellWidth * popup.options.size).toDp() },
                with(density) { popup.cellHeight.toDp() })
            .shadow(10.dp, KeyShape, ambientColor = KeyShadow, spotColor = KeyShadow)
            .clip(KeyShape)
            .background(KeyFill)
            .padding(4.dp),
    ) {
        popup.options.forEachIndexed { index, option ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (index == popup.selected) Accent else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    color = if (index == popup.selected) Color.White else KeyInk,
                    fontSize = 24.sp
                )
            }
        }
    }
}
