package org.quran.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.ArabicVerse
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (initialAyah - 1).coerceAtLeast(0))

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun VerseReaderItem(
    verse: Verse,
    progress: StudyProgress,
    translationForVerse: @Composable (VerseId) -> Unit,
    onRead: (VerseId) -> Unit,
    onBookmark: (VerseId) -> Unit,
    onPractice: (VerseId) -> Unit,
    onStudy: (VerseId) -> Unit,
) {
    var actionsVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${verse.id.surah}:${verse.id.ayah}", style = MaterialTheme.typography.labelLarge)
            if (verse.id == progress.lastRead) Text(appString(QuranStrings.currentReadingPosition))
        }
        ArabicVerse(verse.arabic, progress.childMode)
        translationForVerse(verse.id)
        TextButton(onClick = { actionsVisible = true }) { Text(appString(QuranStrings.moreActions)) }
    }

    if (actionsVisible) {
        AlertDialog(
            onDismissRequest = { actionsVisible = false },
            title = { Text("${verse.id.surah}:${verse.id.ayah}") },
            text = {
                Column {
                    TextButton(onClick = { onRead(verse.id); actionsVisible = false }) {
                        Text(appString(QuranStrings.markAsRead))
                    }
                    TextButton(onClick = { onBookmark(verse.id); actionsVisible = false }) {
                        Text(appString(if (verse.id in progress.bookmarks) QuranStrings.removeBookmark else QuranStrings.saveBookmark))
                    }
                    TextButton(onClick = { onPractice(verse.id); actionsVisible = false }) {
                        Text(appString(QuranStrings.practice))
                    }
                    TextButton(onClick = { onStudy(verse.id); actionsVisible = false }) {
                        Text(appString(QuranStrings.study))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsVisible = false }) { Text(appString(QuranStrings.back)) }
            },
        )
    }
}
