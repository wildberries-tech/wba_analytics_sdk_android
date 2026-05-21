package ru.wildanalytics.pub.analytics.sdk.demo.analytics.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DemoSpacer(size: Dp = 8.dp) {
    Spacer(modifier = Modifier.size(size))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    isDense: Boolean = false
) {
    if (isDense) {
        val interactionSource = remember { MutableInteractionSource() }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.heightIn(min = 40.dp),
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = error != null,
                    label = { Text(text = label, style = MaterialTheme.typography.bodySmall) },
                    colors = OutlinedTextFieldDefaults.colors(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = error != null,
                            interactionSource = interactionSource,
                            colors = OutlinedTextFieldDefaults.colors(),
                            shape = OutlinedTextFieldDefaults.shape
                        )
                    }
                )
            }
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = label) },
            modifier = modifier,
            isError = error != null,
            textStyle = textStyle,
            supportingText = error?.let {
                {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
