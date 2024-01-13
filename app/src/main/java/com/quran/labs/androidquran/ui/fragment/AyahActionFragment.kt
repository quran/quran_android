package com.quran.labs.androidquran.ui.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.endSuraAyah
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackAyah
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

abstract class AyahActionFragment : Fragment() {
  private var scope: CoroutineScope = MainScope()

  @Inject
  lateinit var readingEventPresenter: ReadingEventPresenter

  @Inject
  lateinit var audioStatusRepository: AudioStatusRepository

  protected var start: SuraAyah? = null
  protected var end: SuraAyah? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    scope = MainScope()
    readingEventPresenter.ayahSelectionFlow
      .combine(audioStatusRepository.audioPlaybackFlow) { selectedAyah, playbackStatus ->
        val playbackAyah = playbackStatus.currentPlaybackAyah()
        val (previousStart, previousEnd) = start to end
        if (selectedAyah !is AyahSelection.None) {
          start = selectedAyah.startSuraAyah()
          end = selectedAyah.endSuraAyah()
        } else if (playbackAyah != null) {
          start = playbackAyah
          end = playbackAyah
        }

        if (previousStart != start || previousEnd != end) {
          refreshView()
        }
      }
      .launchIn(scope)

    readingEventPresenter.detailsPanelFlow
      .map {
        onToggleDetailsPanel(it)
      }
      .launchIn(scope)
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  open fun onToggleDetailsPanel(isVisible: Boolean) { }

  protected abstract fun refreshView()
}
