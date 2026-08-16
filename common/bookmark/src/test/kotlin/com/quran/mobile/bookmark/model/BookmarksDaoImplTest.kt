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
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.QuranDatabase
import com.quran.shared.persistence.model.AyahHighlightColor
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.util.PlatformDateTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    QuranDatabase.Schema.create(driver)
    database = QuranDatabase(driver)
    quranInfo = QuranInfo(MadaniDataSource())
    appCoroutineScope = AppCoroutineScope()
    timestampProvider = FakeMobileSyncTimestampProvider()
    val collectionsRepository = CollectionsRepositoryImpl(database)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    dao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
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
  }

  @Test
  fun `is sura ayah bookmarked reflects mobile sync state`() = runTest {
    val suraAyah = SuraAyah(18, 10)

    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isFalse()

    addDefaultBookmark(suraAyah)

    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isTrue()
  }

  @Test
  fun `remove bookmarks deletes ayah bookmarks and ignores page bookmark models`() = runTest {
    val suraAyah = SuraAyah(36, 1)
    addDefaultBookmark(suraAyah)
    val ayahBookmark = dao.bookmarks().single()
    val pageBookmark = Bookmark("bookmark-999", null, null, 50, 1)

    dao.removeBookmarks(listOf(ayahBookmark, pageBookmark))

    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `bookmarks for page returns only bookmarks on requested page`() = runTest {
    val first = SuraAyah(2, 255)
    val second = SuraAyah(36, 1)
    addDefaultBookmark(first)
    addDefaultBookmark(second)

    val bookmarks = dao.bookmarksForPage(quranInfo.getPageFromSuraAyah(first.sura, first.ayah)).first()

    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(first.sura to first.ayah)
  }

  @Test
  fun `location sort orders bookmarks by page`() = runTest {
    val laterPage = SuraAyah(36, 1)
    val earlierPage = SuraAyah(2, 255)
    addDefaultBookmark(laterPage)
    addDefaultBookmark(earlierPage)

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

      addDefaultBookmark(suraAyah)
      val addedBookmarks = awaitItem()
      assertThat(addedBookmarks.map { it.sura to it.ayah })
        .containsExactly(suraAyah.sura to suraAyah.ayah)
      assertThat(addedBookmarks.single().tags).isEmpty()

      dao.deleteAyahBookmark(suraAyah)
      assertThat(awaitItem()).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `one shot bookmark reads reflect external mobile sync writes`() = runTest {
    assertThat(dao.bookmarks()).isEmpty()

    addDefaultBookmark(SuraAyah(2, 255))

    val bookmarks = dao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 255)
  }

  @Test
  fun `bookmarks flow emits external mobile sync writes`() = runTest {
    dao.bookmarksFlow().test {
      assertThat(awaitItem()).isEmpty()
      addDefaultBookmark(SuraAyah(2, 255))
      assertThat(awaitItem().map { it.sura to it.ayah }).containsExactly(2 to 255)
      cancelAndIgnoreRemainingEvents()
    }
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

    val updated = dao.updateTag(com.quran.data.model.bookmark.Tag(firstId, "Second"))

    assertThat(updated).isFalse()
    assertThat(dao.tags()).containsExactly(
      com.quran.data.model.bookmark.Tag(firstId, "First"),
      com.quran.data.model.bookmark.Tag(secondId, "Second")
    )
  }

  @Test
  fun `update tag treats unchanged name as success`() = runTest {
    val tagId = dao.addTag("Review")

    val updated = dao.updateTag(com.quran.data.model.bookmark.Tag(tagId, "Review"))

    assertThat(updated).isTrue()
    assertThat(dao.tags()).containsExactly(com.quran.data.model.bookmark.Tag(tagId, "Review"))
  }

  @Test
  fun `update tag returns false when tag no longer exists`() = runTest {

    val updated = dao.updateTag(com.quran.data.model.bookmark.Tag("missing", "Missing"))

    assertThat(updated).isFalse()
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
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
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
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )

    val updated = dao.updateTag(
      com.quran.data.model.bookmark.Tag(collectionsRepository.defaultCollectionId, "Default Renamed")
    )

    assertThat(updated).isFalse()
    assertThat(collectionsRepository.updateCount).isEqualTo(0)
  }

  @Test
  fun `update tag returns false for non-default system collection`() = runTest {
    val collectionsRepository = DefaultCollectionTestRepository(timestampProvider.now())
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val dao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )

    val updated = dao.updateTag(
      com.quran.data.model.bookmark.Tag(collectionsRepository.managedSystemCollectionId, "Managed Renamed")
    )

    assertThat(updated).isFalse()
    assertThat(collectionsRepository.updateCount).isEqualTo(0)
  }

  @Test
  fun `bookmark tags are populated from collection bookmarks`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    val taggedBookmark = dao.bookmarks().single()
    assertThat(taggedBookmark.tags).containsExactly(tagId)
    assertThat(dao.getBookmarkTagIds(bookmark.id)).containsExactly(tagId)
  }

  @Test
  fun `updating single bookmark tags replaces old collection links`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(firstTagId), deleteNonTagged = true)
    timestampProvider.timestampSeconds = 2_000

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(secondTagId), deleteNonTagged = true)

    assertThat(dao.bookmarks().single().tags).containsExactly(secondTagId)
    assertThat(dao.bookmarks().single().timestamp).isEqualTo(1_000)
  }

  @Test
  fun `updating multiple bookmark tags adds without deleting existing links`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(firstTagId), deleteNonTagged = true)

    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(secondTagId), deleteNonTagged = false)

    assertThat(dao.bookmarks().single().tags).containsExactly(firstTagId, secondTagId)
  }

  @Test
  fun `clearing tags from default bookmark preserves bookmark`() = runTest {
    val defaultCollectionId = CollectionsRepositoryImpl(database)
      .getAllCollections()
      .single { collection -> collection.isDefault }
      .id
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    dao.updateBookmarkTags(arrayOf(bookmark.id), emptySet(), deleteNonTagged = true)

    val remainingBookmark = dao.bookmarks().single()
    assertThat(remainingBookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(remainingBookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(remainingBookmark.tags).isEmpty()
    assertThat(CollectionBookmarksRepositoryImpl(database).getBookmarksForCollection(defaultCollectionId))
      .hasSize(1)
  }

  @Test
  fun `clearing tags from custom only bookmark removes bookmark`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(6, 76)
    dao.replaceAyahBookmarkCollections(suraAyah, setOf(tagId))
    val bookmark = dao.bookmarks().single()

    dao.updateBookmarkTags(arrayOf(bookmark.id), emptySet(), deleteNonTagged = true)

    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `moving custom only bookmark adds new collection before removing old collection`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(6, 76)
    dao.replaceAyahBookmarkCollections(suraAyah, setOf(firstTagId))

    val changed = dao.replaceAyahBookmarkCollections(suraAyah, setOf(secondTagId))

    assertThat(changed).isTrue()
    assertThat(dao.bookmarks().single().tags).containsExactly(secondTagId)
  }

  @Test
  fun `replacing unchanged bookmark collections returns false`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(6, 76)
    assertThat(dao.replaceAyahBookmarkCollections(suraAyah, setOf(tagId))).isTrue()

    val changed = dao.replaceAyahBookmarkCollections(suraAyah, setOf(tagId))

    assertThat(changed).isFalse()
    assertThat(dao.bookmarks().single().tags).containsExactly(tagId)
  }

  @Test
  fun `empty replacement for missing bookmark returns false`() = runTest {
    val changed = dao.replaceAyahBookmarkCollections(SuraAyah(6, 76), emptySet())

    assertThat(changed).isFalse()
    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `bookmark tag removal preserves highlight membership`() = runTest {
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val suraAyah = SuraAyah(6, 76)
    val tagId = dao.addTag("Review")
    val highlightTimestamp = timestampProvider.now()
    collectionBookmarksRepository.setHighlight(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      color = AyahHighlightColor.BLUE,
      timestamp = highlightTimestamp
    )
    val managedBookmarkId = database.bookmarksQueries
      .getBookmarkForAyah(suraAyah.sura.toLong(), suraAyah.ayah.toLong())
      .executeAsOne()
      .local_id
      .toString()
    timestampProvider.timestampSeconds = 2_000

    dao.replaceAyahBookmarkCollections(suraAyah, setOf(tagId))
    assertThat(dao.bookmarks().single().id).isEqualTo(managedBookmarkId)
    assertThat(dao.bookmarks().single().timestamp).isEqualTo(1_000)
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), emptySet(), deleteNonTagged = true)

    assertThat(collectionBookmarksRepository.getHighlightsFlow().first())
      .containsExactly(
        com.quran.shared.persistence.model.AyahHighlight(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          color = AyahHighlightColor.BLUE,
          lastUpdated = highlightTimestamp
        )
      )
  }

  @Test
  fun `collection replacement preserves highlight membership`() = runTest {
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val suraAyah = SuraAyah(2, 255)
    val defaultCollectionId = CollectionsRepositoryImpl(database)
      .getAllCollections()
      .single { collection -> collection.isDefault }
      .id
    collectionBookmarksRepository.setHighlight(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      color = AyahHighlightColor.GREEN,
      timestamp = timestampProvider.now()
    )

    dao.replaceAyahBookmarkCollections(suraAyah, setOf(defaultCollectionId))

    assertThat(collectionBookmarksRepository.getHighlightsFlow().first().single().color)
      .isEqualTo(AyahHighlightColor.GREEN)
  }

  @Test
  fun `collection replacement reuses managed row without rewriting bookmark timestamp`() = runTest {
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val suraAyah = SuraAyah(2, 255)
    val tagId = dao.addTag("Review")
    collectionBookmarksRepository.setHighlight(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      color = AyahHighlightColor.RED,
      timestamp = timestampProvider.now()
    )
    val managedBookmarkId = database.bookmarksQueries
      .getBookmarkForAyah(suraAyah.sura.toLong(), suraAyah.ayah.toLong())
      .executeAsOne()
      .local_id
      .toString()
    timestampProvider.timestampSeconds = 2_000

    dao.replaceAyahBookmarkCollections(suraAyah, setOf(tagId))

    val bookmark = dao.bookmarks().single()
    assertThat(bookmark.id).isEqualTo(managedBookmarkId)
    assertThat(bookmark.timestamp).isEqualTo(1_000)
    assertThat(bookmark.tags).containsExactly(tagId)
    assertThat(collectionBookmarksRepository.getHighlightsFlow().first().single().color)
      .isEqualTo(AyahHighlightColor.RED)
  }

  @Test
  fun `highlight-only persistence row is not exposed as a bookmark`() = runTest {
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val suraAyah = SuraAyah(2, 255)
    collectionBookmarksRepository.setHighlight(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      color = AyahHighlightColor.PURPLE,
      timestamp = timestampProvider.now()
    )

    assertThat(dao.bookmarks()).isEmpty()
    assertThat(dao.bookmarksFlow().first()).isEmpty()
    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isFalse()
  }

  @Test
  fun `removing bookmark from tag unlinks only that collection`() = runTest {
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(firstTagId, secondTagId), deleteNonTagged = true)

    dao.removeBookmarkFromTag(bookmark, firstTagId)

    assertThat(dao.bookmarks().single().tags).containsExactly(secondTagId)
  }

  @Test
  fun `removing bookmark from tag ignores stale unrelated memberships`() = runTest {
    val collectionsRepository = CollectionsRepositoryImpl(database)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val firstTagId = dao.addTag("First")
    val secondTagId = dao.addTag("Second")
    val defaultCollectionId = collectionsRepository.getAllCollections()
      .single { collection -> collection.isDefault }
      .id
    val suraAyah = SuraAyah(2, 255)
    dao.replaceAyahBookmarkCollections(
      suraAyah,
      setOf(defaultCollectionId, firstTagId, secondTagId)
    )
    val bookmark = dao.bookmarks().single()
    val staleCollections = RepositoryBackedTestBookmarkCollectionsState(
      collectionsRepository,
      collectionBookmarksRepository,
      appCoroutineScope
    ).currentCollectionsWithBookmarks()
    val staleState = object : BookmarkCollectionsState {
      override val collectionsWithBookmarks =
        MutableStateFlow<List<CollectionWithAyahBookmarks>?>(staleCollections)
    }
    val staleDao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = staleState,
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )

    assertThat(collectionsRepository.deleteCollection(firstTagId)).isTrue()

    assertThat(staleDao.removeBookmarkFromTag(bookmark, secondTagId)).isTrue()
    assertThat(collectionBookmarksRepository.getBookmarksForCollection(secondTagId)).isEmpty()
    assertThat(
      collectionBookmarksRepository.getBookmarksForCollection(defaultCollectionId)
        .map { membership -> membership.sura to membership.ayah }
    ).containsExactly(suraAyah.sura to suraAyah.ayah)
  }

  @Test
  fun `removing a bookmark clears collection links`() = runTest {
    val tagId = dao.addTag("Review")
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()
    dao.updateBookmarkTags(arrayOf(bookmark.id), setOf(tagId), deleteNonTagged = true)

    dao.removeBookmarks(listOf(bookmark))

    assertThat(dao.bookmarks()).isEmpty()
    assertThat(dao.getBookmarkTagIds(bookmark.id)).isEmpty()
  }

  @Test
  fun `removing a bookmark preserves its highlight`() = runTest {
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    val bookmark = dao.bookmarks().single()
    collectionBookmarksRepository.setHighlight(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      color = AyahHighlightColor.BLUE,
      timestamp = timestampProvider.now()
    )

    dao.removeBookmarks(listOf(bookmark))

    assertThat(dao.bookmarks()).isEmpty()
    assertThat(collectionBookmarksRepository.getHighlightsFlow().first().single().color)
      .isEqualTo(AyahHighlightColor.BLUE)
  }

  @Test
  fun `deleting an ayah bookmark preserves its highlight`() = runTest {
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database)
    val suraAyah = SuraAyah(2, 255)
    addDefaultBookmark(suraAyah)
    collectionBookmarksRepository.setHighlight(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      color = AyahHighlightColor.PURPLE,
      timestamp = timestampProvider.now()
    )

    val deleted = dao.deleteAyahBookmark(suraAyah)

    assertThat(deleted).isTrue()
    assertThat(dao.bookmarks()).isEmpty()
    assertThat(collectionBookmarksRepository.getHighlightsFlow().first().single().color)
      .isEqualTo(AyahHighlightColor.PURPLE)
  }

  /** Seeds a default-collection bookmark for tests exercising another DAO behavior. */
  private suspend fun addDefaultBookmark(suraAyah: SuraAyah) {
    val defaultCollectionId = CollectionsRepositoryImpl(database)
      .getAllCollections()
      .single { collection -> collection.isDefault }
      .id
    BookmarksRepositoryImpl(database).replaceAyahBookmarkCollections(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      collectionIds = listOf(defaultCollectionId),
      timestamp = timestampProvider.now()
    )
  }

  private class DefaultCollectionTestRepository(
    timestamp: PlatformDateTime
  ) : CollectionsRepository {
    private val defaultCollection = SyncCollection(
      name = "Favorites",
      lastUpdated = timestamp,
      id = "101",
      isDefault = true,
      isSystem = true
    )
    private val managedSystemCollection = SyncCollection(
      name = "Managed",
      lastUpdated = timestamp,
      id = "102",
      isSystem = true
    )

    val defaultCollectionId: String = defaultCollection.id
    val managedSystemCollectionId: String = managedSystemCollection.id

    var updateCount = 0
      private set

    override suspend fun getAllCollections(): List<SyncCollection> {
      return listOf(managedSystemCollection, defaultCollection)
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
      return flowOf(listOf(managedSystemCollection, defaultCollection))
    }
  }
}
