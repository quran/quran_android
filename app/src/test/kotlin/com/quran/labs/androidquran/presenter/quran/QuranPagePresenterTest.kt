package com.quran.labs.androidquran.presenter.quran

import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.fakes.FakeCoordinatesModel
import com.quran.labs.androidquran.fakes.FakeQuranPageLoader
import com.quran.labs.androidquran.fakes.FakeQuranPageScreen
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.test.RxSchedulerRule
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranPagePresenterTest {

  @get:Rule
  val rxRule = RxSchedulerRule()

  private lateinit var fakeCoordinatesModel: FakeCoordinatesModel
  private lateinit var quranSettings: QuranSettings
  private lateinit var fakeQuranPageLoader: FakeQuranPageLoader
  private lateinit var quranInfo: QuranInfo
  private lateinit var fakeScreen: FakeQuranPageScreen
  private lateinit var presenter: QuranPagePresenter
  private val testPages = intArrayOf(1, 2, 3)

  @Before
  fun setup() {
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())

    fakeCoordinatesModel = FakeCoordinatesModel()
    fakeCoordinatesModel.pageCoordinatesResult = Observable.empty()
    fakeQuranPageLoader = FakeQuranPageLoader()
    fakeQuranPageLoader.loadPagesResult = Observable.empty()
    fakeScreen = FakeQuranPageScreen()
    quranInfo = QuranInfo(MadaniDataSource())

    presenter = QuranPagePresenter(
      coordinatesModel = fakeCoordinatesModel,
      quranSettings = quranSettings,
      quranPageLoader = fakeQuranPageLoader,
      quranInfo = quranInfo,
      pages = testPages
    )
  }

  @After
  fun teardown() {
    QuranSettings.setInstance(null)
  }

  @Test
  fun `should load page coordinates when screen bound`() {
    // Arrange
    val pageCoordinates = PageCoordinates(
      page = 1, pageBounds = RectF(), suraHeaders = emptyList(), ayahMarkers = emptyList()
    )
    fakeCoordinatesModel.pageCoordinatesResult = Observable.just(pageCoordinates)

    // Act
    presenter.bind(fakeScreen)

    // Assert
    assertThat(fakeCoordinatesModel.lastWantPageBoundsArg).isTrue()
    assertThat(fakeScreen.pageCoordinatesSet).contains(pageCoordinates)
  }

  @Test
  fun `should respect shouldOverlayPageInfo setting when loading coordinates`() {
    // Arrange
    quranSettings.setShouldOverlayPageInfo(false)

    // Act
    presenter.bind(fakeScreen)

    // Assert
    assertThat(fakeCoordinatesModel.lastWantPageBoundsArg).isFalse()
  }

  @Test
  fun `should load page coordinates with overlay enabled`() {
    // Arrange — shouldOverlayPageInfo defaults to true

    // Act
    presenter.bind(fakeScreen)

    // Assert
    assertThat(fakeCoordinatesModel.lastWantPageBoundsArg).isTrue()
  }

  @Test
  fun `should handle page coordinates error`() {
    // Arrange
    fakeCoordinatesModel.pageCoordinatesResult =
      Observable.error(RuntimeException("Coordinates error"))

    // Act
    presenter.bind(fakeScreen)

    // Assert
    assertThat(fakeScreen.ayahCoordinatesErrorCalled).isTrue()
  }

  @Test
  fun `should load ayah coordinates after page coordinates complete`() {
    // Arrange
    val pageCoordinates = PageCoordinates(
      page = 1, pageBounds = RectF(), suraHeaders = emptyList(), ayahMarkers = emptyList()
    )
    val ayahCoordinates = AyahCoordinates(
      page = 1, ayahCoordinates = emptyMap(), glyphCoordinates = null
    )

    fakeCoordinatesModel.pageCoordinatesResult = Observable.just(pageCoordinates)
    fakeCoordinatesModel.ayahCoordinatesResponses[1] = Observable.just(ayahCoordinates)
    fakeCoordinatesModel.ayahCoordinatesResponses[2] = Observable.just(ayahCoordinates)
    fakeCoordinatesModel.ayahCoordinatesResponses[3] = Observable.just(ayahCoordinates)

    // Act
    presenter.bind(fakeScreen)

    // Assert: ayah coordinates are loaded for all pages after page coordinates complete
    // Note: RxSchedulerRule makes trampoline scheduler execute timer immediately (no actual 500ms wait)
    assertThat(fakeCoordinatesModel.getAyahCoordinatesCalledWith).contains(1)
    assertThat(fakeCoordinatesModel.getAyahCoordinatesCalledWith).contains(2)
    assertThat(fakeCoordinatesModel.getAyahCoordinatesCalledWith).contains(3)
    assertThat(fakeScreen.ayahCoordinatesDataSet).isNotEmpty()
  }

  @Test
  fun `should download images on first bind`() {
    // Arrange
    val response = Response(null as android.graphics.Bitmap?)
    fakeQuranPageLoader.loadPagesResult = Observable.just(response)

    // Act
    presenter.bind(fakeScreen)

    // Assert: bind() automatically triggers download
    assertThat(fakeQuranPageLoader.lastLoadedPages?.toList()).containsExactly(1, 2, 3)
  }

  @Test
  fun `should filter invalid pages when downloading`() {
    // Arrange: page 605 exceeds MadaniDataSource's 604-page range
    val localFakeLoader = FakeQuranPageLoader()
    localFakeLoader.loadPagesResult = Observable.empty()
    val localPresenter = QuranPagePresenter(
      coordinatesModel = fakeCoordinatesModel,
      quranSettings = quranSettings,
      quranPageLoader = localFakeLoader,
      quranInfo = quranInfo,
      pages = intArrayOf(1, 605, 3)
    )

    // Act: bind() triggers download automatically
    localPresenter.bind(fakeScreen)

    // Assert: only valid pages are downloaded
    assertThat(localFakeLoader.lastLoadedPages?.toList()).containsExactly(1, 3)
  }

  @Test
  fun `should hide page download error when downloading images`() {
    // Act: bind() triggers download automatically
    presenter.bind(fakeScreen)

    // Assert
    assertThat(fakeScreen.hidePageDownloadErrorCalled).isTrue()
  }

  @Test
  fun `should not deliver results to screen after unbind`() {
    // Arrange: observable that emits after unbind
    val subject = PublishSubject.create<PageCoordinates>()
    fakeCoordinatesModel.pageCoordinatesResult = subject

    // Act
    presenter.bind(fakeScreen)
    presenter.unbind(fakeScreen)
    val lateCoordinates = PageCoordinates(
      page = 99, pageBounds = RectF(), suraHeaders = emptyList(), ayahMarkers = emptyList()
    )
    subject.onNext(lateCoordinates)

    // Assert: screen should NOT receive the late emission
    assertThat(fakeScreen.pageCoordinatesSet).doesNotContain(lateCoordinates)
  }
}
