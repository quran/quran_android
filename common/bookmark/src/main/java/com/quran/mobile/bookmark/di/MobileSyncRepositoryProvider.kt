package com.quran.mobile.bookmark.di

import com.quran.mobile.bookmark.model.BookmarkCollectionsState
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepository

interface MobileSyncRepositoryProvider {
  val bookmarksRepository: BookmarksRepository
  val collectionsRepository: CollectionsRepository
  val collectionBookmarksRepository: CollectionBookmarksRepository
  val bookmarkCollectionsState: BookmarkCollectionsState
  val readingBookmarksRepository: ReadingBookmarksRepository
  val readingSessionsRepository: ReadingSessionsRepository
}
