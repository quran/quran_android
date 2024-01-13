package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import com.quran.data.core.QuranInfo
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackAyah
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.toolbar.R
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject

class TagBookmarkFragment : TagBookmarkDialog() {
  private var scope: CoroutineScope = MainScope()

  @Inject
  lateinit var readingEventPresenter: ReadingEventPresenter

  @Inject
  lateinit var audioStatusRepository: AudioStatusRepository

  @Inject
  lateinit var quranInfo: QuranInfo

  object Provider : AyahActionFragmentProvider {
    override val order = SlidingPagerAdapter.TAG_PAGE
    override val iconResId = R.drawable.ic_tag
    override fun newAyahActionFragment() = TagBookmarkFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    scope = MainScope()
    readingEventPresenter.ayahSelectionFlow
      .combine(audioStatusRepository.audioPlaybackFlow) { selectedAyah, playbackState ->
        val playbackAyah = playbackState.currentPlaybackAyah()
        val start = when {
          selectedAyah !is AyahSelection.None -> selectedAyah.startSuraAyah()
          playbackAyah != null -> playbackAyah
          else -> null
        }

        if (start != null) {
          val page = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
          tagBookmarkPresenter.setAyahBookmarkMode(start.sura, start.ayah, page)
        }
      }
      .launchIn(scope)
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  override fun shouldInject() = false
}
