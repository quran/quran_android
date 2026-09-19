package com.quran.labs.androidquran.ui.helpers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkUIConverterTest {
  @Test
  fun `instant and legacy seconds timestamps produce the same row date`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val quranInfo = QuranInfo(MadaniDataSource())
    val converter = BookmarkUIConverter(
      QuranRowFactory(quranInfo, QuranDisplayData(quranInfo)),
      quranInfo
    )
    val timestamp = Instant.parse("2023-11-14T22:13:20Z")
    val data = BookmarkRawResult(
      rows = listOf(
        BookmarkRowData.RecentPage(RecentPage(42, timestamp)),
        BookmarkRowData.ReadingBookmarkItem(
          PageReadingBookmark(ReadingBookmarkType.TEAL, 42, timestamp)
        ),
        BookmarkRowData.ReadingBookmarkItem(
          AyahReadingBookmark(ReadingBookmarkType.CORAL, 2, 255, timestamp)
        ),
        BookmarkRowData.BookmarkItem(Bookmark("1", 2, 255, 42, timestamp.epochSeconds)),
        BookmarkRowData.HighlightedAyahItem(
          Highlight(SuraAyah(2, 255), HighlightColor.BLUE, timestamp)
        )
      ),
      tagMap = emptyMap()
    )

    val result = converter.convertToUIResult(context, data)

    assertThat(result.rows.map { it.dateAddedInMillis })
      .containsExactly(
        1_700_000_000_000L,
        1_700_000_000_000L,
        1_700_000_000_000L,
        1_700_000_000_000L,
        1_700_000_000_000L
      )
  }
}
