package org.quran.app.memorization

import androidx.compose.runtime.Composable
import org.quran.app.designsystem.*

@Composable
fun ImportedRecordingCard(singleAyah: Boolean, enabled: Boolean, message: String, onImport: () -> Unit) {
    PaperCard {
        QuranText(appString(QuranStrings.ownRecitation), variant = QuranTextVariant.Title)
        QuranText(appString(if (singleAyah) QuranStrings.importRecordingHelp else QuranStrings.importSingleAyahHelp))
        if (singleAyah) Action(appString(QuranStrings.importRecording), onImport, enabled)
        if (message.isNotEmpty()) QuranText(message)
    }
}
