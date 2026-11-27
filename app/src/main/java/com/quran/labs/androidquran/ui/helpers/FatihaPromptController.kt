package com.quran.labs.androidquran.ui.helpers

import android.os.Bundle
import com.quran.labs.androidquran.data.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class FatihaPromptController(
  private val scope: CoroutineScope,
  private val readLabel: String,
  private val returnLabel: (page: Int) -> String,
  private val host: Host
) {
  interface Host {
    fun currentPage(): Int
    fun visiblePagesContainFatiha(): Boolean
    fun openFatiha()
    fun returnToPage(page: Int)
    fun onPromptChanged(label: String?, showClose: Boolean)
  }

  private enum class Mode { HIDDEN, OFFER, RETURN }

  private var mode: Mode = Mode.HIDDEN
  private var returnPage: Int = Constants.NO_PAGE
  private var dismissJob: Job? = null

  fun onVisiblePagesChanged() {
    if (mode == Mode.RETURN && !host.visiblePagesContainFatiha()) {
      hide()
    }
  }

  fun showOffer() {
    if (mode == Mode.RETURN || host.visiblePagesContainFatiha()) return
    mode = Mode.OFFER
    returnPage = Constants.NO_PAGE
    publish()
    dismissJob?.cancel()
    dismissJob = scope.launch {
      delay(OFFER_DURATION_MS)
      hide()
    }
  }

  private fun showReturn(page: Int) {
    dismissJob?.cancel()
    dismissJob = null
    returnPage = page
    mode = Mode.RETURN
    publish()
  }

  fun hide() {
    dismissJob?.cancel()
    dismissJob = null
    mode = Mode.HIDDEN
    returnPage = Constants.NO_PAGE
    publish()
  }

  fun onClick() {
    when (mode) {
      Mode.OFFER -> {
        if (host.visiblePagesContainFatiha()) {
          hide()
        } else {
          showReturn(host.currentPage())
          host.openFatiha()
        }
      }
      Mode.RETURN -> {
        val page = returnPage
        hide()
        if (page != Constants.NO_PAGE) {
          host.returnToPage(page)
        }
      }
      Mode.HIDDEN -> Unit
    }
  }

  fun saveState(outState: Bundle) {
    outState.putString(KEY_MODE, mode.name)
    outState.putInt(KEY_RETURN_PAGE, returnPage)
  }

  fun restoreState(state: Bundle) {
    when (state.getString(KEY_MODE)) {
      Mode.OFFER.name -> showOffer()
      Mode.RETURN.name -> {
        val page = state.getInt(KEY_RETURN_PAGE, Constants.NO_PAGE)
        if (page == Constants.NO_PAGE || !host.visiblePagesContainFatiha()) {
          hide()
        } else {
          showReturn(page)
        }
      }
    }
  }

  private fun publish() {
    when (mode) {
      Mode.OFFER -> host.onPromptChanged(readLabel, showClose = true)
      Mode.RETURN -> host.onPromptChanged(returnLabel(returnPage), showClose = false)
      Mode.HIDDEN -> host.onPromptChanged(null, showClose = false)
    }
  }

  companion object {
    private const val OFFER_DURATION_MS = 15_000L
    private const val KEY_MODE = "fatiha_prompt_mode"
    private const val KEY_RETURN_PAGE = "fatiha_prompt_return_page"
  }
}
