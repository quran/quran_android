package org.quran.app.memorization

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.quran.app.designsystem.*
import org.quran.app.domain.RepeatState

@Composable
fun PracticeRepeatCard(
    state: RepeatState,
    count: Int,
    untilMemorized: Boolean,
    playing: Boolean,
    canMarkMemorized: Boolean,
    onCountChanged: (Int) -> Unit,
    onUntilChanged: (Boolean) -> Unit,
    onManualRepeat: () -> Unit,
    onMemorized: () -> Unit,
    onReset: () -> Unit,
) {
    PaperCard {
        QuranText(
            appString(QuranStrings.repetitions, state.completedRepetitions),
            modifier = Modifier
                .testTag("practice_progress")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row {
            QuranIconButton(appString(QuranStrings.decreaseRepetitions), { onCountChanged(count - 1) }, enabled = count > 1) { QuranText("−") }
            QuranText("$count")
            QuranIconButton(appString(QuranStrings.increaseRepetitions), { onCountChanged(count + 1) }, enabled = count < 20) { QuranText("+") }
        }
        QuranSettingSwitch(appString(QuranStrings.repeatUntilMemorized), appString(QuranStrings.repeatUntilHelp), untilMemorized, onUntilChanged)
        Action(appString(QuranStrings.repeatedVerse), onManualRepeat, !state.complete && !playing)
        Action(appString(QuranStrings.iMemorizedThis), onMemorized, canMarkMemorized)
        if (state.complete) {
            QuranText(
                appString(QuranStrings.sessionComplete),
                modifier = Modifier
                    .testTag("practice_complete")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        QuranTextButton(appString(QuranStrings.startAgain), onReset)
    }
}
