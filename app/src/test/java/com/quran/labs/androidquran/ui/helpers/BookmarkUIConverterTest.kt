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
import com.quran.labs.androidquran.R
import com.quran.mobile.common.ui.core.R as CoreR
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

  @Test
  fun `reading bookmark rows carry their pin's colour, juz' and when it was placed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val quranInfo = QuranInfo(MadaniDataSource())
    val converter = BookmarkUIConverter(
      QuranRowFactory(quranInfo, QuranDisplayData(quranInfo)),
      quranInfo
    )
    val fourMinutesAgo = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 4 * 60_000)
    val data = BookmarkRawResult(
      rows = listOf(
        BookmarkRowData.ReadingBookmarkHeader(2),
        BookmarkRowData.ReadingBookmarkItem(
          AyahReadingBookmark(ReadingBookmarkType.CORAL, 4, 6, fourMinutesAgo)
        ),
        BookmarkRowData.ReadingBookmarkItem(
          PageReadingBookmark(ReadingBookmarkType.INDIGO, 293, fourMinutesAgo)
        ),
        BookmarkRowData.ReadingBookmarkHeader(1),
        BookmarkRowData.ReadingBookmarkItem(
          PageReadingBookmark(
            ReadingBookmarkType.TEAL, 50, Instant.fromEpochMilliseconds(System.currentTimeMillis())
          )
        )
      ),
      tagMap = emptyMap()
    )

    val (header, coral, indigo, singleHeader, justPlaced) =
      converter.convertToUIResult(context, data).rows

    assertThat(header.text).isEqualTo("Reading Bookmarks")
    assertThat(singleHeader.text).isEqualTo("Reading Bookmark")

    assertThat(coral.text).isEqualTo("Surah An-Nisāʾ - Ayah 6")
    assertThat(coral.metadata).isEqualTo("Juz' 4 · 4 minutes ago")
    assertThat(coral.page).isEqualTo(77)
    assertThat(coral.imageResource).isEqualTo(R.drawable.ic_bookmark_filled_24)
    assertThat(coral.imageFilterColorResource).isEqualTo(CoreR.color.reading_bookmark_coral)
    // the colour is what tells pins apart on screen, so the name is what a screen reader says
    assertThat(coral.imageContentDescription).isEqualTo("Coral")

    assertThat(indigo.text).isEqualTo("Surah Al-Kahf")
    assertThat(indigo.metadata).isEqualTo("Juz' 15 · 4 minutes ago")
    assertThat(indigo.page).isEqualTo(293)
    assertThat(indigo.imageFilterColorResource).isEqualTo(CoreR.color.reading_bookmark_indigo)
    assertThat(indigo.imageContentDescription).isEqualTo("Indigo")

    // rather than "0 minutes ago"
    assertThat(justPlaced.metadata).isEqualTo("Juz' 3 · 1 minute ago")
  }

  @Test
  fun `highlighted ayah rows read by their text when there is one`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val quranInfo = QuranInfo(MadaniDataSource())
    val converter = BookmarkUIConverter(
      QuranRowFactory(quranInfo, QuranDisplayData(quranInfo)),
      quranInfo
    )
    val highlight =
      Highlight(SuraAyah(2, 255), HighlightColor.BLUE, Instant.parse("2023-11-14T22:13:20Z"))
    val data = BookmarkRawResult(
      rows = listOf(
        BookmarkRowData.HighlightedAyahItem(highlight, "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ"),
        BookmarkRowData.HighlightedAyahItem(highlight)
      ),
      tagMap = emptyMap()
    )

    val (withText, withoutText) = converter.convertToUIResult(context, data).rows

    assertThat(withText.text).isEqualTo("ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ...")
    assertThat(withText.metadata).isEqualTo("Surah Al-Baqarah - Ayah 255, Juz' 3")
    assertThat(withoutText.text).isEqualTo("Surah Al-Baqarah - Ayah 255")
    assertThat(withoutText.metadata).isEqualTo("Page 42, Juz' 3")
  }

  @Test
  @Config(qualifiers = "ar")
  fun `reading bookmarks header and pin names are translated to arabic`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val quranInfo = QuranInfo(MadaniDataSource())
    val factory = QuranRowFactory(quranInfo, QuranDisplayData(quranInfo))

    assertThat(factory.fromReadingBookmarkHeader(context, 1).text).isEqualTo("علامة موضع القراءة")
    // arabic has its own "two" and "few" forms; both fall back to the plural
    assertThat(factory.fromReadingBookmarkHeader(context, 2).text).isEqualTo("علامات موضع القراءة")
    assertThat(factory.fromReadingBookmarkHeader(context, 3).text).isEqualTo("علامات موضع القراءة")

    // the names a screen reader says for each pin's icon
    val timestamp = Instant.parse("2023-11-14T22:13:20Z")
    assertThat(
      ReadingBookmarkType.entries.map { slot ->
        factory.fromReadingBookmark(context, PageReadingBookmark(slot, 42, timestamp))
          .imageContentDescription
      }
    ).containsExactly("مرجاني", "فيروزي", "نيلي").inOrder()
  }
}
