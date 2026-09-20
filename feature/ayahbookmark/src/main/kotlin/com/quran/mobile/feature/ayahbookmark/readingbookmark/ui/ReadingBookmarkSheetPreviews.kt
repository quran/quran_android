package com.quran.mobile.feature.ayahbookmark.readingbookmark.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.isAt
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetState
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSlotItem
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Clock

private val previewTargetNameResolver: (Context, ReadingBookmarkTarget) -> String =
  { _, target ->
    when (target) {
      is ReadingBookmarkTarget.Page -> "An-Nisāʾ (Page ${target.page})"
      is ReadingBookmarkTarget.Ayah -> "Surah An-Nisāʾ, Ayah ${target.suraAyah.ayah}"
    }
  }

private val previewLocationNameResolver: (Context, ReadingBookmark) -> String =
  { _, bookmark ->
    when (bookmark) {
      is PageReadingBookmark -> "An-Nisāʾ (Page ${bookmark.page})"
      is AyahReadingBookmark -> "Surah An-Nisāʾ, Ayah ${bookmark.ayah} · Page 77"
      else -> ""
    }
  }

private fun previewState(
  target: ReadingBookmarkTarget,
  bookmarks: Map<ReadingBookmarkType, ReadingBookmark?>,
  isNested: Boolean = false,
  isEditing: Boolean = false,
  names: Map<ReadingBookmarkType, String> = emptyMap()
) = ReadingBookmarkSheetState(
  target = target,
  isNested = isNested,
  isEditing = isEditing,
  slots = ReadingBookmarkType.entries.map { slot ->
    val bookmark = bookmarks[slot]
    ReadingBookmarkSlotItem(
      slot = slot,
      name = names[slot],
      draftName = names[slot].orEmpty(),
      bookmark = bookmark,
      isAtTarget = bookmark?.isAt(target) == true
    )
  }.toImmutableList(),
  targetNameResolver = previewTargetNameResolver,
  locationNameResolver = previewLocationNameResolver
)

@Composable
private fun PreviewScaffold(state: ReadingBookmarkSheetState) {
  QuranTheme {
    Surface {
      Box(modifier = Modifier.fillMaxWidth()) {
        ReadingBookmarkSheet(state = state)
      }
    }
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("arabic", locale = "ar")
@Composable
private fun ReadingBookmarkSheetPagePreview() {
  PreviewScaffold(
    previewState(
      target = ReadingBookmarkTarget.Page(77),
      bookmarks = mapOf(
        ReadingBookmarkType.CORAL to PageReadingBookmark(
          ReadingBookmarkType.CORAL, 77, Clock.System.now()
        ),
        ReadingBookmarkType.TEAL to AyahReadingBookmark(
          ReadingBookmarkType.TEAL, 4, 6, Clock.System.now()
        ),
        ReadingBookmarkType.INDIGO to null
      )
    )
  )
}

@Preview("nothing placed")
@Preview("nothing placed (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingBookmarkSheetNothingPlacedPreview() {
  PreviewScaffold(
    previewState(
      target = ReadingBookmarkTarget.Page(77),
      bookmarks = emptyMap()
    )
  )
}

@Preview("ayah context, nested in the ayah sheet")
@Preview("ayah context (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingBookmarkSheetAyahPreview() {
  PreviewScaffold(
    previewState(
      target = ReadingBookmarkTarget.Ayah(SuraAyah(4, 6)),
      isNested = true,
      bookmarks = mapOf(
        ReadingBookmarkType.CORAL to PageReadingBookmark(
          ReadingBookmarkType.CORAL, 77, Clock.System.now()
        ),
        ReadingBookmarkType.TEAL to AyahReadingBookmark(
          ReadingBookmarkType.TEAL, 4, 6, Clock.System.now()
        ),
        ReadingBookmarkType.INDIGO to null
      )
    )
  )
}

@Preview("editing, with a renamed pin")
@Preview("editing (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingBookmarkSheetEditingPreview() {
  PreviewScaffold(
    previewState(
      target = ReadingBookmarkTarget.Page(77),
      isEditing = true,
      names = mapOf(ReadingBookmarkType.CORAL to "Tafsir study"),
      bookmarks = mapOf(
        ReadingBookmarkType.CORAL to PageReadingBookmark(
          ReadingBookmarkType.CORAL, 77, Clock.System.now()
        ),
        ReadingBookmarkType.TEAL to AyahReadingBookmark(
          ReadingBookmarkType.TEAL, 4, 6, Clock.System.now()
        ),
        ReadingBookmarkType.INDIGO to null
      )
    )
  )
}
