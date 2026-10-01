package com.quran.labs.androidquran.model.bookmark

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.toPersistenceImportData
import com.quran.shared.persistence.QuranDatabase
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.importdata.PersistenceImportRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33], qualifiers = "en")
@RunWith(RobolectricTestRunner::class)
class ImportedCollectionNamesTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var driver: JdbcSqliteDriver
  private lateinit var importer: PersistenceImportRepositoryImpl
  private lateinit var collections: CollectionsRepositoryImpl
  private lateinit var collectionBookmarks: CollectionBookmarksRepositoryImpl

  @Before
  fun setUp() {
    driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    QuranDatabase.Schema.create(driver)
    val database = QuranDatabase(driver)
    importer = PersistenceImportRepositoryImpl(database)
    collections = CollectionsRepositoryImpl(database)
    collectionBookmarks = CollectionBookmarksRepositoryImpl(database)
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `reserved favorites tag retains its original membership when its import id collides`() = runTest {
    importData(
      MobileSyncImportData(
        bookmarks = (1..15).map { ayah ->
          MobileSyncImportBookmark("bookmark-$ayah", 2, ayah, 1_234_000L)
        },
        collections = listOf(MobileSyncImportCollection("import-favorites", "Favorites", 1_234_000L)),
        collectionBookmarks = (1..5).map { ayah ->
          MobileSyncImportCollectionBookmark("import-favorites", "bookmark-$ayah", 1_234_000L)
        }
      )
    )

    val tags = collections.getAllCollections()
    val importedTag = tags.single { it.name == "Favorites (Imported)" }
    val defaultTag = tags.single { it.isDefault }
    assertThat(importedTag.isSystem).isFalse()
    assertThat(collectionBookmarks.getBookmarksForCollection(importedTag.id).map { it.ayah })
      .containsExactly(1, 2, 3, 4, 5)
    assertThat(collectionBookmarks.getBookmarksForCollection(defaultTag.id).filter { it.ayah in 6..15 })
      .hasSize(10)
  }

  @Test
  fun `reserved tags merge matching imported names and preserve numbered tags`() = runTest {
    val original = dataWithTaggedBookmarks(
      listOf("Favorites", "Favorites (Imported)", "Favorites (Imported 2)")
    )
    val data = original.copy(
      collections = original.collections.mapIndexed { index, collection ->
        collection.copy(timestampMillis = (index + 1) * 1_234_000L)
      },
      collectionBookmarks = original.collectionBookmarks +
        MobileSyncImportCollectionBookmark("tag-1", "bookmark-0", 4_567_000L)
    )

    val converted = data.toPersistenceImportData(context)
    val mergedCollection = converted.collections.single { it.name == "Favorites (Imported)" }
    assertThat(mergedCollection.lastUpdated.toEpochMilliseconds()).isEqualTo(2_468_000L)
    val sharedMembership = converted.collectionBookmarks.single {
      it.collectionImportId == mergedCollection.importId && it.sura == 2 && it.ayah == 1
    }
    assertThat(sharedMembership.lastUpdated.toEpochMilliseconds()).isEqualTo(4_567_000L)

    importData(data)
    importData(data.copy(collections = data.collections.reversed()))

    val tags = collections.getAllCollections().filterNot { it.isSystem }
    assertThat(tags.map { it.name }).containsExactly("Favorites (Imported)", "Favorites (Imported 2)")
    val mergedTag = tags.single { it.name == "Favorites (Imported)" }
    assertThat(collectionBookmarks.getBookmarksForCollection(mergedTag.id).map { it.ayah })
      .containsExactly(1, 2)
    val numberedTag = tags.single { it.name == "Favorites (Imported 2)" }
    assertThat(collectionBookmarks.getBookmarksForCollection(numberedTag.id).map { it.ayah })
      .containsExactly(3)
  }

  @Test
  fun `reserved names preserve spelling and import as user collections`() = runTest {
    val names = listOf(
      " fAvOrItEs ",
      "system:highlights:blue",
      "system:highlights:red",
      "system:highlights:green",
      "system:highlights:yellow",
      "system:highlights:purple"
    )

    importData(dataWithTaggedBookmarks(names))

    val tags = collections.getAllCollections().filterNot { it.isSystem }
    assertThat(tags.map { it.name }).containsExactlyElementsIn(names.map { "$it (Imported)" })
    names.forEachIndexed { index, name ->
      val tag = tags.single { it.name == "$name (Imported)" }
      assertThat(collectionBookmarks.getBookmarksForCollection(tag.id).map { it.ayah })
        .containsExactly(index + 1)
    }
  }

  private suspend fun importData(data: MobileSyncImportData) {
    importer.importData(data.toPersistenceImportData(context), deleteExisting = false)
  }

  private fun dataWithTaggedBookmarks(names: List<String>): MobileSyncImportData {
    return MobileSyncImportData(
      bookmarks = names.mapIndexed { index, _ ->
        MobileSyncImportBookmark("bookmark-$index", 2, index + 1, 1_234_000L)
      },
      collections = names.mapIndexed { index, name ->
        MobileSyncImportCollection("tag-$index", name, 1_234_000L)
      },
      collectionBookmarks = names.mapIndexed { index, _ ->
        MobileSyncImportCollectionBookmark("tag-$index", "bookmark-$index", 1_234_000L)
      }
    )
  }
}
