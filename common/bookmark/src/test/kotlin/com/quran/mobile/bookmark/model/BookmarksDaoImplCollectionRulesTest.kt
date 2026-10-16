package com.quran.mobile.bookmark.model

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.CollectionNameException
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.db.inMemoryMobileSyncDatabase
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.MobileSyncDatabase
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/** How mobile-sync's collection rules reach the app through [BookmarksDaoImpl]. */
class BookmarksDaoImplCollectionRulesTest {

  private lateinit var database: MobileSyncDatabase
  private lateinit var dao: BookmarksDaoImpl
  private lateinit var appCoroutineScope: AppCoroutineScope

  @Before
  fun setup() {
    database = inMemoryMobileSyncDatabase()
    appCoroutineScope = AppCoroutineScope()
    val quranInfo = QuranInfo(MadaniDataSource())
    val collectionsRepository = CollectionsRepositoryImpl(database.synced)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(database.synced)
    dao = BookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      bookmarksRepository = BookmarksRepositoryImpl(database.synced),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = FakeMobileSyncTimestampProvider(),
      appCoroutineScope = appCoroutineScope
    )
  }

  @After
  fun tearDown() {
    appCoroutineScope.cancel()
  }

  @Test
  fun `adding a tag named Favorites in any case or spacing is reserved`() = runTest {
    val error = errorOf { dao.addTag(" fAVORITES ") }

    assertThat(error).isInstanceOf(CollectionNameException.Reserved::class.java)
    assertThat((error as CollectionNameException.Reserved).name).isEqualTo(" fAVORITES ")
    assertThat(dao.userTags()).isEmpty()
  }

  @Test
  fun `adding a tag starting with the server's system prefix is reserved`() = runTest {
    val error = errorOf { dao.addTag("System: Ramadan") }

    assertThat(error).isInstanceOf(CollectionNameException.Reserved::class.java)
    assertThat(dao.userTags()).isEmpty()
  }

  @Test
  fun `adding a collection with a reserved name is reserved`() = runTest {
    val error = errorOf { dao.addCollection("system:highlights:blue") }

    assertThat(error).isInstanceOf(CollectionNameException.Reserved::class.java)
  }

  @Test
  fun `adding a blank tag is too short`() = runTest {
    val error = errorOf { dao.addTag("   ") }

    assertThat(error).isInstanceOf(CollectionNameException.TooShort::class.java)
    assertThat((error as CollectionNameException.TooShort).minLength).isEqualTo(1)
  }

  @Test
  fun `adding a tag longer than the server allows is too long`() = runTest {
    val error = errorOf { dao.addTag("a".repeat(256)) }

    assertThat(error).isInstanceOf(CollectionNameException.TooLong::class.java)
    assertThat((error as CollectionNameException.TooLong).maxLength).isEqualTo(255)
    assertThat(dao.userTags()).isEmpty()
  }

  @Test
  fun `adding a tag whose name is taken returns the existing tag`() = runTest {
    val reviewId = dao.addTag("Review")

    val addedId = dao.addTag(" review ")

    assertThat(addedId).isEqualTo(reviewId)
    assertThat(dao.userTags()).containsExactly(Tag(reviewId, "Review"))
  }

  @Test
  fun `renaming a tag to a reserved name is refused and keeps its name`() = runTest {
    val reviewId = dao.addTag("Review")

    val error = errorOf { dao.updateTag(Tag(reviewId, "Favorites")) }

    assertThat(error).isInstanceOf(CollectionNameException.Reserved::class.java)
    assertThat(dao.userTags()).containsExactly(Tag(reviewId, "Review"))
  }

  @Test
  fun `renaming a tag beyond the length limit is too long`() = runTest {
    val reviewId = dao.addTag("Review")

    val error = errorOf { dao.updateTag(Tag(reviewId, "a".repeat(256))) }

    assertThat(error).isInstanceOf(CollectionNameException.TooLong::class.java)
    assertThat(dao.userTags()).containsExactly(Tag(reviewId, "Review"))
  }

  @Test
  fun `a new database has Favorites as its read-only default collection`() = runTest {
    val favorites = dao.tags().single()

    assertThat(favorites.name).isEqualTo("Favorites")
    assertThat(favorites.isDefault).isTrue()
    assertThat(favorites.isSystem).isTrue()
    assertThat(favorites.isReadOnly).isTrue()
  }

  @Test
  fun `Favorites can't be renamed or deleted`() = runTest {
    val favorites = dao.tags().single()

    assertThat(dao.updateTag(favorites.copy(name = "Loved"))).isFalse()
    dao.removeTags(listOf(favorites))

    assertThat(dao.tags()).containsExactly(favorites)
  }

  @Test
  fun `a protected color collection lists as a tag that can't be renamed or deleted`() = runTest {
    // what sync writes for a color collection an older Quran.com app created, which the server protects
    database.synced.collectionsQueries.collectionInsert(
      name = "Green",
      name_key = "green",
      is_default = 0,
      is_system = 0,
      is_read_only = 1,
      created_at = 1_000,
      updated_at = 1_000
    )
    val green = dao.userTags().single()
    assertThat(green.name).isEqualTo("Green")
    assertThat(green.isReadOnly).isTrue()

    assertThat(dao.updateTag(green.copy(name = "Olive"))).isFalse()
    dao.removeTags(listOf(green))

    assertThat(dao.userTags()).containsExactly(green)
  }

  private suspend fun errorOf(block: suspend () -> Any): Throwable? {
    return runCatching { block() }.exceptionOrNull()
  }

  private suspend fun BookmarksDaoImpl.userTags() = tags().filterNot { tag -> tag.isSystem }
}
