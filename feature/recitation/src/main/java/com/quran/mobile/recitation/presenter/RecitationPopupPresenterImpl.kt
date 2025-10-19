package com.quran.mobile.recitation.presenter

import com.quran.data.di.QuranPageScope
import com.quran.recitation.presenter.RecitationPopupPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter.PopupContainer
import dev.zacsweers.metro.Inject

@QuranPageScope
class RecitationPopupPresenterImpl @Inject constructor(): RecitationPopupPresenter {
  override fun bind(what: PopupContainer) {}
  override fun unbind(what: PopupContainer) {}
}
