package org.quran.app.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compatibility entry point for existing feature actions. */
@Composable
fun Action(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    QuranButton(text, onClick, modifier, enabled)
}
