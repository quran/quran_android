package org.quran.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.QuranSpacing
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextButton
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.appString
import org.quran.app.model.Chapter

@Composable
internal fun SurahListItem(chapter: Chapter, onOpen: (Int, Int) -> Unit) {
    val readLabel = appString(QuranStrings.read)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = QuranSpacing.TouchTarget)
                .testTag("surah_open_${chapter.number}")
                .clickable(onClickLabel = readLabel, role = Role.Button) { onOpen(chapter.number, 1) }
                .padding(vertical = QuranSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                    .padding(QuranSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                QuranText(chapter.number.toString(), variant = QuranTextVariant.Label, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuranSpacing.ExtraSmall)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Medium), verticalArrangement = Arrangement.spacedBy(QuranSpacing.ExtraSmall)) {
                    QuranText(chapter.englishName)
                    Text(chapter.arabicName, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, textDirection = TextDirection.Rtl))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Small), verticalArrangement = Arrangement.spacedBy(QuranSpacing.ExtraSmall)) {
                    QuranText(appString(QuranStrings.verseCount, chapter.verseCount), variant = QuranTextVariant.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuranText(appString(QuranStrings.availableOffline), variant = QuranTextVariant.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            QuranTextButton(readLabel, onClick = { onOpen(chapter.number, 1) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
