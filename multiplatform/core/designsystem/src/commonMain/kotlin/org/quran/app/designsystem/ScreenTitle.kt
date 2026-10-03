package org.quran.app.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = QuranSpacing.Large), verticalArrangement = Arrangement.spacedBy(QuranSpacing.Small)) {
        QuranText(title, Modifier.semantics { heading() }, QuranTextVariant.Heading)
        QuranText(subtitle, variant = QuranTextVariant.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
