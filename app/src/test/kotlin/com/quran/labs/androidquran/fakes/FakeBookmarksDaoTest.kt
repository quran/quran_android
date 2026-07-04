package com.quran.labs.androidquran.fakes

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeBookmarksDaoTest {

  @Test
  fun `tag-only ayah bookmarks are not in the default collection`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val tag = Tag("tag-1", "Review")
    bookmarksDao.setTags(listOf(tag))

    bookmarksDao.updateAyahBookmarkTags(
      suraAyah = SuraAyah(6, 76),
      page = 137,
      tagIds = setOf(tag.id),
      deleteNonTagged = true
    )

    val collections = bookmarksDao.collectionsWithBookmarksFlow().first()

    assertThat(collections.single { it.readingCollection.isSystem }.bookmarks).isEmpty()
    assertThat(collections.single { it.readingCollection.id == tag.id }.bookmarks.single().sura)
      .isEqualTo(6)
  }

  @Test
  fun `seeded ayah bookmarks are in the default collection`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val tag = Tag("tag-1", "Review")
    bookmarksDao.setTags(listOf(tag))
    bookmarksDao.setBookmarks(listOf(Bookmark("bookmark-1", 6, 76, 137, tags = listOf(tag.id))))

    val collections = bookmarksDao.collectionsWithBookmarksFlow().first()

    assertThat(collections.single { it.readingCollection.isSystem }.bookmarks.single().sura)
      .isEqualTo(6)
    assertThat(collections.single { it.readingCollection.id == tag.id }.bookmarks.single().sura)
      .isEqualTo(6)
  }

  @Test
  fun `removing tag prunes custom-only ayah bookmark`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val tag = Tag("tag-1", "Review")
    bookmarksDao.setTags(listOf(tag))
    bookmarksDao.updateAyahBookmarkTags(
      suraAyah = SuraAyah(6, 76),
      page = 137,
      tagIds = setOf(tag.id),
      deleteNonTagged = true
    )

    bookmarksDao.removeTags(listOf(tag))

    assertThat(bookmarksDao.currentBookmarks()).isEmpty()
  }

  @Test
  fun `removing bookmark from tag prunes custom-only ayah bookmark`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val tag = Tag("tag-1", "Review")
    bookmarksDao.setTags(listOf(tag))
    bookmarksDao.updateAyahBookmarkTags(
      suraAyah = SuraAyah(6, 76),
      page = 137,
      tagIds = setOf(tag.id),
      deleteNonTagged = true
    )

    bookmarksDao.removeBookmarkFromTag(bookmarksDao.currentBookmarks().single(), tag.id)

    assertThat(bookmarksDao.currentBookmarks()).isEmpty()
  }

  @Test
  fun `removing last tag preserves default ayah bookmark`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val tag = Tag("tag-1", "Review")
    bookmarksDao.setTags(listOf(tag))
    bookmarksDao.setBookmarks(listOf(Bookmark("bookmark-1", 6, 76, 137, tags = listOf(tag.id))))

    bookmarksDao.removeTags(listOf(tag))

    assertThat(bookmarksDao.currentBookmarks().single().tags).isEmpty()
    assertThat(bookmarksDao.isSuraAyahBookmarked(SuraAyah(6, 76))).isTrue()
  }
}
