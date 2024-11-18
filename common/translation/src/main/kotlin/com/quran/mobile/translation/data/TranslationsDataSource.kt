package com.quran.mobile.translation.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.mobile.translation.mapper.LocalTranslationMapper
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationsDataSource @Inject constructor(translationsDatabase: TranslationsDatabase) {
  private val translationsQueries = translationsDatabase.translationsQueries

  private val scope = MainScope()
  private val translations by lazy {
    translationsQueries.all(LocalTranslationMapper.mapper)
      .asFlow()
      .mapToList(Dispatchers.IO)
      .stateIn(scope, SharingStarted.Lazily, null)
  }

  fun translations(): StateFlow<List<LocalTranslation>?> = translations

  suspend fun updateTranslations(items: List<LocalTranslation>) {
    withContext(Dispatchers.IO) {
      translationsQueries.transaction {
        items.forEach {
          translationsQueries.update(
            id = it.id,
            name = it.name,
            translator = it.translator,
            translatorForeign = it.translatorForeign,
            filename = it.filename,
            url = it.url,
            languageCode = it.languageCode,
            version = it.version.toLong(),
            minimumRequiredVersion = it.minimumVersion.toLong(),
            userDisplayOrder = it.displayOrder.toLong()
          )
        }
      }
    }
  }

  suspend fun removeTranslation(filename: String) {
    withContext(Dispatchers.IO) {
      translationsQueries.deleteByFileName(filename)
    }
  }

  suspend fun removeTranslationsById(ids: List<Long>) {
    withContext(Dispatchers.IO) {
      translationsQueries.transaction {
        ids.forEach {
          translationsQueries.deleteById(it)
        }
      }
    }
  }

  suspend fun maximumDisplayOrder(): Long {
    return withContext(Dispatchers.IO) {
      translationsQueries.greatestDisplayOrder().executeAsOne().MAX ?: 0
    }
  }
}
