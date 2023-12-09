package com.quran.mobile.translation.data

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.mobile.translation.mapper.LocalTranslationMapper
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

  suspend fun removeTranslation(filename: String) {
    translationsQueries.deleteByFileName(filename)
  }

  suspend fun removeTranslationsById(ids: List<Long>) {
    translationsQueries.transaction {
      ids.forEach {
        translationsQueries.deleteById(it)
      }
    }
  }

  suspend fun maximumDisplayOrder(): Long {
    return translationsQueries.greatestDisplayOrder().awaitAsOne().MAX ?: 0
  }
}
