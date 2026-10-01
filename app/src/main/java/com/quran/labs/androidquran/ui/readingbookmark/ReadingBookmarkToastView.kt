package com.quran.labs.androidquran.ui.readingbookmark

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.labs.androidquran.feature.reading.presenter.ReadingBookmarkChange
import com.quran.page.common.data.QuranNaming

internal fun createReadingBookmarkToastView(
  context: Context,
  change: ReadingBookmarkChange,
  isEducation: Boolean,
  quranNaming: QuranNaming,
  onUndo: () -> Unit,
  onDismiss: () -> Unit
): ComposeView {
  return ComposeView(context).apply {
    setContent {
      QuranTheme {
        ReadingBookmarkToastContent(
          change = change,
          isEducation = isEducation,
          quranNaming = quranNaming,
          onUndo = onUndo,
          onDismiss = onDismiss
        )
      }
    }
  }
}

@Composable
private fun ReadingBookmarkToastContent(
  change: ReadingBookmarkChange,
  isEducation: Boolean,
  quranNaming: QuranNaming,
  onUndo: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val slotName = ReadingBookmarkSlots.displayName(change.slot, change.name)
  val previousLocation = change.previous?.let { quranNaming.readingBookmarkLabel(context, it) }

  val title = when {
    isEducation -> stringResource(R.string.reading_bookmark_movable_education_title)
    change is ReadingBookmarkChange.Placed -> {
      val location = quranNaming.targetLabel(context, change.target)
      if (previousLocation != null) {
        stringResource(R.string.reading_bookmark_moved_to_title, slotName, location)
      } else {
        stringResource(R.string.reading_bookmark_placed_title, slotName, location)
      }
    }
    else -> stringResource(R.string.reading_bookmark_cleared_title, slotName)
  }

  val subtitle = when {
    previousLocation == null -> null
    change is ReadingBookmarkChange.Cleared ->
      stringResource(R.string.reading_bookmark_was_at, previousLocation)
    else -> stringResource(R.string.reading_bookmark_moved_from, previousLocation)
  }
  val body = if (isEducation) stringResource(R.string.reading_bookmark_movable_education_body) else null

  ReadingBookmarkMovedToast(
    title = title,
    movedFromText = subtitle,
    body = body,
    onUndo = onUndo,
    onDismiss = onDismiss.takeIf { isEducation },
    modifier = Modifier.padding(14.dp)
  )
}

private fun QuranNaming.readingBookmarkLabel(context: Context, bookmark: ReadingBookmark): String {
  return when (bookmark) {
    is AyahReadingBookmark -> getSuraAyahString(context, bookmark.sura, bookmark.ayah)
    is PageReadingBookmark -> getSuraPageString(context, bookmark.page)
    // shouldn't happen since in this case, previous bookmark should be null
    is EmptyReadingBookmark -> ""
  }
}

private fun QuranNaming.targetLabel(context: Context, target: ReadingBookmarkTarget): String {
  return when (target) {
    is ReadingBookmarkTarget.Page -> getSuraPageString(context, target.page)
    is ReadingBookmarkTarget.Ayah ->
      getSuraAyahString(context, target.suraAyah.sura, target.suraAyah.ayah)
  }
}
