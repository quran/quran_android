package org.quran.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranSpacing
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextButton
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.appString

@Composable
internal fun ReaderAudioCard(
    state: ReaderListeningState,
    reciterName: String,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        state.failed -> appString(QuranStrings.readerAudioFailed)
        state.isPreparing -> appString(QuranStrings.readerAudioPreparing)
        state.isPlaying -> appString(QuranStrings.readerAudioPlaying)
        state.isReady -> appString(QuranStrings.readerAudioReady)
        else -> appString(QuranStrings.readerAudioChooseVerse)
    }
    PaperCard(modifier) {
        QuranText(appString(QuranStrings.readerAudioTitle), variant = QuranTextVariant.Title)
        if (reciterName.isNotBlank()) {
            QuranText(appString(QuranStrings.readerAudioReciter, reciterName), variant = QuranTextVariant.Supporting)
        }
        val verseId = state.verseId
        if (verseId != null) {
            QuranText(appString(QuranStrings.readerAudioVerse, verseId.surah, verseId.ayah), variant = QuranTextVariant.Label)
        }
        QuranText(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, variant = QuranTextVariant.Supporting)
        QuranText(appString(QuranStrings.audioSourceCredit), variant = QuranTextVariant.Caption)
        if (state.isPreparing || state.isReady || state.failed || state.verseId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Small),
            ) {
                when {
                    state.failed -> QuranTextButton(
                        text = appString(QuranStrings.readerAudioRetry),
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                    state.isPreparing -> QuranTextButton(
                        text = appString(QuranStrings.readerAudioPreparing),
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false,
                        loading = true,
                    )
                    state.isReady -> QuranTextButton(
                        text = appString(if (state.isPlaying) QuranStrings.readerAudioPause else QuranStrings.readerAudioPlay),
                        onClick = onTogglePlayback,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.verseId != null) {
                    QuranTextButton(
                        text = appString(QuranStrings.readerAudioStop),
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
