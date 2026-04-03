package com.quran.labs.androidquran.feature.reading.presenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.fakes.FakeAudioExtensionDecider
import com.quran.labs.androidquran.fakes.FakeAudioFileUtils
import com.quran.labs.androidquran.fakes.FakeAudioPresenterScreen
import com.quran.labs.androidquran.fakes.FakeAudioUtils
import com.quran.labs.androidquran.fakes.FakeQuranDisplayData
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.test.TestDataFactory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for AudioPresenter.
 *
 * Tests cover:
 * - Basic playback when files are available
 * - Streaming mode when files are missing
 * - Download triggers when files are missing (download intent content verified)
 * - Start/End ayah validation and swapping
 * - Permission callbacks
 * - Gapless vs gapped audio handling
 */
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AudioPresenterTest {

  private lateinit var fakeQuranDisplayData: FakeQuranDisplayData
  private lateinit var fakeAudioUtils: FakeAudioUtils
  private lateinit var fakeAudioExtensionDecider: FakeAudioExtensionDecider
  private lateinit var fakeAudioFileUtils: FakeAudioFileUtils
  private lateinit var fakeScreen: FakeAudioPresenterScreen
  private lateinit var presenter: AudioPresenter

  private val testQariGapped = QariItem(
    id = 1,
    name = "Test Qari Gapped",
    url = "https://example.com/audio/",
    path = "test_qari_gapped",
    hasGaplessAlternative = false,
    db = null
  )

  private val testQariGapless = QariItem(
    id = 2,
    name = "Test Qari Gapless",
    url = "https://example.com/gapless/",
    path = "test_qari_gapless",
    hasGaplessAlternative = false,
    db = "gapless_db"
  )

  private val start = TestDataFactory.fatihaStart() // 1:1
  private val end = TestDataFactory.fatihaEnd()     // 1:7
  private val localPath = "/sdcard/quran/audio/test_qari"

  @Before
  fun setup() {
    fakeAudioUtils = FakeAudioUtils()
    fakeAudioExtensionDecider = FakeAudioExtensionDecider()
    fakeAudioFileUtils = FakeAudioFileUtils()
    fakeScreen = FakeAudioPresenterScreen()
    fakeQuranDisplayData = FakeQuranDisplayData()

    fakeAudioExtensionDecider.extensionForQariMap[testQariGapped] = "mp3"
    fakeAudioExtensionDecider.extensionForQariMap[testQariGapless] = "mp3"
    fakeAudioExtensionDecider.allowedExtensionsMap[testQariGapped] = listOf("mp3")
    fakeAudioExtensionDecider.allowedExtensionsMap[testQariGapless] = listOf("mp3")

    fakeAudioUtils.localQariUrls[testQariGapped] = localPath
    fakeAudioUtils.localQariUrls[testQariGapless] = localPath
    fakeAudioUtils.qariUrls[testQariGapped to "mp3"] = "https://example.com/audio/"
    fakeAudioUtils.qariUrls[testQariGapless to "mp3"] = "https://example.com/gapless/"

    fakeAudioFileUtils.haveAyaPositionFileResult = true
    fakeAudioFileUtils.ayaPositionFileUrlValue = "https://example.com/ayah.db"
    fakeAudioFileUtils.quranAyahDatabaseDirectoryValue = File("/tmp/quran_test_ayah")
    fakeAudioFileUtils.gaplessDatabaseRootUrlValue = "https://example.com/gapless"

    presenter = AudioPresenter(
      appContext = ApplicationProvider.getApplicationContext(),
      quranDisplayData = fakeQuranDisplayData,
      audioUtil = fakeAudioUtils,
      audioExtensionDecider = fakeAudioExtensionDecider,
      quranFileUtils = fakeAudioFileUtils
    )
  }

  // ==================== Playback Tests ====================

  @Test
  fun `should play audio when all files are downloaded`() {
    fakeAudioUtils.haveAllFilesResult = true
    fakeAudioUtils.shouldDownloadBasmallahResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handlePlaybackCalls).hasSize(1)
    val request = fakeScreen.handlePlaybackCalls[0]!!
    assertThat(request.start).isEqualTo(start)
    assertThat(request.end).isEqualTo(end)
    assertThat(request.shouldStream).isFalse()
  }

  @Test
  fun `should stream when files are missing and streaming enabled`() {
    fakeAudioUtils.haveAllFilesResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = true
    )

    assertThat(fakeScreen.handlePlaybackCalls).hasSize(1)
    val request = fakeScreen.handlePlaybackCalls[0]!!
    assertThat(request.shouldStream).isTrue()
    assertThat(request.audioPathInfo.urlFormat).contains("https://example.com/audio/")
  }

  @Test
  fun `should not stream when all files are available even if streaming enabled`() {
    fakeAudioUtils.haveAllFilesResult = true
    fakeAudioUtils.shouldDownloadBasmallahResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = true
    )

    assertThat(fakeScreen.handlePlaybackCalls).hasSize(1)
    assertThat(fakeScreen.handlePlaybackCalls[0]!!.shouldStream).isFalse()
  }

  @Test
  fun `should swap start and end when start is greater than end`() {
    val reversedStart = TestDataFactory.fatihaEnd()  // 1:7
    val reversedEnd = TestDataFactory.fatihaStart()  // 1:1
    fakeAudioUtils.haveAllFilesResult = true
    fakeAudioUtils.shouldDownloadBasmallahResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = reversedStart, end = reversedEnd, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handlePlaybackCalls).hasSize(1)
    val request = fakeScreen.handlePlaybackCalls[0]!!
    assertThat(request.start).isEqualTo(reversedEnd) // swapped to 1:1
    assertThat(request.end).isEqualTo(reversedStart) // swapped to 1:7
  }

  // ==================== Download Tests ====================

  @Test
  fun `should trigger aya position file download when missing`() {
    fakeAudioFileUtils.haveAyaPositionFileResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)
    assertThat(fakeScreen.handlePlaybackCalls).isEmpty()
  }

  @Test
  fun `should trigger gapless database download when db file missing`() {
    // haveAyaPositionFile = true (default); return a path that doesn't exist on disk
    fakeAudioUtils.qariDatabasePaths[testQariGapless] =
      "/tmp/nonexistent_gapless_${System.currentTimeMillis()}.db"
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapless,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)
    assertThat(fakeScreen.handlePlaybackCalls).isEmpty()
  }

  @Test
  fun `should trigger basmallah download when basmallah file missing`() {
    // haveAyaPositionFile = true (default), gaplessDb = null (testQariGapped has no db)
    fakeAudioUtils.shouldDownloadBasmallahResult = true
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)
    assertThat(fakeScreen.handlePlaybackCalls).isEmpty()
    val intent = fakeScreen.handleRequiredDownloadCalls[0]
    @Suppress("DEPRECATION")
    assertThat(intent?.getSerializableExtra(QuranDownloadService.EXTRA_START_VERSE) as SuraAyah?)
      .isEqualTo(start)
    @Suppress("DEPRECATION")
    assertThat(intent?.getSerializableExtra(QuranDownloadService.EXTRA_END_VERSE) as SuraAyah?)
      .isEqualTo(start)
  }

  @Test
  fun `should trigger full range download when audio files missing`() {
    // haveAyaPositionFile = true, gaplessDb = null, shouldDownloadBasmallah = false (defaults)
    fakeAudioUtils.haveAllFilesResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)
    assertThat(fakeScreen.handlePlaybackCalls).isEmpty()
    val intent = fakeScreen.handleRequiredDownloadCalls[0]
    @Suppress("DEPRECATION")
    assertThat(intent?.getSerializableExtra(QuranDownloadService.EXTRA_START_VERSE) as SuraAyah?)
      .isEqualTo(start)
    @Suppress("DEPRECATION")
    assertThat(intent?.getSerializableExtra(QuranDownloadService.EXTRA_END_VERSE) as SuraAyah?)
      .isEqualTo(end)
    assertThat(intent?.getBooleanExtra(QuranDownloadService.EXTRA_IS_GAPLESS, false)).isFalse()
  }

  @Test
  fun `should bypass download prompt after notifications permission response`() {
    fakeAudioUtils.haveAllFilesResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )
    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)

    presenter.onPostNotificationsPermissionResponse(true)

    assertThat(fakeScreen.proceedWithDownloadCalls).hasSize(1)
    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)
  }

  // ==================== Permission Callback Tests ====================

  @Test
  fun `should replay audio after download permission granted`() {
    fakeAudioUtils.haveAllFilesResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )
    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)

    fakeAudioUtils.haveAllFilesResult = true
    fakeAudioUtils.shouldDownloadBasmallahResult = false

    presenter.onDownloadPermissionGranted()

    assertThat(fakeScreen.handlePlaybackCalls).hasSize(1)
  }

  @Test
  fun `should replay audio after download success`() {
    fakeAudioUtils.haveAllFilesResult = false
    presenter.bind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )
    assertThat(fakeScreen.handleRequiredDownloadCalls).hasSize(1)

    fakeAudioUtils.haveAllFilesResult = true
    fakeAudioUtils.shouldDownloadBasmallahResult = false

    presenter.onDownloadSuccess()

    assertThat(fakeScreen.handlePlaybackCalls).hasSize(1)
  }

  // ==================== Lifecycle Tests ====================

  @Test
  fun `should not play when activity is not bound`() {
    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handlePlaybackCalls).isEmpty()
    assertThat(fakeScreen.handleRequiredDownloadCalls).isEmpty()
  }

  @Test
  fun `should clear activity reference on unbind`() {
    presenter.bind(fakeScreen)
    presenter.unbind(fakeScreen)

    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    assertThat(fakeScreen.handlePlaybackCalls).isEmpty()
    assertThat(fakeScreen.handleRequiredDownloadCalls).isEmpty()
  }
}
