package com.quran.labs.androidquran.feature.reading.presenter

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.playback.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.test.TestDataFactory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
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

  @Mock private lateinit var quranDisplayData: QuranDisplayData
  @Mock private lateinit var audioUtil: AudioUtils
  @Mock private lateinit var audioExtensionDecider: AudioExtensionDecider
  @Mock private lateinit var quranFileUtils: QuranFileUtils
  @Mock private lateinit var pagerActivity: PagerActivity
  @Mock private lateinit var context: Context

  private lateinit var presenter: AudioPresenter

  // Test data
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
  private val end = TestDataFactory.fatihaEnd() // 1:7
  private val localPath = "/sdcard/quran/audio/test_qari"

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)

    // Default mock behaviors
    whenever(audioExtensionDecider.audioExtensionForQari(testQariGapped)).thenReturn("mp3")
    whenever(audioExtensionDecider.audioExtensionForQari(testQariGapless)).thenReturn("mp3")
    whenever(audioExtensionDecider.allowedAudioExtensions(testQariGapped)).thenReturn(listOf("mp3"))
    whenever(audioExtensionDecider.allowedAudioExtensions(testQariGapless)).thenReturn(listOf("mp3"))

    whenever(audioUtil.getLocalQariUrl(testQariGapped)).thenReturn(localPath)
    whenever(audioUtil.getLocalQariUrl(testQariGapless)).thenReturn(localPath)
    whenever(audioUtil.getQariUrl(testQariGapped, "mp3")).thenReturn("https://example.com/audio/")
    whenever(audioUtil.getQariUrl(testQariGapless, "mp3")).thenReturn("https://example.com/gapless/")

    whenever(quranFileUtils.haveAyaPositionFile()).thenReturn(true)

    presenter = AudioPresenter(
      quranDisplayData = quranDisplayData,
      audioUtil = audioUtil,
      audioExtensionDecider = audioExtensionDecider,
      quranFileUtils = quranFileUtils
    )

    // Stubs needed for download path tests
    whenever(quranFileUtils.ayaPositionFileUrl).thenReturn("https://example.com/ayah.db")
    whenever(quranFileUtils.quranAyahDatabaseDirectory).thenReturn(File("/tmp/quran_test_ayah"))
    whenever(quranFileUtils.gaplessDatabaseRootUrl).thenReturn("https://example.com/gapless")

  }

  // ==================== Playback Tests ====================

  @Test
  fun `should play audio when all files are downloaded`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", // baseUrl
      localPath, // path
      start,
      end,
      false, // isGapless
      listOf("mp3") // allowedExtensions
    )).thenReturn(true)

    whenever(audioUtil.shouldDownloadBasmallah(
      localPath, start, end, false, listOf("mp3")
    )).thenReturn(false)

    // Act
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = false
    )

    // Assert: should trigger playback directly, no download
    val captor = ArgumentCaptor.forClass(AudioRequest::class.java)
    verify(pagerActivity).handlePlayback(captor.capture())

    val request = captor.value
    assertThat(request.start).isEqualTo(start)
    assertThat(request.end).isEqualTo(end)
    assertThat(request.shouldStream).isFalse()
  }

  @Test
  fun `should stream when files are missing and streaming enabled`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", // baseUrl
      localPath, // path
      start,
      end,
      false, // isGapless
      listOf("mp3") // allowedExtensions
    )).thenReturn(false) // Files not available

    // Act
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = true // Streaming enabled
    )

    // Assert: should stream with remote URL
    val captor = ArgumentCaptor.forClass(AudioRequest::class.java)
    verify(pagerActivity).handlePlayback(captor.capture())

    val request = captor.value
    assertThat(request.shouldStream).isTrue()
    assertThat(request.audioPathInfo.urlFormat).contains("https://example.com/audio/")
  }

  @Test
  fun `should not stream when all files are available even if streaming enabled`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", // baseUrl
      localPath, // path
      start,
      end,
      false, // isGapless
      listOf("mp3") // allowedExtensions
    )).thenReturn(true) // All files available

    whenever(audioUtil.shouldDownloadBasmallah(
      localPath, start, end, false, listOf("mp3")
    )).thenReturn(false)

    // Act
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = true // Streaming requested but files available
    )

    // Assert: should NOT stream since files are available
    val captor = ArgumentCaptor.forClass(AudioRequest::class.java)
    verify(pagerActivity).handlePlayback(captor.capture())

    val request = captor.value
    assertThat(request.shouldStream).isFalse()
  }

  @Test
  fun `should swap start and end when start is greater than end`() {
    // Arrange
    presenter.bind(pagerActivity)
    val reversedStart = TestDataFactory.fatihaEnd() // 1:7
    val reversedEnd = TestDataFactory.fatihaStart() // 1:1

    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", // baseUrl
      localPath, // path
      reversedEnd, // Swapped start
      reversedStart, // Swapped end
      false, // isGapless
      listOf("mp3") // allowedExtensions
    )).thenReturn(true)

    whenever(audioUtil.shouldDownloadBasmallah(
      localPath, reversedEnd, reversedStart, false, listOf("mp3")
    )).thenReturn(false)

    // Act
    presenter.play(
      start = reversedStart, // 1:7 (larger)
      end = reversedEnd, // 1:1 (smaller)
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = false
    )

    // Assert: should swap start and end
    val captor = ArgumentCaptor.forClass(AudioRequest::class.java)
    verify(pagerActivity).handlePlayback(captor.capture())

    val request = captor.value
    assertThat(request.start).isEqualTo(reversedEnd) // Swapped to smaller
    assertThat(request.end).isEqualTo(reversedStart) // Swapped to larger
  }

  // ==================== Download Tests ====================

  @Test
  fun `should trigger aya position file download when missing`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(pagerActivity.getString(R.string.highlighting_database)).thenReturn("Required Files")
    whenever(pagerActivity.getString(R.string.timing_database)).thenReturn("Required Files")
    whenever(quranFileUtils.haveAyaPositionFile()).thenReturn(false)

    // Act
    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    // Assert: aya position file download triggered
    verify(pagerActivity).handleRequiredDownload(org.mockito.ArgumentMatchers.any())
    verify(pagerActivity, never()).handlePlayback(org.mockito.ArgumentMatchers.any())
  }

  @Test
  fun `should trigger gapless database download when db file missing`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(pagerActivity.getString(R.string.highlighting_database)).thenReturn("Required Files")
    whenever(pagerActivity.getString(R.string.timing_database)).thenReturn("Required Files")
    // haveAyaPositionFile = true (default stub)
    // Return a path that doesn't exist on disk — File.exists() returns false naturally
    whenever(audioUtil.getQariDatabasePathIfGapless(testQariGapless))
      .thenReturn("/tmp/nonexistent_gapless_${System.currentTimeMillis()}.db")

    // Act
    presenter.play(
      start = start, end = end, qari = testQariGapless,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    // Assert: gapless timing database download triggered
    verify(pagerActivity).handleRequiredDownload(org.mockito.ArgumentMatchers.any())
    verify(pagerActivity, never()).handlePlayback(org.mockito.ArgumentMatchers.any())
  }

  @Test
  fun `should trigger basmallah download when basmallah file missing`() {
    // Arrange
    presenter.bind(pagerActivity)
    org.mockito.Mockito.doReturn("Test Notification")
          .`when`(quranDisplayData).getNotificationTitle(pagerActivity, start, start, false)
    // haveAyaPositionFile = true (default), gaplessDb = null (getQariDatabasePathIfGapless not stubbed)
    whenever(audioUtil.shouldDownloadBasmallah(
      localPath, start, end, false, listOf("mp3")
    )).thenReturn(true)

    // Act
    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    // Assert: basmallah download triggered; intent uses start for both start/end (single verse)
    val captor = ArgumentCaptor.forClass(Intent::class.java)
    verify(pagerActivity).handleRequiredDownload(captor.capture())
    verify(pagerActivity, never()).handlePlayback(org.mockito.ArgumentMatchers.any())
    val intent = captor.value
    assertThat(intent.getSerializableExtra(QuranDownloadService.EXTRA_START_VERSE) as SuraAyah?).isEqualTo(start)
    assertThat(intent.getSerializableExtra(QuranDownloadService.EXTRA_END_VERSE) as SuraAyah?).isEqualTo(start)
  }

  @Test
  fun `should trigger full range download when audio files missing`() {
    // Arrange
    presenter.bind(pagerActivity)
    org.mockito.Mockito.doReturn("Test Notification")
          .`when`(quranDisplayData).getNotificationTitle(pagerActivity, start, end, false)
    // haveAyaPositionFile = true, gaplessDb = null, shouldDownloadBasmallah = false (defaults)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", localPath, start, end, false, listOf("mp3")
    )).thenReturn(false)

    // Act
    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )

    // Assert: full range download triggered with correct start/end extras
    val captor = ArgumentCaptor.forClass(Intent::class.java)
    verify(pagerActivity).handleRequiredDownload(captor.capture())
    verify(pagerActivity, never()).handlePlayback(org.mockito.ArgumentMatchers.any())
    val intent = captor.value
    assertThat(intent.getSerializableExtra(QuranDownloadService.EXTRA_START_VERSE) as SuraAyah?).isEqualTo(start)
    assertThat(intent.getSerializableExtra(QuranDownloadService.EXTRA_END_VERSE) as SuraAyah?).isEqualTo(end)
    assertThat(intent.getBooleanExtra(QuranDownloadService.EXTRA_IS_GAPLESS, false)).isFalse()
  }

  @Test
  fun `should bypass download prompt after notifications permission response`() {
    // Arrange: set up state where a download is needed (files missing)
    presenter.bind(pagerActivity)
    org.mockito.Mockito.doReturn("Test Notification")
          .`when`(quranDisplayData).getNotificationTitle(pagerActivity, start, end, false)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", localPath, start, end, false, listOf("mp3")
    )).thenReturn(false)

    // First play — stores lastAudioRequest, triggers handleRequiredDownload
    presenter.play(
      start = start, end = end, qari = testQariGapped,
      verseRepeat = 1, rangeRepeat = 1, enforceRange = false,
      playbackSpeed = 1.0f, shouldStream = false
    )
    verify(pagerActivity).handleRequiredDownload(org.mockito.ArgumentMatchers.any())

    // Act: notification permission responded
    presenter.onPostNotificationsPermissionResponse(true)

    // Assert: proceedWithDownload (bypass) called; handleRequiredDownload still only 1 time
    verify(pagerActivity).proceedWithDownload(org.mockito.ArgumentMatchers.any())
    verify(pagerActivity, org.mockito.Mockito.times(1)).handleRequiredDownload(
      org.mockito.ArgumentMatchers.any()
    )
  }

  // ==================== Permission Callback Tests ====================

  @Test
  fun `should replay audio after download permission granted`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", // baseUrl
      localPath, // path
      start,
      end,
      false, // isGapless
      listOf("mp3") // allowedExtensions
    )).thenReturn(true)

    whenever(audioUtil.shouldDownloadBasmallah(
      localPath, start, end, false, listOf("mp3")
    )).thenReturn(false)

    // First play to set lastAudioRequest
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = false
    )

    // Act: grant permission
    presenter.onDownloadPermissionGranted()

    // Assert: should replay (handlePlayback called twice - initial + after permission)
    verify(pagerActivity, org.mockito.Mockito.times(2)).handlePlayback(org.mockito.ArgumentMatchers.any())
  }


  @Test
  fun `should replay audio after download success`() {
    // Arrange
    presenter.bind(pagerActivity)
    whenever(audioUtil.haveAllFiles(
      "$localPath/%d/%d.mp3", // baseUrl
      localPath, // path
      start,
      end,
      false, // isGapless
      listOf("mp3") // allowedExtensions
    )).thenReturn(true)

    whenever(audioUtil.shouldDownloadBasmallah(
      localPath, start, end, false, listOf("mp3")
    )).thenReturn(false)

    // First play to set lastAudioRequest
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = false
    )

    // Act: notify download success
    presenter.onDownloadSuccess()

    // Assert: should replay
    verify(pagerActivity, org.mockito.Mockito.times(2)).handlePlayback(org.mockito.ArgumentMatchers.any())
  }

  // ==================== Lifecycle Tests ====================

  @Test
  fun `should not play when activity is not bound`() {
    // Arrange: don't bind activity

    // Act
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = false
    )

    // Assert: nothing should happen (safe-call on null)
    verify(pagerActivity, never()).handlePlayback(org.mockito.ArgumentMatchers.any())
    verify(pagerActivity, never()).handleRequiredDownload(org.mockito.ArgumentMatchers.any())
  }

  @Test
  fun `should clear activity reference on unbind`() {
    // Arrange
    presenter.bind(pagerActivity)

    // Act
    presenter.unbind(pagerActivity)

    // Now try to play
    presenter.play(
      start = start,
      end = end,
      qari = testQariGapped,
      verseRepeat = 1,
      rangeRepeat = 1,
      enforceRange = false,
      playbackSpeed = 1.0f,
      shouldStream = false
    )

    // Assert: nothing should happen after unbind
    verify(pagerActivity, never()).handlePlayback(org.mockito.ArgumentMatchers.any())
    verify(pagerActivity, never()).handleRequiredDownload(org.mockito.ArgumentMatchers.any())
  }
}
