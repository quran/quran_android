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

    bookmarksDao.replaceAyahBookmarkCollections(SuraAyah(6, 76), setOf(tag.id))

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
    bookmarksDao.replaceAyahBookmarkCollections(SuraAyah(6, 76), setOf(tag.id))

    bookmarksDao.removeTags(listOf(tag))

    assertThat(bookmarksDao.currentBookmarks()).isEmpty()
  }

  @Test
  fun `removing bookmark from tag prunes custom-only ayah bookmark`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val tag = Tag("tag-1", "Review")
    bookmarksDao.setTags(listOf(tag))
    bookmarksDao.replaceAyahBookmarkCollections(SuraAyah(6, 76), setOf(tag.id))

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

  @Test
  fun `empty collection replacement removes an existing collection bookmark`() = runTest {
    val bookmarksDao = FakeBookmarksDao()
    val suraAyah = SuraAyah(6, 76)
    bookmarksDao.setBookmarks(listOf(Bookmark("bookmark-1", 6, 76, 137)))

    val changed = bookmarksDao.replaceAyahBookmarkCollections(suraAyah, emptySet())

    assertThat(changed).isTrue()
    assertThat(bookmarksDao.currentBookmarks()).isEmpty()
  }

  @Test
  fun `empty collection replacement does not create a bookmark`() = runTest {
    val bookmarksDao = FakeBookmarksDao()

    val changed = bookmarksDao.replaceAyahBookmarkCollections(SuraAyah(6, 76), emptySet())

    assertThat(changed).isFalse()
    assertThat(bookmarksDao.currentBookmarks()).isEmpty()
  }
}
