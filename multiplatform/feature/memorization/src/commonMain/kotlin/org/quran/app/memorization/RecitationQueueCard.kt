package org.quran.app.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
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
    onReciterSelected: (String) -> Unit,
    onAudioReady: (Map<VerseId, String>) -> Unit,
    onAudioCleared: () -> Unit,
    onDownloadStateChanged: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ready = rememberUpdatedState(onAudioReady)
    val cleared = rememberUpdatedState(onAudioCleared)
    val controller = remember(repository) {
        RecitationQueueController(repository, scope, { ready.value(it) }, { cleared.value() })
    }
    val state by controller.state.collectAsState()
    LaunchedEffect(state.isDownloading) { onDownloadStateChanged(state.isDownloading) }
    LaunchedEffect(selectedReciterId, verses) { controller.select(selectedReciterId, verses) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    PaperCard {
        QuranText(appString(QuranStrings.reciterAudio), variant = QuranTextVariant.Title)
        QuranText(appString(QuranStrings.rangeAudioHelp))
        Column(verticalArrangement = Arrangement.spacedBy(QuranSpacing.Small)) {
            repository.reciters().forEach { reciter ->
                QuranChoiceChip(
                    text = if (isArabic) reciter.nameArabic else reciter.nameEnglish,
                    selected = reciter.id == selectedReciterId,
                    onClick = { cleared.value(); onReciterSelected(reciter.id) },
                )
            }
        }
        Action(
            appString(when {
                state.isDownloading -> QuranStrings.audioDownloading
                state.cachedCount == verses.size -> QuranStrings.useCachedRange
                else -> QuranStrings.downloadRange
            }),
            { cleared.value(); onDownloadStateChanged(true); controller.prepareAudio() },
            !state.isDownloading && state.reciterId == selectedReciterId && state.verses == verses,
        )
        if (state.isDownloading) QuranText(appString(QuranStrings.audioDownloadProgress, state.completedDownloads, verses.size))
        if (state.failed) QuranText(appString(QuranStrings.audioDownloadFailed))
        QuranText(appString(QuranStrings.audioSourceCredit), variant = QuranTextVariant.Caption)
    }
}
