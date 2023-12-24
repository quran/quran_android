package com.quran.mobile.recitation.presenter

import com.quran.data.di.QuranPageScope
import com.quran.data.di.QuranReadingPageScope
import com.quran.recitation.presenter.RecitationHighlightsPresenter
import com.quran.recitation.presenter.RecitationHighlightsPresenter.RecitationPage
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@QuranPageScope
@ContributesBinding(scope = QuranReadingPageScope::class, boundType = RecitationHighlightsPresenter::class)
class RecitationHighlightsPresenterImpl @Inject constructor(): RecitationHighlightsPresenter {
  override fun bind(what: RecitationPage) {}
  override fun unbind(what: RecitationPage) {}

  override fun refresh() {}
}
