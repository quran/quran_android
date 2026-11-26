package com.quran.mobile.mapper.imlaei.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@SingleIn(AppScope::class)
@Inject
class ImlaeiUthmaniDatabaseProvider(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val databaseName = "word_alignment.db"
  private val mutex = Mutex()

  @Volatile
  private var cachedDatabase: ImlaeiUthmaniMappingDatabase? = null

  private suspend fun ensureWordAlignmentDatabase(
    databaseDirectory: File,
    databasePath: File
  ): Boolean {
    return withContext(Dispatchers.IO) {
      if (databasePath.exists()) {
        true
      } else {
        runCatching {
          quranFileManager.copyFromAssetsRelative(
            databaseName,
            databaseName,
            databaseDirectory
          )
        }.isSuccess
      }
    }
  }

  suspend fun provideDatabase(): ImlaeiUthmaniMappingDatabase? {
    cachedDatabase?.let { return it }
    return mutex.withLock {
      cachedDatabase?.let { return@withLock it }
      val databaseDirectory = withContext(Dispatchers.IO) {
        quranFileManager.databaseDirectory()
      }
      val databasePath = File(databaseDirectory, databaseName)
      if (ensureWordAlignmentDatabase(databaseDirectory, databasePath)) {
        val filePath = databasePath.absolutePath
        val driver = AndroidSqliteDriver(ImlaeiUthmaniMappingDatabase.Schema, appContext, name = filePath)
        val database = ImlaeiUthmaniMappingDatabase(driver)
        cachedDatabase = database
        database
      } else {
        null
      }
    }
  }
}
