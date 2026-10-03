package org.quran.app.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ButtonLabel(text: String, loading: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Small), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(QuranSpacing.ExtraLarge), color = LocalContentColor.current, strokeWidth = 2.dp)
        QuranText(text, variant = QuranTextVariant.Label)
    }
}
