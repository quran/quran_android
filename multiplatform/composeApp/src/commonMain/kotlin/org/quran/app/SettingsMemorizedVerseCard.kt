package org.quran.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.quran.app.designsystem.Action
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextButton
import org.quran.app.designsystem.appString
import org.quran.app.model.VerseId

@Composable
internal fun SettingsMemorizedVerseCard(
    verseId: VerseId,
    onRead: (VerseId) -> Unit,
    onReview: (VerseId) -> Unit,
    onNeedsPractice: (VerseId) -> Unit,
) {
    val address = "${verseId.surah}:${verseId.ayah}"
    PaperCard {
        QuranText(address)
        Action(
            text = appString(QuranStrings.openVerse),
            onClick = { onRead(verseId) },
            modifier = Modifier.testTag("memorized_read_${verseId.surah}_${verseId.ayah}"),
        )
        Action(
            text = appString(QuranStrings.reviewVerse),
            onClick = { onReview(verseId) },
            modifier = Modifier.testTag("memorized_review_${verseId.surah}_${verseId.ayah}"),
        )
        QuranTextButton(
            text = appString(QuranStrings.needsPractice),
            onClick = { onNeedsPractice(verseId) },
            modifier = Modifier.testTag("memorized_remove_${verseId.surah}_${verseId.ayah}"),
        )
    }
}
