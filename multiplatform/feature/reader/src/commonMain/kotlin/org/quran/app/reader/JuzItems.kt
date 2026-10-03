package org.quran.app.reader

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import org.quran.app.model.Juz

internal fun LazyListScope.juzItems(juzs: List<Juz>, onOpen: (Int, Int) -> Unit) {
    items(juzs, key = { "juz-${it.number}" }) { juz -> JuzListItem(juz, onOpen) }
}
