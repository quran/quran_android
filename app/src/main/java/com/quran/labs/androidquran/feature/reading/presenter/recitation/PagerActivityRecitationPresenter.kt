package com.quran.labs.androidquran.feature.reading.presenter.recitation

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_PAUSE
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.common.toolbar.R
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.ui.listener.AudioBarRecitationListener
import com.quran.page.common.toolbar.AyahToolBar
import com.quran.reading.common.ReadingEventPresenter
import com.quran.recitation.events.RecitationEventPresenter
import com.quran.recitation.events.RecitationSelection
import com.quran.recitation.presenter.RecitationPlaybackPresenter
import com.quran.recitation.presenter.RecitationPresenter
import com.quran.recitation.presenter.RecitationSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class PagerActivityRecitationPresenter @Inject constructor(
  private val quranInfo: QuranInfo,
  private val readingEventPresenter: ReadingEventPresenter,
  private val recitationPresenter: RecitationPresenter,
  private val recitationEventPresenter: RecitationEventPresenter,
  private val recitationPlaybackPresenter: RecitationPlaybackPresenter,
  private val recitationSettings: RecitationSettings,
) : AudioBarRecitationListener, DefaultLifecycleObserver, LifecycleEventObserver {
  private val scope = MainScope()

  private lateinit var bridge: Bridge

  class Bridge(
    val isDualPageVisible: () -> Boolean,
    val currentPage: () -> Int,
    val ayahToolBar: () -> AyahToolBar?,
    val ensurePage: (ayah: SuraAyah) -> Unit,
    val showSlider: (sliderPage: Int) -> Unit,
    /** Called when a recitation session starts or ends (e.g. to handle back presses). */
    val onSessionActiveChanged: (isActive: Boolean) -> Unit = {},
    /** Stops any Quran audio that is playing, so it doesn't compete with the user's recitation. */
    val stopAudio: () -> Unit = {},
    val isShowingTranslation: () -> Boolean = { false },
    val switchToQuran: () -> Unit = {},
  )

  fun isRecitationEnabled(): Boolean {
    return recitationPresenter.isRecitationEnabled()
  }

  fun bind(activity: PagerActivity, bridge: Bridge) {
    if (!isRecitationEnabled()) return
    this.bridge = bridge
    activity.lifecycle.addObserver(this)
  }

  fun unbind(activity: PagerActivity) {
    activity.lifecycle.removeObserver(this)
    scope.cancel()
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    if (!isRecitationEnabled()) return

    val activity = source as PagerActivity
    when (event) {
      ON_CREATE -> {
        recitationPresenter.bind(activity)
        subscribe()
      }
      ON_RESUME -> {
        recitationPlaybackPresenter.bind(activity)
      }
      ON_PAUSE -> {
        recitationPlaybackPresenter.unbind(activity)
      }
      ON_DESTROY -> {
        recitationPresenter.unbind(activity)
        unbind(activity)
      }
      else -> {}
    }
  }

  private fun subscribe() {
    // Handle Changes to Recitation Enabled
    recitationPresenter.isRecitationEnabledFlow()
      .onEach { onRecitationEnabledStateChanged(it) }
      .launchIn(scope)

    // Session start / end
    recitationEventPresenter.recitationSessionFlow
      .map { it != null }
      .distinctUntilChanged()
      .onEach { bridge.onSessionActiveChanged(it) }
      .launchIn(scope)

    // Recitation Events
    recitationEventPresenter.recitationChangeFlow
      .onEach { onRecitationChange(it) }
      .launchIn(scope)
    recitationEventPresenter.recitationSelectionFlow
      .onEach { onRecitationSelection(it) }
      .launchIn(scope)

    // Recitation Playback Events
    recitationPlaybackPresenter.recitationPlaybackFlow
      .onEach { onRecitationPlayback(it) }
      .launchIn(scope)
  }

  fun onSessionEnd() {
    // End recitation service if running
    if (isRecitationEnabled() && recitationEventPresenter.hasRecitationSession()) {
      recitationPresenter.endSession()
    }
  }

  fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
    recitationPresenter.onRequestPermissionsResult(requestCode, grantResults)
  }

  // Recitation Events

  private fun onRecitationEnabledStateChanged(isEnabled: Boolean) {
    bridge.ayahToolBar()?.apply {
      if (isRecitationEnabled != isEnabled) {
        isRecitationEnabled = isEnabled
        setMenuItemVisibility(R.id.cab_recite_from_here, isEnabled)
      }
    }
  }

  private fun onRecitationChange(ayah: SuraAyah) {
    val curAyah = recitationEventPresenter.recitationSession()?.currentAyah() ?: ayah
    bridge.ensurePage(curAyah)
  }

  private fun onRecitationSelection(selection: RecitationSelection) {
    selection.ayah()?.let { bridge.ensurePage(it) }
  }

  private fun onRecitationPlayback(playback: RecitationSelection) {
    playback.ayah()?.let { bridge.ensurePage(it) }
  }

  fun hasSession(): Boolean = isRecitationEnabled() && recitationEventPresenter.hasRecitationSession()

  /**
   * The toolbar's "Memorize" action: starts from the selected ayah (or the first ayah on the page)
   * or ends the current session.
   */
  fun onMemorizeMenuPressed() {
    if (!isRecitationEnabled()) return
    if (recitationEventPresenter.hasRecitationSession()) {
      recitationPresenter.endSession()
    } else {
      prepareForNewSession()
      recitationPresenter.startOrStopRecitation({ firstAyahToMemorize() })
    }
  }

  private fun prepareForNewSession() {
    bridge.stopAudio()
    if (bridge.isShowingTranslation()) bridge.switchToQuran()
  }

  // AudioBarRecitationListener

  override fun onRecitationPressed() {
    if (!recitationEventPresenter.hasRecitationSession()) prepareForNewSession()
    recitationPresenter.startOrStopRecitation({ ayahToStartFrom() })
  }

  override fun onRecitationLongPressed() {
    recitationPresenter.startOrStopRecitation({ ayahToStartFrom() }, true)
  }

  override fun onRecitationTranscriptPressed() {
    if (!recitationPresenter.supportsTranscript()) return
    if (recitationEventPresenter.hasRecitationSession()) {
      bridge.showSlider(SlidingPagerAdapter.TRANSCRIPT_PAGE)
    } else {
      Timber.e("Transcript pressed but we don't have a session; this should never happen")
    }
  }

  override fun onRecitationHintPressed() {
    recitationPresenter.onHintRequested()
  }

  override fun onHideVersesPressed() {
    recitationSettings.toggleAyahVisibility()
  }

  override fun onEndRecitationSessionPressed() {
    recitationPresenter.endSession()
  }

  override fun onPlayRecitationPressed() {
    recitationPlaybackPresenter.play()
  }

  override fun onPauseRecitationPressed() {
    recitationPlaybackPresenter.pauseIfPlaying()
  }

  private fun firstAyahToMemorize(): SuraAyah {
    val page = if (bridge.isDualPageVisible()) bridge.currentPage() - 1 else bridge.currentPage()
    return readingEventPresenter.currentAyahSelection().startSuraAyah()
      ?: SuraAyah(quranInfo.getSuraOnPage(page), quranInfo.getFirstAyahOnPage(page))
  }

  private fun ayahToStartFrom(): SuraAyah {
    val page = if (bridge.isDualPageVisible()) {
      // subtracting 1 because in dual mode currentPage gives 2, 4, 6.. instead of 1, 3, 5..
      // but we want to start from the first ayah of the first visible page
      bridge.currentPage() - 1
    } else {
      bridge.currentPage()
    }

    // If we're in ayah mode, start from selected ayah
    return readingEventPresenter.currentAyahSelection().startSuraAyah()
      // If a sura starts on this page, assume the user meant to start there
      ?: quranInfo.getListOfSurahWithStartingOnPage(page).firstNotNullOfOrNull { SuraAyah(it, 1) }
      // Otherwise, start from the beginning of the page
      ?: SuraAyah(quranInfo.getSuraOnPage(page), quranInfo.getFirstAyahOnPage(page))
  }
}
