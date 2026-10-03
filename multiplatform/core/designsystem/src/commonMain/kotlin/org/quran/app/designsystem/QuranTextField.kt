package org.quran.app.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuranTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { QuranText(label, variant = QuranTextVariant.Supporting) },
        modifier = modifier.fillMaxWidth(), enabled = enabled, isError = isError,
        supportingText = supportingText?.let { text -> { QuranText(text, variant = QuranTextVariant.Caption) } },
        singleLine = singleLine, keyboardOptions = keyboardOptions,
    )
}
