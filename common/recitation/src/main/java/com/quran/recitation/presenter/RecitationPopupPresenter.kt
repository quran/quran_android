package com.quran.recitation.presenter

import android.graphics.RectF
import android.widget.ImageView
import com.quran.data.model.AyahWord
import com.quran.data.model.selection.SelectionIndicator
import com.quran.recitation.presenter.RecitationPopupPresenter.PopupContainer

interface RecitationPopupPresenter : Presenter<PopupContainer> {

  override fun bind(what: PopupContainer)
  override fun unbind(what: PopupContainer)

  interface PopupContainer {
    fun getQuranPageImageView(page: Int): ImageView?
    fun getSelectionBoundsForWord(page: Int, word: AyahWord): SelectionIndicator.SelectedItemPosition?
    fun getBoundsForWord(word: AyahWord): List<RectF>?
  }

}
