package org.quran.app.memorization

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
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
        QuranText(appString(QuranStrings.repetitions, state.completedRepetitions))
        Row {
            QuranIconButton(appString(QuranStrings.decreaseRepetitions), { onCountChanged(count - 1) }, enabled = count > 1) { QuranText("−") }
            QuranText("$count")
            QuranIconButton(appString(QuranStrings.increaseRepetitions), { onCountChanged(count + 1) }, enabled = count < 20) { QuranText("+") }
        }
        QuranSettingSwitch(appString(QuranStrings.repeatUntilMemorized), appString(QuranStrings.repeatUntilHelp), untilMemorized, onUntilChanged)
        Action(appString(QuranStrings.repeatedVerse), onManualRepeat, !state.complete && !playing)
        Action(appString(QuranStrings.iMemorizedThis), onMemorized, canMarkMemorized)
        if (state.complete) QuranText(appString(QuranStrings.sessionComplete))
        QuranTextButton(appString(QuranStrings.startAgain), onReset)
    }
}
