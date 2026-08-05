package sa.hulksa.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Compact Login-only overload.
 *
 * The existing shared HulkTextField API remains unchanged. Calls that explicitly
 * provide [compact] use this overload so short portrait Login windows can keep
 * both credential fields exposed while the software keyboard is visible.
 */
@Composable
fun HulkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    compact: Boolean,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(Color(0xFF12130F))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.gold else colors.line,
                shape = shape,
            )
            .padding(
                horizontal = if (compact) 12.dp else 15.dp,
                vertical = if (compact) 8.dp else 13.dp,
            ),
        singleLine = true,
        textStyle = TextStyle(
            color = colors.text,
            fontSize = if (compact) 13.sp else 15.sp,
            textAlign = TextAlign.Start,
        ),
        cursorBrush = Brush.verticalGradient(listOf(colors.gold, colors.gold)),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerField ->
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        color = colors.textMuted,
                        fontSize = if (compact) 12.sp else 14.sp,
                    )
                }
                innerField()
            }
        },
    )
}
