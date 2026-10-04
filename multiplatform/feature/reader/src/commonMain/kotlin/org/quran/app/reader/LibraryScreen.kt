package org.quran.app.reader

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.quran.app.designsystem.QuranSpacing
import org.quran.app.designsystem.QuranTextField
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.appString
import org.quran.app.model.Juz
import org.quran.app.model.Chapter
import org.quran.app.model.StudyProgress

@Composable
fun LibraryScreen(
    chapters: List<Chapter>,
    progress: StudyProgress,
    juzs: List<Juz>,
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
        modifier = Modifier.fillMaxSize().testTag("library_list"),
        verticalArrangement = Arrangement.spacedBy(QuranSpacing.Medium),
        contentPadding = PaddingValues(bottom = QuranSpacing.ExtraLarge),
    ) {
        item { QuranText(appString(QuranStrings.librarySubtitle), variant = QuranTextVariant.Supporting) }
        item { ContinueReadingCard(progress.lastRead, onOpen) }
        item { LibraryTabs(section, onSelect = { section = it }) }
        if (section == LibrarySection.SURAHS) {
            item {
                QuranTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = appString(QuranStrings.searchSurah),
                    singleLine = true,
                )
            }
            items(filteredChapters, key = Chapter::number) { chapter -> SurahListItem(chapter, onOpen) }
        } else if (section == LibrarySection.JUZ) {
            juzItems(juzs, onOpen)
        } else {
            bookmarkItems(progress.bookmarks, onOpen)
        }
    }
}
