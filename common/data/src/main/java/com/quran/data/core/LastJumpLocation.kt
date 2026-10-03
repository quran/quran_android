package com.quran.data.core

import com.quran.data.di.AppScope
import com.quran.data.model.JumpLocation
import com.quran.data.model.bookmark.ReadingBookmarkType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@SingleIn(AppScope::class)
class LastJumpLocation @Inject constructor() {
  val jump: StateFlow<Jump?>
    field = MutableStateFlow(null)

  fun update(location: JumpLocation, bookmarkType: ReadingBookmarkType? = null) {
    jump.value = Jump(location, bookmarkType)
  }

  data class Jump(val location: JumpLocation, val bookmarkType: ReadingBookmarkType?)
}
