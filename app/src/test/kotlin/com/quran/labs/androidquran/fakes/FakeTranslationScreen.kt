package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter
import com.quran.mobile.translation.model.LocalTranslation

/**
 * Fake implementation of TranslationPresenter.TranslationScreen for testing.
 *
 * Records the pages whose verses were shown and how many times the scroll position was restored.
 */
class FakeTranslationScreen : TranslationPresenter.TranslationScreen {
  val shownPages: MutableList<Int> = mutableListOf()
  var scrollPositionUpdates: Int = 0

  override fun setVerses(
    page: Int,
    translations: Array<LocalTranslation>,
    verses: List<QuranAyahInfo>
  ) {
    shownPages.add(page)
  }

  override fun updateScrollPosition() {
    scrollPositionUpdates++
  }
}
