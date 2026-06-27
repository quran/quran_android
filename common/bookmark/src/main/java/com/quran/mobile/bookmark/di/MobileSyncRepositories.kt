package com.quran.mobile.bookmark.di

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.model.BookmarkCollectionsState
import com.quran.mobile.bookmark.model.RepositoryBookmarkCollectionsState
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepository
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class MobileSyncRepositories @Inject constructor(
  mobileSyncDatabase: MobileSyncDatabase,
  appCoroutineScope: AppCoroutineScope
) {
  val bookmarksRepository: BookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database)
  val collectionsRepository: CollectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database)
  val collectionBookmarksRepository: CollectionBookmarksRepository =
    CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database)
  val bookmarkCollectionsState: BookmarkCollectionsState =
    RepositoryBookmarkCollectionsState(collectionsRepository, collectionBookmarksRepository, appCoroutineScope)
  val readingBookmarksRepository: ReadingBookmarksRepository =
    ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
  val readingSessionsRepository: ReadingSessionsRepository =
    ReadingSessionsRepositoryImpl(mobileSyncDatabase.database)
}
