package org.quran.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.model.Chapter

@Composable
internal fun SurahListItem(chapter: Chapter, onOpen: (Int, Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(chapter.number.toString(), style = MaterialTheme.typography.titleMedium)
            Text(chapter.arabicName, style = MaterialTheme.typography.titleLarge)
        }
        Text(chapter.englishName, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(appString(QuranStrings.verseCount, chapter.verseCount))
            Text("·")
            Text(appString(QuranStrings.availableOffline))
        }
        TextButton(onClick = { onOpen(chapter.number, 1) }) { Text(appString(QuranStrings.read)) }
        HorizontalDivider()
    }
}
