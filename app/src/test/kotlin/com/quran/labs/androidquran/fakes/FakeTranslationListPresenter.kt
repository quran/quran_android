package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.presenter.translationlist.TranslationListCallback
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake implementation of TranslationListPresenter for testing.
 *
 * Provides minimal implementation with configurable translation data.
 * Used by BaseTranslationPresenterTest.
 */
class FakeTranslationListPresenter : TranslationListPresenter {

  private var translationsList: List<LocalTranslation> = emptyList()

  fun setTranslations(translations: List<LocalTranslation>) {
    translationsList = translations
  }

  override fun translations(): Flow<List<LocalTranslation>> {
    return flowOf(translationsList.sortedBy { it.displayOrder })
  }

  override fun registerForTranslations(callback: TranslationListCallback): Job {
    return Job()
  }
}
