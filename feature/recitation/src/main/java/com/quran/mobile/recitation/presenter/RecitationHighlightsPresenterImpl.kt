package com.quran.mobile.recitation.presenter

import com.quran.data.di.QuranPageScope
import com.quran.recitation.presenter.RecitationHighlightsPresenter
import com.quran.recitation.presenter.RecitationHighlightsPresenter.RecitationPage
import dev.zacsweers.metro.Inject

@QuranPageScope
class RecitationHighlightsPresenterImpl @Inject constructor(): RecitationHighlightsPresenter {
  override fun bind(what: RecitationPage) {}
  override fun unbind(what: RecitationPage) {}

  override fun refresh() {}
}
