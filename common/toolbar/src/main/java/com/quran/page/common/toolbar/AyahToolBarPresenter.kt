package com.quran.page.common.toolbar

import androidx.annotation.VisibleForTesting
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class AyahToolBarPresenter private constructor(
  private val bookmarksDao: BookmarksDao,
  private val readingEventPresenter: ReadingEventPresenter,
  private val ioDispatcher: CoroutineDispatcher
) {
  @Inject
  constructor(
    bookmarksDao: BookmarksDao,
    readingEventPresenter: ReadingEventPresenter
  ) : this(bookmarksDao, readingEventPresenter, Dispatchers.IO)

  private var bindScope: CoroutineScope? = null
  private var bookmarkToCheckChannel: Channel<SuraAyah>? = null

  private var currentSuraAyah: SuraAyah? = null
  private var ayahSelectionReactor: AyahSelectionReactor? = null

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    val selectedAyah = ayahSelection.startSuraAyah()
    val isDifferentSuraAyah = selectedAyah != currentSuraAyah
    currentSuraAyah = selectedAyah

    if (isDifferentSuraAyah && selectedAyah != null) {
      bookmarkToCheckChannel?.trySend(selectedAyah)
    }

    ayahSelectionReactor?.onSelectionChanged(
      ayahSelection.selectionIndicator(), isDifferentSuraAyah)
  }

  fun bind(reactor: AyahSelectionReactor) {
    bindScope?.cancel()
    bookmarkToCheckChannel?.close()

    val newScope = MainScope()
    val newChannel = Channel<SuraAyah>(Channel.CONFLATED)
    bindScope = newScope
    bookmarkToCheckChannel = newChannel
    ayahSelectionReactor = reactor
    currentSuraAyah = null

    readingEventPresenter
      .ayahSelectionFlow
      .onEach { onAyahSelectionChanged(it) }
      .launchIn(newScope)

    newChannel.consumeAsFlow()
      .onEach {
        val result = withContext(ioDispatcher) {
          bookmarksDao.isSuraAyahBookmarked(it)
        }

        if (readingEventPresenter.currentAyahSelection().startSuraAyah() == it) {
          ayahSelectionReactor?.updateBookmarkStatus(result)
        }
      }
      .launchIn(newScope)
  }

  fun unbind(reactor: AyahSelectionReactor) {
    if (ayahSelectionReactor === reactor) {
      ayahSelectionReactor = null
      currentSuraAyah = null
      bindScope?.cancel()
      bindScope = null
      bookmarkToCheckChannel?.close()
      bookmarkToCheckChannel = null
    }
  }

  companion object {
    @VisibleForTesting
    fun forTest(
      bookmarksDao: BookmarksDao,
      readingEventPresenter: ReadingEventPresenter,
      ioDispatcher: CoroutineDispatcher
    ): AyahToolBarPresenter =
      AyahToolBarPresenter(bookmarksDao, readingEventPresenter, ioDispatcher)
  }
}
