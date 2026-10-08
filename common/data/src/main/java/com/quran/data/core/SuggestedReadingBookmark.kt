package com.quran.data.core

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.JumpLocation
import com.quran.data.model.bookmark.ReadingBookmarkType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
class SuggestedReadingBookmark @Inject constructor(
  private val lastJumpLocation: LastJumpLocation,
  readingBookmarksDao: ReadingBookmarksDao,
  selector: ReadingBookmarkSelector,
  appCoroutineScope: AppCoroutineScope
) {
  val bookmarkType = combine(lastJumpLocation.jump, readingBookmarksDao.readingBookmarksFlow()) { jump, bookmarks ->
    jump to bookmarks
  }
    .runningFold(null as ReadingBookmarkType?) { previous, (jump, bookmarks) ->
      selector.select(jump?.location, bookmarks, jump?.bookmarkType, previous)
    }
    .map { it ?: ReadingBookmarkType.PURPLE }
    .stateIn(appCoroutineScope, SharingStarted.Eagerly, ReadingBookmarkType.PURPLE)

  fun onJump(location: JumpLocation, bookmarkType: ReadingBookmarkType? = null) {
    lastJumpLocation.update(location, bookmarkType)
  }
}
