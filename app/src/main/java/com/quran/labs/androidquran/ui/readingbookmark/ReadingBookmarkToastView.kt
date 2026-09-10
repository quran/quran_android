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
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.page.common.data.QuranNaming

/** Creates the non-modal Compose view driven by `ReadingBookmarkPresenter`. */
internal fun createReadingBookmarkToastView(
  context: Context,
  previousBookmark: ReadingBookmark?,
  isEducation: Boolean,
  quranNaming: QuranNaming,
  onUndo: () -> Unit,
  onConfirm: () -> Unit
): ComposeView {
  return ComposeView(context).apply {
    setContent {
      QuranTheme {
        ReadingBookmarkToastContent(
          isEducation = isEducation,
          previousBookmark = previousBookmark,
          quranNaming = quranNaming,
          onUndo = onUndo,
          onConfirm = onConfirm
        )
      }
    }
  }
}

@Composable
private fun ReadingBookmarkToastContent(
  isEducation: Boolean,
  previousBookmark: ReadingBookmark?,
  quranNaming: QuranNaming,
  onUndo: () -> Unit,
  onConfirm: () -> Unit
) {
  val context = LocalContext.current
  val movedFromLocation = previousBookmark?.let { quranNaming.readingBookmarkLabel(context, it) }

  val title = when {
    isEducation -> stringResource(R.string.reading_bookmark_movable_education_title)
    movedFromLocation != null -> stringResource(R.string.reading_bookmark_moved_from_title, movedFromLocation)
    else -> stringResource(R.string.reading_bookmark_added)
  }
  val movedFromText = if (isEducation && movedFromLocation != null) {
    stringResource(R.string.reading_bookmark_moved_from, movedFromLocation)
  } else {
    null
  }
  val body = if (isEducation) stringResource(R.string.reading_bookmark_movable_education_body) else null

  ReadingBookmarkMovedToast(
    title = title,
    movedFromText = movedFromText,
    body = body,
    onUndo = onUndo.takeIf { movedFromLocation != null },
    onDismiss = onConfirm.takeIf { isEducation },
    modifier = Modifier.padding(14.dp)
  )
}

private fun QuranNaming.readingBookmarkLabel(context: Context, bookmark: ReadingBookmark): String {
  return when (bookmark) {
    is AyahReadingBookmark -> getSuraAyahString(context, bookmark.sura, bookmark.ayah)
    is PageReadingBookmark -> getSuraPageString(context, bookmark.page)
  }
}
