package com.quran.mobile.bookmark.model

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.sync.FakeLocalDataChangeNotifier
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.QuranDatabase
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.util.PlatformDateTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.quran.shared.persistence.model.Collection as SyncCollection

class BookmarksDaoImplTest {

  private lateinit var database: QuranDatabase
  private lateinit var quranInfo: QuranInfo
  private lateinit var dao: BookmarksDaoImpl
  private lateinit var appCoroutineScope: AppCoroutineScope
  private lateinit var localDataChangeNotifier: FakeLocalDataChangeNotifier
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    QuranDatabase.Schema.create(driver)
    database = QuranDatabase(driver)
    quranInfo = QuranInfo(MadaniDataSource())
    appCoroutineScope = AppCoroutineScope()
    localDataChangeNotifier = FakeLocalDataChangeNotifier()
    timestampProvider = FakeMobileSyncTimestampProvider()
    val collectionsRepository = CollectionsRepositoryImpl(database)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    dao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )
  }

  @After
  fun tearDown() {
    if (::appCoroutineScope.isInitialized) {
      appCoroutineScope.cancel()
    }
  }

  @Test
  fun `bookmarks are empty when no mobile sync bookmarks exist`() = runTest {
    assertThat(dao.bookmarks()).isEmpty()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `toggle ayah bookmark on stores bookmark in mobile sync`() = runTest {
    val suraAyah = SuraAyah(2, 255)

    val added = dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(2, 255))
    val bookmarks = dao.bookmarks()

    assertThat(added).isTrue()
    assertThat(bookmarks).hasSize(1)
    assertThat(bookmarks.single().sura).isEqualTo(2)
    assertThat(bookmarks.single().ayah).isEqualTo(255)
    assertThat(bookmarks.single().page).isEqualTo(quranInfo.getPageFromSuraAyah(2, 255))
    assertThat(bookmarks.single().timestamp).isEqualTo(timestampProvider.timestampSeconds)
    assertThat(bookmarks.single().tags).isEmpty()
    assertThat(bookmarks.single().isPageBookmark()).isFalse()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `toggle ayah bookmark off deletes bookmark`() = runTest {
    val suraAyah = SuraAyah(1, 1)
    dao.toggleAyahBookmark(suraAyah, 1)

    val removed = dao.toggleAyahBookmark(suraAyah, 1)

    assertThat(removed).isFalse()
    assertThat(dao.bookmarks()).isEmpty()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(2)
  }

  @Test
  fun `notifier failures do not fail bookmark writes`() = runTest {
    localDataChangeNotifier.throwOnUpdate = true

    val added = dao.toggleAyahBookmark(SuraAyah(2, 255), quranInfo.getPageFromSuraAyah(2, 255))

    assertThat(added).isTrue()
    assertThat(dao.bookmarks().map { it.sura to it.ayah }).containsExactly(2 to 255)
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `is sura ayah bookmarked reflects mobile sync state`() = runTest {
    val suraAyah = SuraAyah(18, 10)

    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isFalse()

    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(18, 10))

    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isTrue()
  }

  @Test
  fun `remove bookmarks deletes ayah bookmarks and ignores page bookmark models`() = runTest {
    val suraAyah = SuraAyah(36, 1)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(36, 1))
    val ayahBookmark = dao.bookmarks().single()
    val pageBookmark = Bookmark("bookmark-999", null, null, 50, 1)

    dao.removeBookmarks(listOf(ayahBookmark, pageBookmark))

    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `bookmarks for page returns only bookmarks on requested page`() = runTest {
    val first = SuraAyah(2, 255)
    val second = SuraAyah(36, 1)
    dao.toggleAyahBookmark(first, quranInfo.getPageFromSuraAyah(first.sura, first.ayah))
    dao.toggleAyahBookmark(second, quranInfo.getPageFromSuraAyah(second.sura, second.ayah))

    val bookmarks = dao.bookmarksForPage(quranInfo.getPageFromSuraAyah(first.sura, first.ayah)).first()

    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(first.sura to first.ayah)
  }

  @Test
  fun `location sort orders bookmarks by page`() = runTest {
    val laterPage = SuraAyah(36, 1)
    val earlierPage = SuraAyah(2, 255)
    dao.toggleAyahBookmark(laterPage, quranInfo.getPageFromSuraAyah(laterPage.sura, laterPage.ayah))
    dao.toggleAyahBookmark(earlierPage, quranInfo.getPageFromSuraAyah(earlierPage.sura, earlierPage.ayah))

    val bookmarks = dao.bookmarks(BookmarkSortOrder.SORT_LOCATION)

    assertThat(bookmarks.map { it.sura to it.ayah })
      .containsExactly(earlierPage.sura to earlierPage.ayah, laterPage.sura to laterPage.ayah)
      .inOrder()
  }

  @Test
  fun `bookmarks flow emits mobile sync changes`() = runTest {
    val suraAyah = SuraAyah(4, 1)

    dao.bookmarksFlow().test {
      assertThat(awaitItem()).isEmpty()

      dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
      val addedBookmarks = awaitItem()
      assertThat(addedBookmarks.map { it.sura to it.ayah })
        .containsExactly(suraAyah.sura to suraAyah.ayah)
      assertThat(addedBookmarks.single().tags).isEmpty()

      dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
      assertThat(awaitItem()).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `one shot bookmark reads reflect external mobile sync writes`() = runTest {
    val repository = BookmarksRepositoryImpl(database)
    assertThat(dao.bookmarks()).isEmpty()

    repository.addBookmark(2, 255)

    val bookmarks = dao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 255)
  }

  @Test
  fun `bookmarks flow emits external mobile sync writes`() = runTest {
    val repository = BookmarksRepositoryImpl(database)

    dao.bookmarksFlow().test {
      assertThat(awaitItem()).isEmpty()
      repository.addBookmark(2, 255)
      assertThat(awaitItem().map { it.sura to it.ayah }).containsExactly(2 to 255)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `repository collections state includes default collection without custom collections`() = runTest {
    val collectionsState = RepositoryBookmarkCollectionsState(
      CollectionsRepositoryImpl(database),
      CollectionBookmarksRepositoryImpl(database),
      appCoroutineScope
    )

    val collectionsWithBookmarks = collectionsState.currentCollectionsWithBookmarks()

    assertThat(collectionsWithBookmarks.map { collectionWithBookmarks -> collectionWithBookmarks.collection.id })
      .containsExactly(DEFAULT_BOOKMARK_COLLECTION_ID)
    assertThat(collectionsWithBookmarks.single().collection.isDefault).isTrue()
    assertThat(collectionsWithBookmarks.single().bookmarks).isEmpty()
  }

  @Test
  fun `tags map to mobile sync collections`() = runTest {
    val id = dao.addTag("Review")

    assertThat(dao.tags()).containsExactly(com.quran.data.model.bookmark.Tag(id, "Review"))

    dao.updateTag(com.quran.data.model.bookmark.Tag(id, "Important"))

    assertThat(dao.tags()).containsExactly(com.quran.data.model.bookmark.Tag(id, "Important"))
  }

  @Test
  fun `update tag returns false when name already exists`() = runTest {
    val firstId = dao.addTag("First")
    val secondId = dao.addTag("Second")
    localDataChangeNotifier.reset()

    val updated = dao.updateTag(com.quran.data.model.bookmark.Tag(firstId, "Second"))

    assertThat(updated).isFalse()
    assertThat(dao.tags()).containsExactly(
      com.quran.data.model.bookmark.Tag(firstId, "First"),
      com.quran.data.model.bookmark.Tag(secondId, "Second")
    )
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `update tag returns false when tag no longer exists`() = runTest {
    localDataChangeNotifier.reset()

    val updated = dao.updateTag(com.quran.data.model.bookmark.Tag("missing", "Missing"))

    assertThat(updated).isFalse()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `tags exclude default collection`() = runTest {
    val collectionsRepository = DefaultCollectionTestRepository(timestampProvider.now())
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val dao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )

    assertThat(dao.tags()).isEmpty()
    dao.tagsFlow().test {
      assertThat(awaitItem()).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `update tag returns false for default collection`() = runTest {
    val collectionsRepository = DefaultCollectionTestRepository(timestampProvider.now())
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val dao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )

    val updated = dao.updateTag(
      com.quran.data.model.bookmark.Tag(DEFAULT_BOOKMARK_COLLECTION_ID, "Default Renamed")
    )

    assertThat(updated).isFalse()
    assertThat(collectionsRepository.updateCount).isEqualTo(0)
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `bookmark tags are populated from collection bookmarks`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    val taggedBookmark = dao.bookmarks().single()
    assertThat(taggedBookmark.tags).containsExactly(tagId)
    assertThat(dao.getBookmarkTagIds(bookmark.id)).containsExactly(tagId)
    assertThat(dao.getAyahBookmarkTagIds(suraAyah)).containsExactly(tagId)
  }

  @Test
  fun `bookmark tag updates notify once per high level write`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    localDataChangeNotifier.reset()

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `updating single bookmark tags replaces old collection links`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(firstTagId), deleteNonTagged = true)

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(secondTagId), deleteNonTagged = true)

    assertThat(dao.bookmarks().single().tags).containsExactly(secondTagId)
  }

  @Test
  fun `updating multiple bookmark tags adds without deleting existing links`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(firstTagId), deleteNonTagged = true)

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(secondTagId), deleteNonTagged = false)

    assertThat(dao.bookmarks().single().tags).containsExactly(firstTagId, secondTagId)
  }

  @Test
  fun `ayah bookmark tags create missing bookmark and link collection`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(6, 76)

    dao.updateAyahBookmarkTags(
      suraAyah = suraAyah,
      page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah),
      tagIds = setOf(tagId),
      deleteNonTagged = true
    )

    val bookmark = dao.bookmarks().single()
    assertThat(bookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(bookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(bookmark.tags).containsExactly(tagId)
  }

  @Test
  fun `clearing tags from default bookmark preserves bookmark`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    dao.updateBookmarkTags(arrayOf(bookmark.id), emptySet(), deleteNonTagged = true)

    val remainingBookmark = dao.bookmarks().single()
    assertThat(remainingBookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(remainingBookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(remainingBookmark.tags).isEmpty()
  }

  @Test
  fun `clearing tags from custom only bookmark removes bookmark`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(6, 76)
    dao.updateAyahBookmarkTags(
      suraAyah = suraAyah,
      page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah),
      tagIds = setOf(tagId),
      deleteNonTagged = true
    )

    dao.updateAyahBookmarkTags(
      suraAyah = suraAyah,
      page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah),
      tagIds = emptySet(),
      deleteNonTagged = true
    )

    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `removing bookmark from tag unlinks only that collection`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(firstTagId, secondTagId), deleteNonTagged = true)

    dao.removeBookmarkFromTag(bookmark, firstTagId)

    assertThat(dao.bookmarks().single().tags).containsExactly(secondTagId)
  }

  @Test
  fun `removing a bookmark clears collection links`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    dao.removeBookmarks(listOf(bookmark))

    assertThat(dao.bookmarks()).isEmpty()
    assertThat(dao.getBookmarkTagIds(bookmark.id)).isEmpty()
  }

  @Test
  fun `toggling a bookmark off clears collection links`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    val removed = dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))

    assertThat(removed).isFalse()
    assertThat(dao.bookmarks()).isEmpty()
    assertThat(dao.getBookmarkTagIds(bookmark.id)).isEmpty()
  }

  @Test
  fun `removing bookmarks for page clears collection links`() = runTest {
    val tagId = dao.addTag("Review")
    val target = SuraAyah(2, 255)
    val other = SuraAyah(36, 1)
    dao.toggleAyahBookmark(target, quranInfo.getPageFromSuraAyah(target.sura, target.ayah))
    dao.toggleAyahBookmark(other, quranInfo.getPageFromSuraAyah(other.sura, other.ayah))
    val targetBookmark = dao.bookmarks().first { it.sura == target.sura && it.ayah == target.ayah }
    dao.updateBookmarkTags(arrayOf(targetBookmark.id), setOf(tagId), deleteNonTagged = true)

    dao.removeBookmarksForPage(quranInfo.getPageFromSuraAyah(target.sura, target.ayah))

    assertThat(dao.bookmarks().map { it.sura to it.ayah }).containsExactly(other.sura to other.ayah)
    assertThat(dao.getBookmarkTagIds(targetBookmark.id)).isEmpty()
  }

  @Test
  fun `replacing ayah bookmarks clears old collection links`() = runTest {
    val oldTagId = dao.addTag("Old")
    val newTagId = dao.addTag("New")
    val oldSuraAyah = SuraAyah(2, 255)
    val newSuraAyah = SuraAyah(36, 1)
    dao.toggleAyahBookmark(oldSuraAyah, quranInfo.getPageFromSuraAyah(oldSuraAyah.sura, oldSuraAyah.ayah))
    val oldBookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(oldBookmark.id), setOf(oldTagId), deleteNonTagged = true)

    dao.replaceAyahBookmarks(
      listOf(
        Bookmark(
          id = "bookmark-999",
          sura = newSuraAyah.sura,
          ayah = newSuraAyah.ayah,
          page = quranInfo.getPageFromSuraAyah(newSuraAyah.sura, newSuraAyah.ayah),
          timestamp = timestampProvider.timestampSeconds,
          tags = listOf(newTagId)
        )
      )
    )

    assertThat(dao.getBookmarkTagIds(oldBookmark.id)).isEmpty()
    val bookmarks = dao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(newSuraAyah.sura to newSuraAyah.ayah)
    assertThat(bookmarks.single().tags).containsExactly(newTagId)
  }

  @Test
  fun `removing bookmark with tags notifies once`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    dao.toggleAyahBookmark(suraAyah, quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)
    localDataChangeNotifier.reset()

    dao.removeBookmarks(listOf(bookmark))

    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  private class DefaultCollectionTestRepository(
    timestamp: PlatformDateTime
  ) : CollectionsRepository {
    private val defaultCollection = SyncCollection(
      name = "Default",
      lastUpdated = timestamp,
      id = DEFAULT_BOOKMARK_COLLECTION_ID
    )

    var updateCount = 0
      private set

    override suspend fun getAllCollections(): List<SyncCollection> {
      return listOf(defaultCollection)
    }

    override suspend fun addCollection(name: String): SyncCollection {
      throw UnsupportedOperationException()
    }

    override suspend fun addCollection(name: String, timestamp: PlatformDateTime): SyncCollection {
      throw UnsupportedOperationException()
    }

    override suspend fun updateCollection(id: String, name: String): SyncCollection {
      updateCount++
      return defaultCollection.copy(name = name)
    }

    override suspend fun updateCollection(
      id: String,
      name: String,
      timestamp: PlatformDateTime
    ): SyncCollection {
      updateCount++
      return defaultCollection.copy(name = name, lastUpdated = timestamp)
    }

    override suspend fun deleteCollection(id: String): Boolean {
      throw UnsupportedOperationException()
    }

    override fun getCollectionsFlow(): Flow<List<SyncCollection>> {
      return flowOf(listOf(defaultCollection))
    }
  }
}
