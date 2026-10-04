package org.quran.app.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
                modifier = Modifier
                    .testTag("library_tab_${item.name.lowercase()}")
                    .semantics {
                        role = Role.Tab
                        selected = section == item
                    },
            )
        }
    }
}
