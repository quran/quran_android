package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class LegacyBookmarkMigrationNormalizerTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var quranInfo: QuranInfo
  private lateinit var normalizer: LegacyBookmarkMigrationNormalizer

  @Before
  fun setUp() {
    quranInfo = QuranInfo(MadaniDataSource())
    normalizer = LegacyBookmarkMigrationNormalizer(context, quranInfo)
  }

  @Test
  fun `page bookmarks become ayah bookmarks in old page bookmarks collection`() {
    val page = 50
    val pageBounds = quranInfo.getPageBounds(page)
    val oldPageBookmarksName = context.getString(R.string.old_page_bookmarks)

    val data = normalizer.normalize(
      LegacyBookmarksSnapshot(
        tags = listOf(
          LegacyBookmarkTag(10L, "Reading", 100L),
          LegacyBookmarkTag(11L, oldPageBookmarksName, 200L),
          LegacyBookmarkTag(12L, oldPageBookmarksName, 300L)
        ),
        bookmarks = listOf(
          Bookmark(1L, null, null, page, 1000L, tags = listOf(10L, 12L))
        ),
        recentPages = emptyList()
      )
    )

    assertThat(data.bookmarks).hasSize(1)
    assertThat(data.bookmarks.single().sura).isEqualTo(pageBounds[0])
    assertThat(data.bookmarks.single().ayah).isEqualTo(pageBounds[1])
    assertThat(data.bookmarks.single().timestampSeconds).isEqualTo(1000L)
    assertThat(data.collections.map { collection -> collection.name })
      .containsExactly("Reading", oldPageBookmarksName)
      .inOrder()
    assertThat(data.collectionBookmarks.map { link -> link.collectionImportId })
      .containsExactly("tag-10", "tag-11")
  }

  @Test
  fun `direct ayah bookmark wins over converted page bookmark for the same ayah`() {
    val page = 50
    val pageBounds = quranInfo.getPageBounds(page)

    val data = normalizer.normalize(
      LegacyBookmarksSnapshot(
        tags = emptyList(),
        bookmarks = listOf(
          Bookmark(1L, null, null, page, 2000L),
          Bookmark(2L, pageBounds[0], pageBounds[1], page, 1000L)
        ),
        recentPages = emptyList()
      )
    )

    assertThat(data.bookmarks).hasSize(1)
    assertThat(data.bookmarks.single().sura).isEqualTo(pageBounds[0])
    assertThat(data.bookmarks.single().ayah).isEqualTo(pageBounds[1])
    assertThat(data.bookmarks.single().timestampSeconds).isEqualTo(1000L)
    assertThat(data.collections.map { collection -> collection.name })
      .containsExactly(context.getString(R.string.old_page_bookmarks))
    assertThat(data.collectionBookmarks.single().timestampSeconds).isEqualTo(2000L)
  }

  @Test
  fun `recent pages become reading sessions at first ayah of page`() {
    val firstPageBounds = quranInfo.getPageBounds(50)
    val secondPageBounds = quranInfo.getPageBounds(51)

    val data = normalizer.normalize(
      LegacyBookmarksSnapshot(
        tags = emptyList(),
        bookmarks = emptyList(),
        recentPages = listOf(RecentPage(50, 1000L), RecentPage(51, 900L))
      )
    )

    assertThat(data.readingSessions.map { session -> session.sura to session.ayah })
      .containsExactly(
        firstPageBounds[0] to firstPageBounds[1],
        secondPageBounds[0] to secondPageBounds[1]
      )
      .inOrder()
    assertThat(data.readingSessions.map { session -> session.timestampSeconds })
      .containsExactly(1000L, 900L)
      .inOrder()
  }
}
