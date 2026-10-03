package org.quran.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.model.Chapter
import org.quran.app.model.StudyProgress
import org.quran.app.model.Verse
import org.quran.app.model.VerseId

@Composable
fun ReaderScreen(
    chapter: Chapter,
    verses: List<Verse>,
    initialAyah: Int,
    progress: StudyProgress,
    translationSummary: @Composable () -> Unit,
    translationForVerse: @Composable (VerseId) -> Unit,
    onRead: (VerseId) -> Unit,
    onBookmark: (VerseId) -> Unit,
    onPractice: (VerseId) -> Unit,
    onStudy: (VerseId) -> Unit,
) {
    // The chapter header occupies index zero; retain it when opening from the start.
    val initialIndex = if (initialAyah <= 1) 0 else initialAyah.coerceAtMost(verses.size)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("reader_list"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            ScreenTitle(chapter.arabicName, chapter.englishName)
            Text(appString(QuranStrings.readerOfflineText), style = MaterialTheme.typography.bodySmall)
            translationSummary()
        }
        items(verses, key = { it.id.ayah }) { verse ->
            VerseReaderItem(
                verse = verse,
                progress = progress,
                translationForVerse = translationForVerse,
                onRead = onRead,
                onBookmark = onBookmark,
                onPractice = onPractice,
                onStudy = onStudy,
            )
        }
    }
}
