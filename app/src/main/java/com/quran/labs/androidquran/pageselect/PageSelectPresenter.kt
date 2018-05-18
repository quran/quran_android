package com.quran.labs.androidquran.pageselect

import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.presenter.Presenter
import dagger.Reusable
import javax.inject.Inject

@Reusable
class PageSelectPresenter @Inject
    constructor(private val pageTypes:
                Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>) :
    Presenter<PageSelectActivity> {
  private var currentView: PageSelectActivity? = null

  override fun bind(what: PageSelectActivity) {
    currentView = what
  }

  override fun unbind(what: PageSelectActivity?) {
    if (currentView === what) {
      currentView = null
    }
  }
}
