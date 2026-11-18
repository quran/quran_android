package com.quran.labs.androidquran.presenter.quran.ayahtracker

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.playback.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeHighlightsDao
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.reading.common.ReadingEventPresenter
import com.quran.recitation.events.RecitationEventPresenter
import com.quran.recitation.presenter.RecitationHighlightsPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter
import com.quran.recitation.presenter.RecitationPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AyahTrackerPresenterTest {

  private lateinit var audioStatusRepository: AudioStatusRepository
  private lateinit var presenter: AyahTrackerPresenter
  private lateinit var quranInfo: QuranInfo

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    val context = ApplicationProvider.getApplicationContext<Context>()
    val pageProvider = FakePageProvider()
    quranInfo = QuranInfo(pageProvider.getDataSource())
    val display = checkNotNull(
      context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    )
    val pageSizeCalculator = object : PageSizeCalculator {
      override fun getWidthParameter(): String = ""
      override fun getTabletWidthParameter(): String = ""
      override fun setOverrideParameter(parameter: String) = Unit
    }
    val quranFileUtils = QuranFileUtils(
      context,
      pageProvider,
      QuranScreenInfo(context, display, pageSizeCalculator)
    )

    audioStatusRepository = AudioStatusRepository()
    presenter = AyahTrackerPresenter(
      quranInfo = quranInfo,
      quranFileUtils = quranFileUtils,
      quranDisplayData = QuranDisplayData(quranInfo),
      quranSettings = QuranSettings.getInstance(context),
      readingEventPresenter = ReadingEventPresenter(quranInfo),
      bookmarksDao = FakeBookmarksDao(),
      highlightsDao = FakeHighlightsDao(),
      readingBookmarksDao = FakeReadingBookmarksDao(),
      audioStatusRepository = audioStatusRepository,
      recitationPresenter = DisabledRecitationPresenter(),
      recitationEventPresenter = RecitationEventPresenter(),
      recitationPopupPresenter = NoOpRecitationPopupPresenter(),
      recitationHighlightsPresenter = NoOpRecitationHighlightsPresenter()
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun unbind_clearsAudioHighlightsAndResetsState() {
    val ayah = SuraAyah(1, 1)
    val item = RecordingAyahTrackerItem(quranInfo.getPageFromSuraAyah(ayah.sura, ayah.ayah))
    val handler = handlerFor(item)

    presenter.bind(handler)
    audioStatusRepository.updateAyahPlayback(playbackFor(ayah))

    assertThat(item.highlightedAudioAyahs).contains(ayah)

    presenter.unbind(handler)

    assertThat(item.unhighlightedTypes).contains(HighlightTypes.AUDIO)
  }

  @Test
  fun rebindAfterAudioAdvancesAcrossPages_highlightsNewAyahWithoutStaleHighlight() {
    val firstAyah = SuraAyah(1, 1)
    val nextPageAyah = SuraAyah(2, 1)
    val firstItem = RecordingAyahTrackerItem(
      quranInfo.getPageFromSuraAyah(firstAyah.sura, firstAyah.ayah)
    )
    val firstHandler = handlerFor(firstItem)

    presenter.bind(firstHandler)
    audioStatusRepository.updateAyahPlayback(playbackFor(firstAyah))
    assertThat(firstItem.highlightedAudioAyahs).contains(firstAyah)
    presenter.unbind(firstHandler)

    val nextPageItem = RecordingAyahTrackerItem(
      quranInfo.getPageFromSuraAyah(nextPageAyah.sura, nextPageAyah.ayah)
    )
    val nextPageHandler = handlerFor(nextPageItem)
    presenter.bind(nextPageHandler)
    audioStatusRepository.updateAyahPlayback(playbackFor(nextPageAyah))

    assertThat(firstItem.unhighlightedTypes).contains(HighlightTypes.AUDIO)
    assertThat(nextPageItem.highlightedAudioAyahs).contains(nextPageAyah)
    assertThat(nextPageItem.highlightedAudioAyahs).doesNotContain(firstAyah)
    assertThat(nextPageItem.unhighlightedAudioAyahs).doesNotContain(firstAyah)

    presenter.unbind(nextPageHandler)
  }

  @Test
  fun rebindWithSameAyahAfterUnbind_rehighlightsAyah() {
    val ayah = SuraAyah(1, 1)
    val firstItem = RecordingAyahTrackerItem(quranInfo.getPageFromSuraAyah(ayah.sura, ayah.ayah))
    val firstHandler = handlerFor(firstItem)

    presenter.bind(firstHandler)
    audioStatusRepository.updateAyahPlayback(playbackFor(ayah))
    assertThat(firstItem.highlightedAudioAyahs).contains(ayah)
    presenter.unbind(firstHandler)

    val reboundItem = RecordingAyahTrackerItem(quranInfo.getPageFromSuraAyah(ayah.sura, ayah.ayah))
    val reboundHandler = handlerFor(reboundItem)
    presenter.bind(reboundHandler)

    assertThat(firstItem.unhighlightedTypes).contains(HighlightTypes.AUDIO)
    assertThat(reboundItem.highlightedAudioAyahs).contains(ayah)

    presenter.unbind(reboundHandler)
  }

  private fun handlerFor(vararg trackerItems: AyahTrackerItem): AyahInteractionHandler =
    object : AyahInteractionHandler {
      override fun getAyahTrackerItems(): Array<AyahTrackerItem> = trackerItems.toList().toTypedArray()
    }

  private fun playbackFor(ayah: SuraAyah): AudioStatus.Playback {
    val qari = QariItem(
      id = 1,
      name = "Test Qari",
      url = "https://example.invalid/audio/",
      path = "test_qari",
      hasGaplessAlternative = false
    )
    val audioPathInfo = AudioPathInfo(
      urlFormat = "https://example.invalid/audio/",
      localDirectory = "test_qari",
      gaplessDatabase = null,
      allowedExtensions = listOf("mp3")
    )
    val request = AudioRequest(
      start = ayah,
      end = ayah,
      qari = qari,
      enforceBounds = false,
      shouldStream = false,
      audioPathInfo = audioPathInfo
    )
    return AudioStatus.Playback(ayah, request, PlaybackStatus.PLAYING)
  }

  private class RecordingAyahTrackerItem(page: Int) : AyahTrackerItem(page) {
    val highlightedAudioAyahs = mutableListOf<SuraAyah>()
    val unhighlightedAudioAyahs = mutableListOf<SuraAyah>()
    val unhighlightedTypes = mutableListOf<HighlightType>()

    override fun onHighlightAyah(
      page: Int,
      sura: Int,
      ayah: Int,
      word: Int,
      type: HighlightType,
      scrollToAyah: Boolean
    ): Boolean {
      if (type == HighlightTypes.AUDIO) {
        highlightedAudioAyahs += SuraAyah(sura, ayah)
      }
      return true
    }

    override fun onUnHighlightAyah(
      page: Int,
      sura: Int,
      ayah: Int,
      word: Int,
      type: HighlightType
    ) {
      if (type == HighlightTypes.AUDIO) {
        unhighlightedAudioAyahs += SuraAyah(sura, ayah)
      }
    }

    override fun onUnHighlightAyahType(type: HighlightType) {
      unhighlightedTypes += type
    }
  }

  private class DisabledRecitationPresenter : RecitationPresenter {
    private val enabled = MutableStateFlow(false)

    override fun bind(what: Activity) = Unit
    override fun unbind(what: Activity) = Unit
    override fun isRecitationEnabled(): Boolean = false
    override fun isRecitationEnabledFlow() = enabled
    override fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) = Unit
    override fun startOrStopRecitation(startAyah: () -> SuraAyah, showModeSelection: Boolean) = Unit
    override fun endSession() = Unit
  }

  private class NoOpRecitationPopupPresenter : RecitationPopupPresenter {
    override fun bind(what: RecitationPopupPresenter.PopupContainer) = Unit
    override fun unbind(what: RecitationPopupPresenter.PopupContainer) = Unit
  }

  private class NoOpRecitationHighlightsPresenter : RecitationHighlightsPresenter {
    override fun bind(what: RecitationHighlightsPresenter.RecitationPage) = Unit
    override fun unbind(what: RecitationHighlightsPresenter.RecitationPage) = Unit
    override fun refresh() = Unit
  }
}
