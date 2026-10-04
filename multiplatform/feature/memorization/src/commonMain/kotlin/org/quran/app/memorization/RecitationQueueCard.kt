package org.quran.app.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.quran.app.designsystem.*
import org.quran.app.domain.RecitationRepository
import org.quran.app.model.VerseId

/** Explicitly prepares the complete range before enabling playback. */
@Composable
fun RecitationQueueCard(
    verses: List<VerseId>,
    repository: RecitationRepository,
    selectedReciterId: String,
    isArabic: Boolean,
    state: RecitationQueueState,
    onReciterSelected: (String) -> Unit,
    onPrepareAudio: () -> Unit,
) {
    PaperCard {
        QuranText(appString(QuranStrings.reciterAudio), variant = QuranTextVariant.Title)
        QuranText(appString(QuranStrings.rangeAudioHelp))
        Column(verticalArrangement = Arrangement.spacedBy(QuranSpacing.Small)) {
            repository.reciters().forEach { reciter ->
                QuranChoiceChip(
                    text = if (isArabic) reciter.nameArabic else reciter.nameEnglish,
                    selected = reciter.id == selectedReciterId,
                    onClick = { onReciterSelected(reciter.id) },
                )
            }
        }
        Action(
            appString(when {
                state.isDownloading -> QuranStrings.audioDownloading
                state.cachedCount == verses.size -> QuranStrings.useCachedRange
                else -> QuranStrings.downloadRange
            }),
            onPrepareAudio,
            !state.isDownloading && state.reciterId == selectedReciterId && state.verses == verses,
        )
        if (state.isDownloading) QuranText(appString(QuranStrings.audioDownloadProgress, state.completedDownloads, verses.size))
        if (state.failed) QuranText(appString(QuranStrings.audioDownloadFailed))
        QuranText(appString(QuranStrings.audioSourceCredit), variant = QuranTextVariant.Caption)
    }
}
