package org.quran.app.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable

@Composable
fun QuranDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { QuranText(title, variant = QuranTextVariant.Title) },
        text = content, confirmButton = confirmButton, dismissButton = dismissButton,
    )
}
