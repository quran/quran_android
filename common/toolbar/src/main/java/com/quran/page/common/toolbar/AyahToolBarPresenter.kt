package com.quran.page.common.toolbar

import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AyahToolBarPresenter @Inject constructor(
  bookmarksDao: BookmarksDao,
  private val readingEventPresenter: ReadingEventPresenter
) {
  private val scope = MainScope()
  private val bookmarkToCheckChannel = Channel<SuraAyah>(Channel.CONFLATED)

  private var currentSuraAyah: SuraAyah? = null
  private var ayahSelectionReactor: AyahSelectionReactor? = null

  init {
    readingEventPresenter
      .ayahSelectionFlow
      .onEach { onAyahSelectionChanged(it) }
      .launchIn(scope)

    bookmarkToCheckChannel.consumeAsFlow()
      .onEach {
        val result = withContext(Dispatchers.IO) {
          bookmarksDao.isSuraAyahBookmarked(it)
        }

        if (readingEventPresenter.currentAyahSelection().startSuraAyah() == it) {
          ayahSelectionReactor?.updateBookmarkStatus(result)
        }
      }
      .launchIn(scope)
  }

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    val selectedAyah = ayahSelection.startSuraAyah()
    val isDifferentSuraAyah = selectedAyah != currentSuraAyah
    currentSuraAyah = selectedAyah

    if (isDifferentSuraAyah && selectedAyah != null) {
      bookmarkToCheckChannel.trySend(selectedAyah)
    }

    ayahSelectionReactor?.onSelectionChanged(
      ayahSelection.selectionIndicator(), isDifferentSuraAyah)
  }

  fun bind(reactor: AyahSelectionReactor) {
    ayahSelectionReactor = reactor
    onAyahSelectionChanged(readingEventPresenter.ayahSelectionFlow.value)
  }

  fun unbind(reactor: AyahSelectionReactor) {
    if (ayahSelectionReactor === reactor) {
      ayahSelectionReactor = null
    }
  }
}
