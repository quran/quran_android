package com.quran.mobile.bookmark.importdata

import com.quran.shared.persistence.model.ReadingBookmarkSlot

data class MobileSyncImportData(
  val bookmarks: List<MobileSyncImportBookmark> = emptyList(),
  val collections: List<MobileSyncImportCollection> = emptyList(),
  val collectionBookmarks: List<MobileSyncImportCollectionBookmark> = emptyList(),
  val readingSessions: List<MobileSyncImportReadingSession> = emptyList(),
  val readingBookmarks: List<MobileSyncImportReadingBookmark> = emptyList()
) {
  fun isEmpty(): Boolean {
    return bookmarks.isEmpty() &&
      collections.isEmpty() &&
      collectionBookmarks.isEmpty() &&
      readingSessions.isEmpty() &&
      readingBookmarks.isEmpty()
  }
}

data class MobileSyncImportBookmark(
  val importId: String,
  val sura: Int,
  val ayah: Int,
  val timestampMillis: Long
)

data class MobileSyncImportCollection(
  val importId: String,
  val name: String,
  val timestampMillis: Long
)

data class MobileSyncImportCollectionBookmark(
  val collectionImportId: String,
  val bookmarkImportId: String,
  val timestampMillis: Long
)

data class MobileSyncImportReadingSession(
  val sura: Int,
  val ayah: Int,
  val timestampMillis: Long
)

sealed interface MobileSyncImportReadingBookmark {
  val timestampMillis: Long

  data class Ayah(
    val slot: ReadingBookmarkSlot,
    val sura: Int,
    val ayah: Int,
    override val timestampMillis: Long
  ) : MobileSyncImportReadingBookmark

  data class Page(
    val slot: ReadingBookmarkSlot,
    val page: Int,
    override val timestampMillis: Long
  ) : MobileSyncImportReadingBookmark
}

data class MobileSyncImportResult(
  val bookmarksImported: Int,
  val collectionsImported: Int,
  val collectionBookmarksImported: Int,
  val readingSessionsImported: Int,
  val readingBookmarkImported: Int
)
