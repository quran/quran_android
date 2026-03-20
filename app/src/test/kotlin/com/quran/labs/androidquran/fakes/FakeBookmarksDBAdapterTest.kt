package com.quran.labs.androidquran.fakes

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import org.junit.Before
import org.junit.Test

/**
 * Tests for FakeBookmarksDBAdapter to verify fake behavior.
 */
class FakeBookmarksDBAdapterTest {

  private lateinit var fake: FakeBookmarksDBAdapter

  @Before
  fun setup() {
    fake = FakeBookmarksDBAdapter()
  }

  @Test
  fun `should store and retrieve tags`() {
    val tags = listOf(Tag(1, "Important"), Tag(2, "Review"))
    fake.setTags(tags)

    val result = fake.getTags()

    assertThat(result).hasSize(2)
    assertThat(result[0].name).isEqualTo("Important")
    assertThat(result[1].name).isEqualTo("Review")
  }

  @Test
  fun `should update tag successfully`() {
    fake.setTags(listOf(Tag(1, "Old Name")))

    val result = fake.updateTag(1, "New Name")

    assertThat(result).isTrue()
    assertThat(fake.getTags()[0].name).isEqualTo("New Name")
    fake.assertTagUpdated(1, "New Name")
  }

  @Test
  fun `should fail to update tag when name already exists`() {
    fake.setTags(listOf(Tag(1, "First"), Tag(2, "Second")))

    val result = fake.updateTag(1, "Second")

    assertThat(result).isFalse()
    assertThat(fake.getTags()[0].name).isEqualTo("First")
  }

  @Test
  fun `should track update tag calls`() {
    fake.setTags(listOf(Tag(1, "Name")))

    fake.updateTag(1, "Updated")
    fake.updateTag(1, "Updated Again")

    assertThat(fake.getUpdateTagCallCount()).isEqualTo(2)
  }

  @Test
  fun `should add recent page`() {
    fake.addRecentPage(42)

    val pages = fake.getRecentPages()
    assertThat(pages).hasSize(1)
    assertThat(pages[0].page).isEqualTo(42)
    fake.assertRecentPageAdded(42)
  }

  @Test
  fun `should replace recent page range`() {
    fake.setRecentPages(listOf(
      RecentPage(10, System.currentTimeMillis()),
      RecentPage(20, System.currentTimeMillis()),
      RecentPage(30, System.currentTimeMillis())
    ))

    fake.replaceRecentRangeWithPage(15, 25, 42)

    val pages = fake.getRecentPages()
    assertThat(pages).hasSize(3)
    assertThat(pages[0].page).isEqualTo(42)
    assertThat(pages.map { it.page }).containsExactly(42, 10, 30)
    fake.assertReplaceRecentRangeCalled(15, 25, 42)
  }

  @Test
  fun `should tag bookmarks successfully`() {
    val result = fake.tagBookmarks(
      bookmarkIds = longArrayOf(1, 2),
      tagIds = setOf(1L, 2L),
      deleteNonTagged = false
    )

    assertThat(result).isTrue()
    assertThat(fake.getBookmarkTagIds(1)).containsExactly(1L, 2L)
    assertThat(fake.getBookmarkTagIds(2)).containsExactly(1L, 2L)
    fake.assertTagBookmarksCalled(longArrayOf(1, 2), setOf(1L, 2L), false)
  }

  @Test
  fun `should sort bookmarks by date`() {
    val now = System.currentTimeMillis()
    fake.setBookmarks(listOf(
      Bookmark(1, 2, 4, 10, now - 1000),
      Bookmark(2, 3, 5, 20, now),
      Bookmark(3, 4, 6, 30, now - 2000)
    ))

    val sorted = fake.getBookmarks(FakeBookmarksDBAdapter.SORT_DATE_ADDED)

    assertThat(sorted.map { it.id }).containsExactly(2L, 1L, 3L).inOrder()
  }

  @Test
  fun `should sort bookmarks by location`() {
    fake.setBookmarks(listOf(
      Bookmark(1, 2, 4, 30, System.currentTimeMillis()),
      Bookmark(2, 3, 5, 10, System.currentTimeMillis()),
      Bookmark(3, 4, 6, 20, System.currentTimeMillis())
    ))

    val sorted = fake.getBookmarks(FakeBookmarksDBAdapter.SORT_LOCATION)

    assertThat(sorted.map { it.page }).containsExactly(10, 20, 30).inOrder()
  }

  @Test
  fun `should reset state`() {
    fake.setTags(listOf(Tag(1, "Test")))
    fake.setBookmarks(listOf(Bookmark(1, 2, 4, 10, System.currentTimeMillis())))
    fake.updateTag(1, "Updated")

    fake.reset()

    assertThat(fake.getTags()).isEmpty()
    assertThat(fake.getBookmarks(FakeBookmarksDBAdapter.SORT_DATE_ADDED)).isEmpty()
    assertThat(fake.getUpdateTagCallCount()).isEqualTo(0)
  }

  @Test
  fun `should configure updateTag to fail`() {
    fake.setTags(listOf(Tag(1, "Test")))
    fake.setUpdateTagResult(false)

    val result = fake.updateTag(1, "Updated")

    assertThat(result).isFalse()
  }
}
