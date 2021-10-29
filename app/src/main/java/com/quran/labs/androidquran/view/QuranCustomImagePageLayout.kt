package com.quran.labs.androidquran.view

import android.content.Context
import android.view.View

class QuranCustomImagePageLayout(
  context: Context,
  private val wrappedView: View
) : QuranPageLayout(context) {

  init {
    isFullWidth = true
    initialize()
  }

  override fun generateContentView(context: Context, isLandscape: Boolean): View {
    return wrappedView
  }

  override fun shouldWrapWithScrollView(): Boolean = false
}
