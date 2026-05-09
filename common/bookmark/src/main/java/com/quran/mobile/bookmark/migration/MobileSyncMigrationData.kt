package com.quran.mobile.bookmark.migration

data class MobileSyncMigrationData(
  val bookmarks: List<MobileSyncMigrationBookmark> = emptyList(),
  val collections: List<MobileSyncMigrationCollection> = emptyList(),
  val collectionBookmarks: List<MobileSyncMigrationCollectionBookmark> = emptyList(),
  val readingSessions: List<MobileSyncMigrationReadingSession> = emptyList()
) {
  fun isEmpty(): Boolean {
    return bookmarks.isEmpty() &&
      collections.isEmpty() &&
      collectionBookmarks.isEmpty() &&
      readingSessions.isEmpty()
  }
}

data class MobileSyncMigrationBookmark(
  val importId: String,
  val sura: Int,
  val ayah: Int,
  val timestampSeconds: Long
)

data class MobileSyncMigrationCollection(
  val importId: String,
  val name: String,
  val timestampSeconds: Long
)

data class MobileSyncMigrationCollectionBookmark(
  val collectionImportId: String,
  val bookmarkImportId: String,
  val timestampSeconds: Long
)

data class MobileSyncMigrationReadingSession(
  val sura: Int,
  val ayah: Int,
  val timestampSeconds: Long
)

data class MobileSyncMigrationImportResult(
  val bookmarksImported: Int,
  val collectionsImported: Int,
  val collectionBookmarksImported: Int,
  val readingSessionsImported: Int
)
