package org.quran.app.memorization

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import org.quran.app.designsystem.*
import org.quran.app.model.VerseId

@Composable
fun PracticeRangeCard(start: VerseId, endAyah: Int, maxEndAyah: Int, maxSize: Int, onEndChanged: (Int) -> Unit) {
    PaperCard {
        QuranText(appString(QuranStrings.practiceRange), variant = QuranTextVariant.Title)
        QuranText(appString(QuranStrings.practiceRangeHelp, maxSize))
        QuranText(appString(QuranStrings.practiceRangeAddress, start.surah, start.ayah, endAyah))
        Row {
            QuranIconButton(appString(QuranStrings.decreaseRange), { onEndChanged(endAyah - 1) }, enabled = endAyah > start.ayah) { QuranText("−") }
            QuranIconButton(appString(QuranStrings.increaseRange), { onEndChanged(endAyah + 1) }, enabled = endAyah < maxEndAyah) { QuranText("+") }
        }
    }
}
