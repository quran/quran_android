package org.quran.app.designsystem

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuranTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    TextButton(onClick, modifier.sizeIn(minWidth = QuranSpacing.TouchTarget, minHeight = QuranSpacing.TouchTarget), enabled = enabled && !loading) {
        ButtonLabel(text, loading)
    }
}
