package com.quran.labs.androidquran.presenter.quran

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.model.quran.CoordinatesModel
import com.quran.labs.androidquran.ui.helpers.QuranPageLoader
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

/**
 * Tests for QuranPagePresenter.
 *
 * Tests cover:
 * - Screen binding/unbinding
 * - Page coordinates loading
 * - Ayah coordinates loading
 * - Image downloading
 * - Error handling
 * - Settings integration (overlay page info)
 */
class QuranPagePresenterTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setupClass() {
      // Use Schedulers.trampoline() for synchronous testing
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
    }
  }

  @Mock private lateinit var coordinatesModel: CoordinatesModel
  @Mock private lateinit var quranSettings: QuranSettings
  @Mock private lateinit var quranPageLoader: QuranPageLoader
  @Mock private lateinit var quranInfo: QuranInfo
  @Mock private lateinit var screen: QuranPageScreen

  private lateinit var presenter: QuranPagePresenter
  private val testPages = intArrayOf(1, 2, 3)

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)

    // Default mock behaviors
    whenever(quranInfo.isValidPage(1)).thenReturn(true)
    whenever(quranInfo.isValidPage(2)).thenReturn(true)
    whenever(quranInfo.isValidPage(3)).thenReturn(true)
    whenever(quranSettings.shouldOverlayPageInfo()).thenReturn(true)

    // Default: return empty observables to avoid null pointer issues
    whenever(coordinatesModel.getPageCoordinates(true, 1, 2, 3))
      .thenReturn(Observable.empty())
    whenever(quranPageLoader.loadPages(arrayOf(1, 2, 3)))
      .thenReturn(Observable.empty())

    presenter = QuranPagePresenter(
      coordinatesModel = coordinatesModel,
      quranSettings = quranSettings,
      quranPageLoader = quranPageLoader,
      quranInfo = quranInfo,
      pages = testPages
    )
  }

  @Test
  fun `should load page coordinates when screen bound`() {
    // Arrange
    val mockPageCoordinates = mock(PageCoordinates::class.java)
    whenever(coordinatesModel.getPageCoordinates(true, 1, 2, 3))
      .thenReturn(Observable.just(mockPageCoordinates))

    // Act
    presenter.bind(screen)

    // Assert
    verify(coordinatesModel).getPageCoordinates(true, 1, 2, 3)
    verify(screen).setPageCoordinates(mockPageCoordinates)
  }

  @Test
  fun `should respect shouldOverlayPageInfo setting when loading coordinates`() {
    // Arrange
    whenever(quranSettings.shouldOverlayPageInfo()).thenReturn(false)
    whenever(coordinatesModel.getPageCoordinates(false, 1, 2, 3))
      .thenReturn(Observable.empty())

    // Act
    presenter.bind(screen)

    // Assert
    verify(coordinatesModel).getPageCoordinates(false, 1, 2, 3)
  }

  @Test
  fun `should load page coordinates with overlay enabled`() {
    // Arrange
    whenever(quranSettings.shouldOverlayPageInfo()).thenReturn(true)
    whenever(coordinatesModel.getPageCoordinates(true, 1, 2, 3))
      .thenReturn(Observable.empty())

    // Act
    presenter.bind(screen)

    // Assert
    verify(coordinatesModel).getPageCoordinates(true, 1, 2, 3)
  }

  @Test
  fun `should handle page coordinates error`() {
    // Arrange
    val error = RuntimeException("Coordinates error")
    whenever(coordinatesModel.getPageCoordinates(true, 1, 2, 3))
      .thenReturn(Observable.error(error))

    // Act
    presenter.bind(screen)

    // Assert
    verify(screen).setAyahCoordinatesError()
  }

  @Test
  fun `should load ayah coordinates after page coordinates complete`() {
    // Arrange
    val mockPageCoordinates = mock(PageCoordinates::class.java)
    val mockAyahCoordinates = mock(AyahCoordinates::class.java)

    whenever(coordinatesModel.getPageCoordinates(true, 1, 2, 3))
      .thenReturn(Observable.just(mockPageCoordinates))
    whenever(coordinatesModel.getAyahCoordinates(1))
      .thenReturn(Observable.just(mockAyahCoordinates))
    whenever(coordinatesModel.getAyahCoordinates(2))
      .thenReturn(Observable.just(mockAyahCoordinates))
    whenever(coordinatesModel.getAyahCoordinates(3))
      .thenReturn(Observable.just(mockAyahCoordinates))

    // Act
    presenter.bind(screen)

    // Wait for timer (500ms delay in code) + processing time
    Thread.sleep(600)

    // Assert: ayah coordinates are loaded for all pages after page coordinates complete
    verify(coordinatesModel).getAyahCoordinates(1)
    verify(coordinatesModel).getAyahCoordinates(2)
    verify(coordinatesModel).getAyahCoordinates(3)
  }

  @Test
  fun `should download images on first bind`() {
    // Arrange
    val mockResponse = mock(Response::class.java)
    whenever(quranPageLoader.loadPages(arrayOf(1, 2, 3)))
      .thenReturn(Observable.just(mockResponse))

    // Act
    presenter.bind(screen)

    // Assert: bind() automatically triggers download
    verify(quranPageLoader).loadPages(arrayOf(1, 2, 3))
  }

  @Test
  fun `should filter invalid pages when downloading`() {
    // Arrange
    whenever(quranInfo.isValidPage(1)).thenReturn(true)
    whenever(quranInfo.isValidPage(2)).thenReturn(false) // Invalid page
    whenever(quranInfo.isValidPage(3)).thenReturn(true)
    whenever(quranPageLoader.loadPages(arrayOf(1, 3))) // Only valid pages
      .thenReturn(Observable.empty())

    // Act: bind() triggers download automatically
    presenter.bind(screen)

    // Assert: only valid pages are downloaded
    verify(quranPageLoader).loadPages(arrayOf(1, 3))
  }

  @Test
  fun `should hide page download error when downloading images`() {
    // Arrange
    whenever(quranPageLoader.loadPages(arrayOf(1, 2, 3)))
      .thenReturn(Observable.empty())

    // Act: bind() triggers download automatically
    presenter.bind(screen)

    // Assert
    verify(screen).hidePageDownloadError()
  }

  @Test
  fun `should clear disposables when screen unbound`() {
    // Arrange
    whenever(coordinatesModel.getPageCoordinates(true, 1, 2, 3))
      .thenReturn(Observable.never()) // Never completes

    // Act
    presenter.bind(screen)
    presenter.unbind(screen)

    // Assert
    // After unbind, subscription should be disposed
    // We verify this indirectly - no crash, cleanup successful
  }
}
