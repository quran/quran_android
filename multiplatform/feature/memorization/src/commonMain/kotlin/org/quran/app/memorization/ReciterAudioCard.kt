package org.quran.app.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import org.quran.app.designsystem.*
import org.quran.app.domain.RecitationRepository
import org.quran.app.model.VerseId

/** Downloads only after an explicit action; stale requests cannot replace another selection. */
@Composable
fun ReciterAudioCard(
    verseId: VerseId,
    repository: RecitationRepository,
    selectedReciterId: String,
    isArabic: Boolean,
    onReciterSelected: (String) -> Unit,
    onAudioReady: (String) -> Unit,
    onAudioCleared: () -> Unit,
    onDownloadStateChanged: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ready = rememberUpdatedState(onAudioReady)
    val cleared = rememberUpdatedState(onAudioCleared)
    val controller = remember(repository) {
        RecitationAudioController(repository, scope, { ready.value(it) }, { cleared.value() })
    }
    val state by controller.state.collectAsState()
    LaunchedEffect(state.isDownloading) { onDownloadStateChanged(state.isDownloading) }
    LaunchedEffect(selectedReciterId, verseId) { controller.select(selectedReciterId, verseId) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    PaperCard {
        QuranText(appString(QuranStrings.reciterAudio))
        QuranText(appString(QuranStrings.audioCacheHelp))
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
                state.cachedUri != null -> QuranStrings.useCachedRecitation
                else -> QuranStrings.downloadAyah
            }),
            { cleared.value(); controller.prepareAudio() },
            !state.isDownloading && state.reciterId == selectedReciterId,
        )
        if (state.failed) QuranText(appString(QuranStrings.audioDownloadFailed))
        QuranText(appString(QuranStrings.audioSourceCredit))
    }
}
