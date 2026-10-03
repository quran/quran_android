package org.quran.app.reader

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.model.Chapter
import org.quran.app.model.StudyProgress

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
