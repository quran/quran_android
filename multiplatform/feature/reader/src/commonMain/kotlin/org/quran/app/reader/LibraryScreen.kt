package org.quran.app.reader

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.quran.app.designsystem.QuranSpacing
import org.quran.app.designsystem.QuranIconButton
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
    val filteredChapters = remember(chapters, query) { filterChapters(chapters, query) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuranTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = appString(QuranStrings.searchSurah),
                        modifier = Modifier.weight(1f).testTag("library_search_field"),
                        singleLine = true,
                    )
                    if (query.isNotEmpty()) {
                        QuranIconButton(
                            contentDescription = appString(QuranStrings.clearSearch),
                            onClick = { query = "" },
                            modifier = Modifier.testTag("library_search_clear"),
                        ) {
                            QuranText("×", variant = QuranTextVariant.Label)
                        }
                    }
                }
            }
            items(filteredChapters, key = Chapter::number) { chapter -> SurahListItem(chapter, onOpen) }
        } else if (section == LibrarySection.JUZ) {
            juzItems(juzs, onOpen)
        } else {
            bookmarkItems(progress.bookmarks, onOpen)
        }
    }
}

internal fun filterChapters(chapters: List<Chapter>, query: String): List<Chapter> {
    val normalizedQuery = normalizeLibrarySearch(query)
    if (normalizedQuery.isEmpty()) return chapters

    return chapters.filter { chapter ->
        normalizedQuery == chapter.number.toString() ||
            normalizeLibrarySearch(chapter.englishName).contains(normalizedQuery) ||
            normalizeLibrarySearch(chapter.arabicName).contains(normalizedQuery)
    }
}

private fun normalizeLibrarySearch(value: String): String = buildString {
    for (character in value.lowercase()) {
        when {
            character in '\u064B'..'\u065F' || character == '\u0670' ||
                character in '\u06D6'..'\u06ED' || character == '\u0640' -> Unit
            character in '\u0660'..'\u0669' -> append(('0'.code + character.code - '\u0660'.code).toChar())
            character in '\u06F0'..'\u06F9' -> append(('0'.code + character.code - '\u06F0'.code).toChar())
            character.isLetterOrDigit() -> append(
                when (character) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى', 'ئ' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    else -> character
                },
            )
        }
    }
}
