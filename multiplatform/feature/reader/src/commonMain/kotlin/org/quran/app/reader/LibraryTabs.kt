package org.quran.app.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.quran.app.designsystem.QuranSpacing
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranChoiceChip
import org.quran.app.designsystem.appString

@Composable
internal fun LibraryTabs(section: LibrarySection, onSelect: (LibrarySection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Small),
    ) {
        for (item in LibrarySection.entries) {
            val title = when (item) {
                LibrarySection.SURAHS -> appString(QuranStrings.surahTab)
                LibrarySection.JUZ -> appString(QuranStrings.juzTab)
                LibrarySection.BOOKMARKS -> appString(QuranStrings.bookmarksTab)
            }
            QuranChoiceChip(
                text = title,
                selected = section == item,
                onClick = { onSelect(item) },
            )
        }
    }
}
