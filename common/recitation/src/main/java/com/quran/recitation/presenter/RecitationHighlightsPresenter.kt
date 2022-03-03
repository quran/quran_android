package com.quran.recitation.presenter

import com.quran.data.model.highlight.HighlightInfo
import com.quran.data.model.highlight.HighlightType
import com.quran.recitation.presenter.RecitationHighlightsPresenter.RecitationPage

interface RecitationHighlightsPresenter : Presenter<RecitationPage> {

  override fun bind(what: RecitationPage)
  override fun unbind(what: RecitationPage)

  /** Force a refresh of highlights */
  fun refresh()

  interface RecitationPage {
    val pageNumbers: Set<Int>
    fun applyHighlights(highlights: List<HighlightAction>)
  }

  sealed class HighlightAction {
    data class Highlight(val highlightInfo: HighlightInfo): HighlightAction()
    data class Unhighlight(val highlightInfo: HighlightInfo): HighlightAction()
    data class UnhighlightAll(val highlightType: HighlightType, val page: Int? = null): HighlightAction()
  }

}
