package com.quran.recitation.pages

import android.app.Activity
import android.view.View
import com.quran.data.model.SuraAyah
import com.quran.recitation.presenter.RecitationHighlightsPresenter.HighlightAction

/**
 * Draws mushaf pages as the reading screen does (the page type in use, its theme, night mode), but
 * outside of it: e.g. to play a recorded session back on its own pages.
 */
interface PagePreviews {
  /** Pages for [activity], until [PageRenderer.close] (at the latest when the activity goes). */
  fun open(activity: Activity): PageRenderer
}

interface PageRenderer {
  /** Whether pages are drawn at night (the reading screen's setting). */
  val isNightMode: Boolean

  /**
   * A view of [page], loading as the reading screen does (downloading a missing image). [onTapped]
   * gets the ayah tapped, and its word when the page knows where its words are.
   */
  fun page(page: Int, onTapped: (ayah: SuraAyah, word: Int?) -> Unit): PagePreview

  fun close()
}

interface PagePreview {
  val view: View

  /** Changes the page's highlights; they are kept until its layout has loaded. */
  fun apply(actions: List<HighlightAction>)

  /** Stops loading and frees the page's image. */
  fun release()
}
