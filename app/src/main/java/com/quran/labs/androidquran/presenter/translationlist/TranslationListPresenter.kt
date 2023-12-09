package com.quran.labs.androidquran.presenter.translationlist

import com.quran.mobile.translation.data.TranslationsDataSource
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class TranslationListPresenter @Inject constructor(
  private val dataSource: TranslationsDataSource
) {
  fun translations(): Flow<List<LocalTranslation>> {
    return dataSource.translations()
      .filterNotNull()
      .map { translations -> translations.sortedBy { it.displayOrder } }
  }
}
