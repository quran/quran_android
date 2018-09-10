package com.quran.labs.androidquran.dao.bookmark

data class Bookmark @JvmOverloads constructor(val id: Long,
                                              // sura and ayah are nullable for page bookmarks
                                              val sura: Int?,
                                              val ayah: Int?,
                                              val page: Int,
                                              val timestamp: Long = System.currentTimeMillis(),
                                              val tags: List<Long> = emptyList(),
                                              val ayahText: String? = null) {

  fun isPageBookmark() = sura == null && ayah == null

  fun withTags(tagIds: List<Long>): Bookmark {
    return this.copy(tags = tagIds)
  }

  fun withAyahText(ayahText: String): Bookmark {
    return this.copy(ayahText = ayahText)
  }
}
