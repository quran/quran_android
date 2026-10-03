package org.quran.app.designsystem

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuranChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.sizeIn(minHeight = QuranSpacing.TouchTarget),
        enabled = enabled,
        label = { QuranText(text, variant = QuranTextVariant.Label) },
    )
}
