package org.quran.app

import androidx.compose.runtime.Composable
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextButton
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.appString

@Composable
internal fun SettingsDownloadsCard(onManageDownloads: () -> Unit) {
    PaperCard {
        QuranText(appString(QuranStrings.downloadsTitle), variant = QuranTextVariant.Title)
        QuranText(appString(QuranStrings.downloadsSettingsHelp), variant = QuranTextVariant.Supporting)
        QuranTextButton(appString(QuranStrings.manageDownloads), onClick = onManageDownloads)
    }
}
