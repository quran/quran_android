package org.quran.app.designsystem

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun QuranIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    require(contentDescription.isNotBlank()) { "An icon action needs an accessible description" }
    IconButton(onClick, modifier.sizeIn(minWidth = QuranSpacing.TouchTarget, minHeight = QuranSpacing.TouchTarget).semantics { this.contentDescription = contentDescription }, enabled = enabled, content = icon)
}
