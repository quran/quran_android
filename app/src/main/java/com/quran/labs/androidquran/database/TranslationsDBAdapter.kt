package com.quran.labs.androidquran.database

import android.content.Context
import android.util.SparseArray
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.translation.data.TranslationsDataSource
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationsDBAdapter @Inject constructor(
  @ApplicationContext private val context: Context,
  private val dataSource: TranslationsDataSource,
  private val quranFileUtils: QuranFileUtils
) {

  fun getTranslations(): Flow<List<LocalTranslation>> {
    return dataSource.translations()
      .filterNotNull()
      .map { translations ->
        translations.filter { quranFileUtils.hasTranslation(context, it.filename) }
      }
  }

  suspend fun translationsHash(): SparseArray<LocalTranslation> {
    return withContext(Dispatchers.IO) {
      val result = SparseArray<LocalTranslation>()
      val translations = getTranslations().first()
      for (item in translations) {
        result.put(item.id.toInt(), item)
      }
      result
    }
  }

  suspend fun deleteTranslationByFileName(filename: String) {
    dataSource.removeTranslation(filename)
  }

  suspend fun writeTranslationUpdates(updates: List<TranslationItem>): Boolean {
    return withContext(Dispatchers.IO) {
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

      true
    }
  }
}
