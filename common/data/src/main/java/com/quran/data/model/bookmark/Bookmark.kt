package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

/**
 * Converts numeric IDs from the deprecated bookmarks database and old backups into runtime string
 * IDs. Bookmark IDs and tag IDs live in separate namespaces, so the stable string form can stay the
 * original decimal value.
 */
object LegacyBookmarkIds {
  fun bookmarkId(id: Long): String = id.toString()

  fun tagId(id: Long): String = id.toString()
}

@JsonClass(generateAdapter = true)
data class Bookmark @JvmOverloads constructor(
  val id: String,
  // sura and ayah are nullable for page bookmarks
  val sura: Int?,
  val ayah: Int?,
  val page: Int,
  val timestamp: Long = System.currentTimeMillis(),
  val tags: List<String> = emptyList(),
  val ayahText: String? = null
) {

  fun isPageBookmark() = sura == null && ayah == null

  fun withTags(tagIds: List<String>): Bookmark {
    return this.copy(tags = mutableListOf<String>().apply { addAll(tagIds) })
  }

  fun withAyahText(ayahText: String): Bookmark {
    return this.copy(ayahText = ayahText)
  }

  fun getCommaSeparatedNames() =
    "type, sura, ayah, page, timestamp, tags"

  fun getCommaSeparatedValues(tagsList: List<Tag>) =
    "bookmark, $sura, $ayah, $page, $timestamp, ${getSemiColonSeparatedTags(tagsList)}"

  private fun getSemiColonSeparatedTags(tagsList: List<Tag>) =
    tags.map { tagsList.find { tag -> tag.id == it }?.name }
      .reduceOrNull { tags, tag -> "$tags$tag|" }
}
