package com.quran.labs.androidquran.feature.reading.model

import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
class LatestPageTracker @Inject constructor() {
  data class LatestPage(val page: Int, val pageType: String)

  private val mutableLatestPage = MutableStateFlow<LatestPage?>(null)

  val latestPage: StateFlow<LatestPage?> = mutableLatestPage.asStateFlow()

  fun updateLatestPage(page: Int, pageType: String) {
    mutableLatestPage.value = LatestPage(page, pageType)
  }
}
