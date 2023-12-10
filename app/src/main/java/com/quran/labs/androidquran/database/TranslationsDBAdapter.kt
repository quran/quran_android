package com.quran.labs.androidquran.database

import android.content.Context
import android.util.SparseArray
import androidx.annotation.WorkerThread
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.translation.data.TranslationsDataSource
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationsDBAdapter @Inject constructor(
  @ApplicationContext private val context: Context,
  private val dataSource: TranslationsDataSource,
  private val quranFileUtils: QuranFileUtils
) {
  private val scope = MainScope()

  fun getTranslations(): Flow<List<LocalTranslation>> {
    return dataSource.translations()
      .filterNotNull()
      .map { translations ->
        translations.filter { quranFileUtils.hasTranslation(context, it.filename) }
      }
  }

  @WorkerThread
  fun legacyGetTranslations(): List<LocalTranslation> {
    return runBlocking { getTranslations().first() }
  }

  @WorkerThread
  fun getTranslationsHash(): SparseArray<LocalTranslation> {
    val result = SparseArray<LocalTranslation>()
    for (item in legacyGetTranslations()) {
      result.put(item.id.toInt(), item)
    }
    return result
  }

  suspend fun deleteTranslationByFileName(filename: String) {
    dataSource.removeTranslation(filename)
  }

  @WorkerThread
  fun legacyDeleteTranslationByFileName(filename: String) {
    runBlocking {
      deleteTranslationByFileName(filename)
    }
  }

  @WorkerThread
  fun legacyWriteTranslationUpdates(updates: List<TranslationItem>): Boolean {
    return runBlocking { writeTranslationUpdates(updates) }
  }

  suspend fun writeTranslationUpdates(updates: List<TranslationItem>): Boolean {
    val (available, unavailable) = updates.partition { it.exists() }

    val needNextOrder = available.any { it.displayOrder == -1 }
    val nextOrder = if (needNextOrder) {
      dataSource.maximumDisplayOrder().toInt() + 1
    } else {
      (available.maxOfOrNull { it.displayOrder } ?: 0) + 1
    }

    val items = if (needNextOrder) {
      var nextOrderNumber = nextOrder
      available.map { item ->
        if (item.displayOrder == -1) {
          item.copy(displayOrder = nextOrderNumber++)
        } else {
          item
        }
      }
    } else {
      available
    }

    dataSource.updateTranslations(items.map { it.asLocalTranslation() })
    dataSource.removeTranslationsById(unavailable.map { it.translation.id.toLong() })

    return true
  }
}
