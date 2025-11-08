package com.quran.mobile.mapper.imlaei.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@SingleIn(AppScope::class)
class ImlaeiUthmaniDatabaseProvider @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val databaseName = "word_alignment.db"
  private val databasePath = File(quranFileManager.databaseDirectory(), databaseName)
  private var cachedDatabase: ImlaeiUthmaniMappingDatabase? = null

  private suspend fun ensureWordAlignmentDatabase(): Boolean {
    return if (databasePath.exists()) {
      true
    } else {
      withContext(Dispatchers.IO) {
        runCatching {
          quranFileManager.copyFromAssetsRelative(
            databaseName,
            databaseName,
            quranFileManager.databaseDirectory()
          )
        }.isSuccess
      }
    }
  }

  suspend fun provideDatabase(): ImlaeiUthmaniMappingDatabase? {
    val cached = cachedDatabase
    return cached
        ?: if (ensureWordAlignmentDatabase()) {
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
