package org.quran.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.quran.app.designsystem.*
import org.quran.app.model.ReadingTextSize

@Composable
internal fun ReadingTextSizeChoices(title: String, tag: String, selected: ReadingTextSize, onSelected: (ReadingTextSize) -> Unit) {
    QuranText(title, variant = QuranTextVariant.Supporting)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Small)) {
        ReadingTextSize.entries.forEach { size ->
            QuranChoiceChip(
                appString(if (size == ReadingTextSize.LARGE) QuranStrings.textSizeLarge else QuranStrings.textSizeDefault),
                selected == size,
                { onSelected(size) },
                modifier = Modifier.testTag("reading_${tag}_${size.name.lowercase()}"),
            )
        }
    }
}
