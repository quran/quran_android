package com.quran.mobile.recitation.presenter

import com.quran.data.di.QuranPageScope
import com.quran.data.di.QuranReadingPageScope
import com.quran.recitation.presenter.RecitationPopupPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter.PopupContainer
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@QuranPageScope
@ContributesBinding(scope = QuranReadingPageScope::class, boundType = RecitationPopupPresenter::class)
class RecitationPopupPresenterImpl @Inject constructor(): RecitationPopupPresenter {
  override fun bind(what: PopupContainer) {}
  override fun unbind(what: PopupContainer) {}
}
