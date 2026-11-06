package com.quran.labs.androidquran.widget

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import org.junit.Test

class BookmarksWidgetTagHelperTest {

  private val tagsById = listOf(
    Tag(id = "1", name = "Taraweeh"),
    Tag(id = "2", name = "Duas"),
    Tag(id = "3", name = "Favorites", isDefault = true),
    Tag(id = "4", name = "System", isSystem = true),
    Tag(id = "5", name = "Blue", isReadOnly = true)
  ).associateBy { it.id }

  private fun bookmarkWithTags(vararg tagIds: String): Bookmark {
    return Bookmark(
      id = "bookmark",
      sura = 2,
      ayah = 255,
      page = 42,
      tags = tagIds.toList()
    )
  }

  @Test
  fun `tag names are returned in the bookmark's tag order`() {
    val bookmark = bookmarkWithTags("2", "1")

    assertThat(BookmarksWidgetTagHelper.tagNames(bookmark, tagsById))
      .containsExactly("Duas", "Taraweeh")
      .inOrder()
  }

  @Test
  fun `untagged bookmark has no tag names`() {
    assertThat(BookmarksWidgetTagHelper.tagNames(bookmarkWithTags(), tagsById)).isEmpty()
  }

  @Test
  fun `default, system and read-only collections are not shown as tags`() {
    val bookmark = bookmarkWithTags("3", "4", "5")

    assertThat(BookmarksWidgetTagHelper.tagNames(bookmark, tagsById)).isEmpty()
  }

  @Test
  fun `unknown tag ids are skipped`() {
    val bookmark = bookmarkWithTags("unknown", "1")

    assertThat(BookmarksWidgetTagHelper.tagNames(bookmark, tagsById))
      .containsExactly("Taraweeh")
  }

  @Test
  fun `metadata gains the tag names after a separator`() {
    assertThat(
      BookmarksWidgetTagHelper.metadataWithTags("Page 42, Juz 3", listOf("Duas", "Taraweeh"))
    ).isEqualTo("Page 42, Juz 3 • Duas, Taraweeh")
  }

  @Test
  fun `metadata is unchanged when there are no tags`() {
    assertThat(BookmarksWidgetTagHelper.metadataWithTags("Page 42, Juz 3", emptyList()))
      .isEqualTo("Page 42, Juz 3")
    assertThat(BookmarksWidgetTagHelper.metadataWithTags(null, emptyList())).isNull()
  }

  @Test
  fun `tag names stand alone when there is no metadata`() {
    assertThat(BookmarksWidgetTagHelper.metadataWithTags(null, listOf("Duas")))
      .isEqualTo("Duas")
    assertThat(BookmarksWidgetTagHelper.metadataWithTags("", listOf("Duas")))
      .isEqualTo("Duas")
  }
}
