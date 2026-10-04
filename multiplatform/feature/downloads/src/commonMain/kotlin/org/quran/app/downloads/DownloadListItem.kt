package org.quran.app.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextButton
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.appString
import org.quran.app.downloads.toKibibytesRoundedUp
import org.quran.app.model.RecitationDownload
import org.quran.app.model.AppLanguage

@Composable
internal fun DownloadListItem(
    download: RecitationDownload,
    language: AppLanguage,
    removing: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val reciterName = when (download.reciterId) {
        "alafasy" -> appString(if (language == AppLanguage.ARABIC) QuranStrings.reciterAlafasyArabic else QuranStrings.reciterAlafasy)
        "husary" -> appString(if (language == AppLanguage.ARABIC) QuranStrings.reciterHusaryArabic else QuranStrings.reciterHusary)
        "sudais" -> appString(if (language == AppLanguage.ARABIC) QuranStrings.reciterSudaisArabic else QuranStrings.reciterSudais)
        else -> download.reciterId
    }
    val removeLabel = appString(QuranStrings.removeDownloadDescription, reciterName, download.verseId.surah, download.verseId.ayah)
    PaperCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(org.quran.app.designsystem.QuranSpacing.Medium),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(org.quran.app.designsystem.QuranSpacing.Small)) {
                QuranText(reciterName, variant = QuranTextVariant.Title)
                QuranText(appString(QuranStrings.downloadVerseAddress, download.verseId.surah, download.verseId.ayah))
                QuranText(appString(QuranStrings.downloadItemSize, download.sizeBytes.toKibibytesRoundedUp()), variant = QuranTextVariant.Supporting)
            }
            QuranTextButton(
                text = appString(if (removing) QuranStrings.removingDownload else QuranStrings.removeDownload),
                onClick = onRemove,
                modifier = Modifier.semantics { onClick(label = removeLabel, action = null) }.testTag("remove_download_${download.reciterId}_${download.verseId.surah}_${download.verseId.ayah}").sizeIn(minWidth = org.quran.app.designsystem.QuranSpacing.TouchTarget, minHeight = org.quran.app.designsystem.QuranSpacing.TouchTarget),
                enabled = enabled,
                loading = removing,
            )
        }
    }
}
