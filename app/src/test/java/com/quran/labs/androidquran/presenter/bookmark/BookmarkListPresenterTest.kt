package com.quran.labs.androidquran.presenter.bookmark

import com.google.common.truth.Truth.assertThat
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeHighlightsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BookmarkListPresenterTest {
  private val bookmarksDao = FakeBookmarksDao()
  private val presenter = BookmarkListPresenter(
    bookmarksDao,
    FakeHighlightsDao(),
    { throw IllegalStateException("ArabicDatabaseUtils not wired up in test") },
    AppCoroutineScope()
  )

  @Test
  fun `a color collection older apps created is read-only, so the screen offers no rename`() =
    runTest {
      // as sync writes it: not a system collection, but protected by the server
      bookmarksDao.setTags(listOf(Tag("green", "Green", isSystem = false, isReadOnly = true)))

      val collection = presenter.collection("green").first()

      assertThat(collection?.name).isEqualTo("Green")
      assertThat(collection?.isReadOnly).isTrue()
    }

  @Test
  fun `Favorites is read-only`() = runTest {
    val favorites = bookmarksDao.tags().single { it.isDefault }

    assertThat(presenter.collection(favorites.id).first()?.isReadOnly).isTrue()
  }

  @Test
  fun `a collection the user made can be renamed, and the screen sees the new name`() = runTest {
    bookmarksDao.setTags(listOf(Tag("study", "Study")))
    assertThat(presenter.collection("study").first()?.isReadOnly).isFalse()

    assertThat(bookmarksDao.updateTag(Tag("study", "Review"))).isTrue()

    assertThat(presenter.collection("study").first()?.name).isEqualTo("Review")
  }

  @Test
  fun `a deleted collection is gone`() = runTest {
    val study = Tag("study", "Study")
    bookmarksDao.setTags(listOf(study))

    bookmarksDao.removeTags(listOf(study))

    assertThat(presenter.collection("study").first()).isNull()
  }
}
