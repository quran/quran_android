package com.quran.labs.androidquran.fakes

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
class FakeTranslationListPresenter {

  private var translationsList: List<LocalTranslation> = emptyList()

  fun setTranslations(translations: List<LocalTranslation>) {
    translationsList = translations
  }

  fun translations(): Flow<List<LocalTranslation>> {
    return flowOf(translationsList.sortedBy { it.displayOrder })
  }

  fun registerForTranslations(callback: TranslationListCallback): Job {
    // Minimal implementation - just return a no-op job
    return Job()
  }

  interface TranslationListCallback {
    fun onTranslationsUpdated(titles: Array<String>, translations: List<LocalTranslation>)
  }
}
