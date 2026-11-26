package com.quran.labs.androidquran.common.ui.core

/**
 * Allows View-backed pages in [com.quran.labs.androidquran.ui.helpers.QuranPageAdapter] to be
 * pooled and rebound across page flips without Fragment transactions or DI subcomponent churn.
 */
interface PooledPageView {
  fun rebindPage(page: Int)
  fun cleanup()
}
