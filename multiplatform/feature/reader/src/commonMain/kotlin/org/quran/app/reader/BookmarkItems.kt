package org.quran.app.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.model.VerseId

internal fun androidx.compose.foundation.lazy.LazyListScope.bookmarkItems(
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
    items(bookmarks.sortedWith(compareBy(VerseId::surah, VerseId::ayah)), key = { "bookmark-${it.surah}-${it.ayah}" }) { verse ->
        Column(Modifier.fillMaxWidth()) {
            Text("${verse.surah}:${verse.ayah}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onOpen(verse.surah, verse.ayah) }) { Text(appString(QuranStrings.read)) }
            HorizontalDivider()
        }
    }
}
