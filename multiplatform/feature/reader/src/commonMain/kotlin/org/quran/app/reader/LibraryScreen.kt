package org.quran.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.Action
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.model.Chapter
import org.quran.app.model.StudyProgress
import org.quran.app.model.VerseId

private enum class LibrarySection { SURAHS, BOOKMARKS }

@Composable
fun LibraryScreen(
    chapters: List<Chapter>,
    progress: StudyProgress,
    onOpen: (surah: Int, ayah: Int) -> Unit,
) {
    var section by remember { mutableStateOf(LibrarySection.SURAHS) }
    var query by remember { mutableStateOf("") }
    val filteredChapters = remember(chapters, query) {
        chapters.filter { chapter ->
            chapter.englishName.contains(query, ignoreCase = true) ||
                chapter.arabicName.contains(query) ||
                chapter.number.toString() == query
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ScreenTitle(appString(QuranStrings.libraryTitle), appString(QuranStrings.librarySubtitle)) }
        item { ContinueReadingCard(progress.lastRead, onOpen) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = section == LibrarySection.SURAHS,
                    onClick = { section = LibrarySection.SURAHS },
                    label = { Text(appString(QuranStrings.surahTab)) },
                )
                FilterChip(
                    selected = section == LibrarySection.BOOKMARKS,
                    onClick = { section = LibrarySection.BOOKMARKS },
                    label = { Text(appString(QuranStrings.bookmarksTab)) },
                )
            }
        }
        if (section == LibrarySection.SURAHS) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(appString(QuranStrings.searchSurah)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            items(filteredChapters, key = Chapter::number) { chapter -> SurahListItem(chapter, onOpen) }
        } else {
            bookmarkItems(progress.bookmarks, onOpen)
        }
    }
}

@Composable
private fun ContinueReadingCard(lastRead: VerseId, onOpen: (Int, Int) -> Unit) {
    PaperCard {
        Text(appString(QuranStrings.continueReading), style = MaterialTheme.typography.titleLarge)
        Text("${lastRead.surah}:${lastRead.ayah}", style = MaterialTheme.typography.headlineSmall)
        Action(appString(QuranStrings.openLastRead), onClick = { onOpen(lastRead.surah, lastRead.ayah) })
    }
}

@Composable
private fun SurahListItem(chapter: Chapter, onOpen: (Int, Int) -> Unit) {
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

private fun androidx.compose.foundation.lazy.LazyListScope.bookmarkItems(
    bookmarks: Set<VerseId>,
    onOpen: (Int, Int) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        item {
            PaperCard {
                Text(appString(QuranStrings.noBookmarks), style = MaterialTheme.typography.titleLarge)
                Text(appString(QuranStrings.bookmarkHelp))
            }
        }
        return
    }
    items(bookmarks.sortedWith(compareBy(VerseId::surah, VerseId::ayah)), key = { it }) { verse ->
        Column(Modifier.fillMaxWidth()) {
            Text("${verse.surah}:${verse.ayah}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onOpen(verse.surah, verse.ayah) }) { Text(appString(QuranStrings.read)) }
            HorizontalDivider()
        }
    }
}
