package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

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
    return this.copy(tags = tagIds.toList())
  }

  fun withAyahText(ayahText: String): Bookmark {
    return this.copy(ayahText = ayahText)
  }

  fun getCommaSeparatedValues(tagsList: List<Tag>) =
    "bookmark, $sura, $ayah, $page, $timestamp, ${getSemiColonSeparatedTags(tagsList)},"

  private fun getSemiColonSeparatedTags(tagsList: List<Tag>): String? {
    val tagsById = tagsList.associateBy { it.id }
    return tags.map { tagId ->
      tagsById[tagId]?.name?.let(::sanitizeCsvValue)
    }.reduceOrNull { acc, tag -> "$acc$tag|" }
  }
}

private val FORMULA_PREFIX_CHARS = setOf('=', '+', '-', '@', '\t', '\r')

private fun sanitizeCsvValue(value: String): String {
  val sanitized = value
    .replace('\r', ' ')
    .replace('\n', ' ')
    .replace(',', ' ')
  val firstChar = sanitized.firstOrNull()
  val firstNonSpace = sanitized.firstOrNull { !it.isWhitespace() }
  return if (
    (firstChar != null && firstChar in FORMULA_PREFIX_CHARS) ||
    (firstNonSpace != null && firstNonSpace in FORMULA_PREFIX_CHARS)
  ) {
    "'$sanitized"
  } else {
    sanitized
  }
}
