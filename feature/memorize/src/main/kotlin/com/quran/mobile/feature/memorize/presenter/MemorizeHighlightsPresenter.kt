package com.quran.mobile.feature.memorize.presenter

import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranPageScope
import com.quran.data.model.highlight.HighlightInfo
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.recitation.presenter.RecitationHighlightsPresenter
import com.quran.recitation.presenter.RecitationHighlightsPresenter.HighlightAction
import com.quran.recitation.presenter.RecitationHighlightsPresenter.RecitationPage
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Hides the words of the passage that haven't been recited yet and marks mistakes, hints and
 * skipped words on the page(s) being shown. Only the difference with what is already applied is
 * sent, so revealing a word is cheap.
 */
@QuranPageScope
class MemorizeHighlightsPresenter @Inject constructor(
  private val controller: MemorizeSessionController,
  private val quranInfo: QuranInfo,
) : RecitationHighlightsPresenter {

  private var scope: CoroutineScope? = null
  private var page: RecitationPage? = null
  private var applied: Set<Mark> = emptySet()

  override fun bind(what: RecitationPage) {
    page = what
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    this.scope = scope
    controller.state.onEach { render(it) }.launchIn(scope)
  }

  override fun unbind(what: RecitationPage) {
    scope?.cancel()
    scope = null
    page = null
    applied = emptySet()
  }

  /** Called after the page's coordinates load: masks sent before that were dropped. */
  override fun refresh() {
    val page = page ?: return
    page.applyHighlights(MemorizeHighlightTypes.ALL.map { HighlightAction.UnhighlightAll(it) })
    applied = emptySet()
    render(controller.state.value)
  }

  private fun render(state: MemorizeState?) {
    val page = page ?: return
    val desired = if (state == null) emptySet() else marksFor(state, page.pageNumbers)
    val actions = ArrayList<HighlightAction>()
    (applied - desired).forEach { actions += HighlightAction.Unhighlight(it.info()) }
    (desired - applied).forEach { actions += HighlightAction.Highlight(it.info()) }
    applied = desired
    if (actions.isNotEmpty()) page.applyHighlights(actions)
  }

  private fun marksFor(state: MemorizeState, pages: Set<Int>): Set<Mark> =
    MemorizeMasks.marks(state, pages.map { quranInfo.getVerseRangeForPage(it) })

  private fun Mark.info() = HighlightInfo(sura, ayah, word, type, scrollToAyah = false)
}
