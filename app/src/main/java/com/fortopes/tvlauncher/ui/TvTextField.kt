package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * A text box for the remote. Pressing OK on it brings up the TV keyboard, and the Google Home app's
 * remote keyboard can type or paste into it from your phone.
 */
@Composable
fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
    secret: Boolean = false,
    imeAction: ImeAction = ImeAction.Go,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth()) {
        Text(label, color = TvColors.Muted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(color = TvColors.Text, fontSize = 18.sp),
            cursorBrush = SolidColor(TvColors.Text),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }, onDone = { onSubmit() }, onNext = { onSubmit() }),
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .background(TvColors.Card, RoundedCornerShape(8.dp))
                .border(
                    BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f)),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
