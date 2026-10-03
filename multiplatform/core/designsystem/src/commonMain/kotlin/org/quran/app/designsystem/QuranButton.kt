package org.quran.app.designsystem

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuranButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(onClick, modifier.sizeIn(minWidth = QuranSpacing.TouchTarget, minHeight = QuranSpacing.TouchTarget), enabled = enabled && !loading, shape = MaterialTheme.shapes.medium) {
        ButtonLabel(text, loading)
    }
}
