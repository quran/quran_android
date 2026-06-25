package com.quran.mobile.bookmark.di

import com.quran.data.di.AppScope
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface MobileSyncRepositoryProvider {
  val bookmarksRepository: BookmarksRepository
  val collectionsRepository: CollectionsRepository
  val collectionBookmarksRepository: CollectionBookmarksRepository
  val readingBookmarksRepository: ReadingBookmarksRepository
  val readingSessionsRepository: ReadingSessionsRepository
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultMobileSyncRepositoryProvider @Inject constructor(
  private val repositories: MobileSyncRepositories
) : MobileSyncRepositoryProvider {
  override val bookmarksRepository: BookmarksRepository
    get() = repositories.bookmarksRepository

  override val collectionsRepository: CollectionsRepository
    get() = repositories.collectionsRepository

  override val collectionBookmarksRepository: CollectionBookmarksRepository
    get() = repositories.collectionBookmarksRepository

  override val readingBookmarksRepository: ReadingBookmarksRepository
    get() = repositories.readingBookmarksRepository

  override val readingSessionsRepository: ReadingSessionsRepository
    get() = repositories.readingSessionsRepository
}
